package dev.cosmonauticsroll.region;

/**
 * 离开方块计时器（PRD 2.3）：在适用区域内，玩家离开方块接触后约 0.3 秒，
 * 进入相应的无重力/太空旋转状态。
 *
 * <p>纯逻辑、无 Minecraft 依赖，可在设备 VM 上单元测试。计时只用于本模组的
 * 旋转状态判断，重力是否存在由其他模组决定。</p>
 *
 * <p>输入为每 tick 的"脚部是否接触可站立表面"（阶段 3 实现该检测后接入）；
 * 输出三种状态：</p>
 * <ul>
 *   <li>{@link ContactState#CONTACTED}：接触表面（可站立/行走）</li>
 *   <li>{@link ContactState#RECENTLY_LEFT}：离开表面未满 0.3 秒（宽限期内）</li>
 *   <li>{@link ContactState#FREE}：离开表面超过 0.3 秒（进入太空旋转状态）</li>
 * </ul>
 */
public final class SurfaceContactTimer {

    /** 离开宽限期：0.3 秒，按 20 tick/秒折算。 */
    public static final int LEAVE_GRACE_TICKS = 6;

    /** 接触状态。 */
    public enum ContactState {
        /** 接触表面。 */
        CONTACTED,
        /** 离开表面未满 0.3 秒（宽限期）。 */
        RECENTLY_LEFT,
        /** 离开表面超过 0.3 秒。 */
        FREE
    }

    private int ticksSinceContact = 0;

    /**
     * 每 tick 更新计时器。
     *
     * @param inContact 本 tick 是否接触可站立表面
     * @return 更新后的接触状态
     */
    public ContactState update(boolean inContact) {
        if (inContact) {
            ticksSinceContact = 0;
            return ContactState.CONTACTED;
        }
        ticksSinceContact++;
        return ticksSinceContact > LEAVE_GRACE_TICKS
                ? ContactState.FREE
                : ContactState.RECENTLY_LEFT;
    }

    /** 重置计时器（传送、死亡、离开适用区域时调用）。 */
    public void reset() {
        ticksSinceContact = 0;
    }

    /** 距上次接触表面经过的 tick 数。 */
    public int ticksSinceContact() {
        return ticksSinceContact;
    }
}
