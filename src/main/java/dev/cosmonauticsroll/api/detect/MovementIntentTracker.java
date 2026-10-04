package dev.cosmonauticsroll.api.detect;

/**
 * 水平移动意图跟踪（纯逻辑，无 Minecraft 依赖；PRD 2.4「走向墙面」判定用）。
 *
 * <p>为什么不能只看瞬时位移：玩家走到墙前会被墙挡住，位移随即变为 0——
 * 而这正是最需要判定「正在走向墙面」的时刻（2026-10-04 第三轮日志：
 * 贴墙时 `result=MULTIPLE`，转向墙面的过渡从未触发）。因此本类除记录
 * 本 tick 位移外，还记录「最近一次真实移动」，并在窗口期内把它作为
 * 移动意图返回：{@link #intent(long, int)}。</p>
 *
 * <p>其它规则：</p>
 * <ul>
 *   <li>同一 tick 重复调用 {@link #update} 返回同一结果（多个 ticker 共用，不会互相清零）；</li>
 *   <li>单 tick 位移超过 {@link #MAX_MOVE_PER_TICK} 视为传送/切换维度，
 *       丢弃该次位移与已有意图，避免把瞬移当成「走向墙面」。</li>
 * </ul>
 */
public final class MovementIntentTracker {

    /** 单 tick 最大合理水平位移（格）：超过视为传送/切换维度。 */
    public static final double MAX_MOVE_PER_TICK = 1.0;

    private static final Vec3d ZERO = new Vec3d(0, 0, 0);

    private boolean hasLast;
    private long lastTick = Long.MIN_VALUE;
    private double lastX;
    private double lastZ;

    private Vec3d movement = ZERO;
    private Vec3d lastMovement = ZERO;
    private long lastMoveTick = Long.MIN_VALUE;

    /**
     * 记录本 tick 位置，返回本 tick 水平位移（格/tick）。
     *
     * @param tick 当前 tick（游戏时间；同一 tick 内多次调用返回同一结果）
     * @param x    玩家当前 X
     * @param z    玩家当前 Z
     */
    public Vec3d update(long tick, double x, double z) {
        if (hasLast && tick == lastTick) {
            return movement; // 同一 tick 重复查询
        }
        if (!hasLast) {
            hasLast = true;
            lastTick = tick;
            lastX = x;
            lastZ = z;
            movement = ZERO;
            return movement;
        }

        Vec3d delta = new Vec3d(x - lastX, 0.0, z - lastZ);
        lastTick = tick;
        lastX = x;
        lastZ = z;

        if (delta.length() > MAX_MOVE_PER_TICK) {
            // 传送/切换维度：丢弃位移与意图，避免误判为「走向墙面」
            movement = ZERO;
            lastMovement = ZERO;
            lastMoveTick = Long.MIN_VALUE;
            return movement;
        }

        movement = delta;
        if (delta.length() > 0.0) {
            lastMovement = delta;
            lastMoveTick = tick;
        }
        return movement;
    }

    /**
     * 移动意图：本 tick 有位移则用位移；否则若最近一次真实移动在
     * {@code windowTicks} 内，返回该次移动（玩家被墙挡住后仍保留「走向墙面」意图）；
     * 再否则返回零向量。
     *
     * @param tick        当前 tick
     * @param windowTicks 窗口长度（tick），建议 10（约 0.5 秒）
     */
    public Vec3d intent(long tick, int windowTicks) {
        if (movement.length() > 0.0) {
            return movement;
        }
        if (lastMoveTick == Long.MIN_VALUE) {
            return ZERO;
        }
        return (tick - lastMoveTick) <= windowTicks ? lastMovement : ZERO;
    }

    /** 清空状态（玩家登出/传送重置）。 */
    public void reset() {
        hasLast = false;
        lastTick = Long.MIN_VALUE;
        movement = ZERO;
        lastMovement = ZERO;
        lastMoveTick = Long.MIN_VALUE;
    }
}
