package dev.cosmonauticsroll.region;

import dev.cosmonauticsroll.api.region.RegionRule;

/**
 * 适用区域状态机：处理进入、保持、离开三种状态（PRD 3.1-5）。
 *
 * <p>纯逻辑、无 Minecraft 依赖，可在设备 VM 上单元测试。
 * 每个 tick 调用 {@link #update(String, double)}，返回当前状态；
 * 通过 {@link #entered()} / {@link #left()} 检测状态转换，用于触发
 * 旋转状态初始化与清理（PRD 3.1-6：跨越 Y=8000 边界、维度切换、死亡、
 * 重生、传送时不留残留状态）。</p>
 */
public final class RegionStateMachine {

    /** 区域状态。 */
    public enum RegionState {
        /** 不在适用区域。 */
        INACTIVE,
        /** 在适用区域内。 */
        ACTIVE
    }

    private final RegionRule rule;
    private RegionState state = RegionState.INACTIVE;
    private boolean entered;
    private boolean left;

    /**
     * @param rule 适用区域规则（null 时使用默认组合规则）
     */
    public RegionStateMachine(RegionRule rule) {
        this.rule = rule != null ? rule : RegionRules.DEFAULT;
    }

    /**
     * 每 tick 更新状态机。
     *
     * @param dimensionId 维度注册名
     * @param centerY     玩家碰撞箱中心 Y 坐标
     * @return 更新后的区域状态
     */
    public RegionState update(String dimensionId, double centerY) {
        boolean active = rule.isActive(dimensionId, centerY);
        entered = false;
        left = false;

        if (active) {
            if (state == RegionState.INACTIVE) {
                entered = true;
                state = RegionState.ACTIVE;
            }
        } else {
            if (state == RegionState.ACTIVE) {
                left = true;
                state = RegionState.INACTIVE;
            }
        }
        return state;
    }

    /** 是否刚从区域外进入（本次 update 发生进入转换）。 */
    public boolean entered() {
        return entered;
    }

    /** 是否刚离开区域（本次 update 发生离开转换）。 */
    public boolean left() {
        return left;
    }

    /** 当前是否处于适用区域内。 */
    public boolean isActive() {
        return state == RegionState.ACTIVE;
    }

    /** 当前区域状态。 */
    public RegionState state() {
        return state;
    }

    /**
     * 强制清理状态机：重置为区域外，并清空本次 tick 的转换标记。
     * 用于死亡、重生、传送、维度切换等需要立即丢弃残留状态的场景。
     */
    public void reset() {
        state = RegionState.INACTIVE;
        entered = false;
        left = false;
    }
}
