package dev.cosmonauticsroll.rot;

import dev.cosmonauticsroll.api.detect.FootSurfaceResult;
import dev.cosmonauticsroll.api.detect.StairProgress;
import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.api.rot.RotationSmoother;

/**
 * 平滑站立方向合成器（阶段 4 核心，PRD 3.3；阶段 5 扩展楼梯，PRD 3.4）。
 *
 * <p>每 tick 的完整流程：</p>
 * <ol>
 *   <li>{@link #setTarget}：由脚部检测结果决定目标站立方向
 *       （SINGLE → 表面法线；NONE/MULTIPLE → 保持当前，防抖，PRD 3.3-3）；</li>
 *   <li>{@link #setStairTarget}：楼梯行走进度 → 目标方向（竖直向斜面方向
 *       倾斜 {@code progress×45°}，PRD 3.4-2/3.4-3），进度变化小于
 *       防抖阈值时保持（楼梯边缘防抖，PRD 3.4-5）；</li>
 *   <li>{@link #update}：平滑器按每 tick 最大旋转角向目标过渡（PRD 3.3-1/3.3-2），
 *       快速移动/快速离开表面时保持当前方向不瞬间跳变（PRD 3.3-4）；</li>
 *   <li>{@link #leaveRegion}：离开适用区域时平滑恢复竖直方向（PRD 3.3-7）。</li>
 * </ol>
 *
 * <p>纯逻辑、无 Minecraft 依赖，可在设备 VM 上单元测试。
 * 游戏内应用（旋转玩家身体/视角、与 Do a Barrel Roll 叠加顺序）由
 * 阶段 4 的 {@code RotationTicker} 负责，叠加顺序见 DEVELOPMENT.md。</p>
 */
public final class SmoothStandingRotation {

    /** 楼梯进度防抖阈值：进度变化小于该值不更新目标（楼梯边缘窄条抖动抑制，PRD 3.4-5）。
     *  楼梯密集采样粒度 1/15 ≈ 0.067，取 0.03（约半个粒度）——目标跟随灵敏
     *  （PRD 3.4-3 连续调整角度），视觉平滑由平滑器（每 tick 最大 4°）保证。 */
    public static final double STAIR_PROGRESS_DEAD_ZONE = 0.03;

    private final RotationSmoother smoother;
    private final StandingDirectionState state;
    private boolean leaving;
    private Double lastStairProgress;

    public SmoothStandingRotation() {
        this(new RotationSmoother(), new StandingDirectionState());
    }

    /**
     * @param smoother 平滑器（自定义参数：速率、死区、切换锁）
     * @param state    站立方向状态
     */
    public SmoothStandingRotation(RotationSmoother smoother, StandingDirectionState state) {
        if (smoother == null) {
            throw new IllegalArgumentException("smoother must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.smoother = smoother;
        this.state = state;
    }

    /**
     * 输入本 tick 的脚部表面检测结果。
     *
     * @param result 检测结果（可为 null，视为 NONE）
     * @return 是否接受该目标（false = 被防抖规则忽略或处于恢复竖直模式）
     */
    public boolean setTarget(FootSurfaceResult result) {
        if (leaving) {
            return false; // 离开区域恢复竖直期间不接受新表面目标
        }
        if (result != null && result.isSingle()) {
            // 非楼梯普通表面（含 source=stair 已在 resolver 层转为方向）
            lastStairProgress = null;
        }
        Vec3d target = state.update(result);
        return smoother.setTarget(target);
    }

    /**
     * 输入楼梯行走进度（PRD 3.4-2/3.4-3）：目标方向 = 竖直向斜面方向
     * 倾斜 {@code progress×45°}；进度相对上次变化小于
     * {@value #STAIR_PROGRESS_DEAD_ZONE} 时保持当前目标（楼梯边缘防抖，
     * PRD 3.4-5），不更新。
     *
     * @param progress 楼梯行走进度（含目标方向）
     * @return 是否接受该目标（false = 被防抖规则忽略）
     */
    public boolean setStairTarget(StairProgress progress) {
        if (leaving || progress == null) {
            return false;
        }
        double p = progress.progress();
        if (lastStairProgress != null && Math.abs(p - lastStairProgress) < STAIR_PROGRESS_DEAD_ZONE) {
            return false; // 进度变化过小：楼梯边缘防抖
        }
        lastStairProgress = p;
        return smoother.setTarget(progress.standingDirection());
    }

    /**
     * 每 tick 推进一次平滑旋转。
     *
     * @return 当前站立方向（身体「上」方向，单位向量）
     */
    public Vec3d update() {
        return smoother.update();
    }

    /**
     * 离开适用区域：切换到「恢复竖直」模式，目标 = 竖直方向（+Y），
     * 由平滑器连续过渡（PRD 3.3-7：平滑恢复原版竖直方向，不瞬间跳变）。
     *
     * @return 是否已处于恢复竖直模式
     */
    public boolean leaveRegion() {
        leaving = true;
        smoother.setTarget(new Vec3d(0, 1, 0));
        return leaving;
    }

    /**
     * 重置为竖直方向（传送 / 死亡重生 / 维度切换），不经过平滑过渡。
     * 与 {@link #leaveRegion()} 的平滑恢复不同：reset 用于需要立即丢弃状态的场景。
     */
    public void reset() {
        smoother.reset();
        state.reset();
        leaving = false;
        lastStairProgress = null;
    }

    /** 当前站立方向（身体「上」方向）。 */
    public Vec3d current() {
        return smoother.current();
    }

    /** 目标站立方向（身体「上」方向）。 */
    public Vec3d target() {
        return smoother.target();
    }

    /** 是否处于恢复竖直模式（离开适用区域后）。 */
    public boolean isLeaving() {
        return leaving;
    }
}
