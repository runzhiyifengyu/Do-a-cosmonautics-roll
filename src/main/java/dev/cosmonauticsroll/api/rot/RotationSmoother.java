package dev.cosmonauticsroll.api.rot;

import dev.cosmonauticsroll.api.detect.Vec3d;

/**
 * 平滑旋转核心（阶段 4，PRD 3.3）。
 *
 * <p>纯逻辑、无 Minecraft 依赖，可在设备 VM 上单元测试。
 * 负责三个职责：</p>
 * <ul>
 *   <li>方向平滑：当前站立方向向目标站立方向连续过渡，不瞬间跳变
 *       （PRD 3.3-1/3.3-2：地面/墙面/天花板之间平滑旋转）；</li>
 *   <li>旋转速率限制：每 tick 最多旋转 {@link #maxRadiansPerTick()}，
 *       保证身体转向平滑（PRD 3.3 验收「不能瞬间跳变」）；</li>
 *   <li>防抖（PRD 3.3-3）：目标方向进入死区、持续保持、切换锁定——
 *       防止在表面边缘/墙角反复来回切换。</li>
 * </ul>
 *
 * <p>方向用单位向量表示（身体「上」方向 / 站立表面法线），向量 slerp
 * 插值 + 归一化；当前方向与目标方向接近时直接吸附（消除残余误差与抖动）。</p>
 */
public final class RotationSmoother {

    /** 吸附阈值（弧度）：当前与目标夹角小于该值直接吸附到目标，消除残余抖动。 */
    public static final double DEFAULT_SNAP_RADIANS = Math.toRadians(0.5);

    /** 每 tick 最大旋转角（弧度）：20 tick/s 下约每秒 80°，地面→墙面约 1.1 秒平滑过渡。 */
    public static final double DEFAULT_MAX_RADIANS_PER_TICK = Math.toRadians(4.0);

    /** 目标方向死区（弧度）：目标与当前方向夹角小于该值时不再更新目标（防抖）。 */
    public static final double DEFAULT_DEAD_ZONE_RADIANS = Math.toRadians(0.25);

    /** 切换锁（tick）：目标方向变化后锁定切换，避免边缘位置方向反复抖动。 */
    public static final int DEFAULT_SWITCH_LOCK_TICKS = 4;

    private final double maxRadiansPerTick;
    private final double snapRadians;
    private final double deadZoneRadians;
    private final int switchLockTicks;

    /** 当前身体「上」方向（单位向量）。 */
    private Vec3d current = new Vec3d(0, 1, 0);

    /** 目标身体「上」方向（单位向量）。 */
    private Vec3d target = current;

    /** 距上次目标方向改变的 tick 数（用于切换锁）。 */
    private int ticksSinceSwitch;

    /**
     * 使用默认参数创建平滑器。
     */
    public RotationSmoother() {
        this(DEFAULT_MAX_RADIANS_PER_TICK, DEFAULT_SNAP_RADIANS,
                DEFAULT_DEAD_ZONE_RADIANS, DEFAULT_SWITCH_LOCK_TICKS);
    }

    /**
     * @param maxRadiansPerTick 每 tick 最大旋转角（弧度），必须 &gt; 0
     * @param snapRadians       吸附阈值（弧度），必须 &gt;= 0
     * @param deadZoneRadians   目标死区（弧度），必须 &gt;= 0
     * @param switchLockTicks   切换锁 tick 数，必须 &gt;= 0
     */
    public RotationSmoother(double maxRadiansPerTick, double snapRadians,
                            double deadZoneRadians, int switchLockTicks) {
        if (!(maxRadiansPerTick > 0)) {
            throw new IllegalArgumentException("maxRadiansPerTick must be > 0");
        }
        if (!(snapRadians >= 0)) {
            throw new IllegalArgumentException("snapRadians must be >= 0");
        }
        if (!(deadZoneRadians >= 0)) {
            throw new IllegalArgumentException("deadZoneRadians must be >= 0");
        }
        if (switchLockTicks < 0) {
            throw new IllegalArgumentException("switchLockTicks must be >= 0");
        }
        this.maxRadiansPerTick = maxRadiansPerTick;
        this.snapRadians = snapRadians;
        this.deadZoneRadians = deadZoneRadians;
        this.switchLockTicks = switchLockTicks;
    }

    /**
     * 设置目标站立方向（表面法线 / 身体「上」方向），自动归一化。
     *
     * <p>防抖规则（PRD 3.3-3）：</p>
     * <ul>
     *   <li>目标为 null 或与当前方向夹角在死区内 → 忽略（目标保持，不产生抖动）；</li>
     *   <li>目标方向变化过大（与旧目标夹角超过 90°）且处于切换锁内 → 忽略
     *       （边缘位置两个表面方向来回切换时不跟随，锁定期结束后才允许切换）；</li>
     *   <li>否则接受新目标并启动切换锁。</li>
     * </ul>
     *
     * @param newTarget 新目标方向（可为 null，表示保持当前目标）
     * @return 是否接受该目标（false = 被防抖规则忽略）
     */
    public boolean setTarget(Vec3d newTarget) {
        if (newTarget == null) {
            return false;
        }
        Vec3d normalized = newTarget.normalize();
        if (normalized.lengthSquared() == 0.0) {
            return false; // 零向量：忽略
        }
        if (angleRadians(current, normalized) < deadZoneRadians) {
            return false; // 目标已在死区内：忽略，避免抖动
        }
        if (ticksSinceSwitch < switchLockTicks && angleRadians(target, normalized) > Math.PI / 2.0) {
            return false; // 切换锁内且方向变化过大：忽略，防边缘反复抖动
        }
        target = normalized;
        ticksSinceSwitch = 0;
        return true;
    }

    /**
     * 每 tick 推进一次平滑旋转。
     *
     * @return 更新后的当前站立方向（单位向量）
     */
    public Vec3d update() {
        ticksSinceSwitch++;
        double angle = angleRadians(current, target);
        if (angle <= snapRadians) {
            current = target; // 吸附：消除残余误差与抖动
        } else {
            double step = Math.min(maxRadiansPerTick, angle);
            current = slerp(current, target, step / angle).normalize();
        }
        return current;
    }

    /** 当前站立方向（单位向量）。 */
    public Vec3d current() {
        return current;
    }

    /** 目标站立方向（单位向量）。 */
    public Vec3d target() {
        return target;
    }

    /** 距上次目标改变的 tick 数。 */
    public int ticksSinceSwitch() {
        return ticksSinceSwitch;
    }

    /**
     * 重置为竖直方向（原版站立方向 +Y），并清空目标与切换锁。
     * 用于离开适用区域、传送、死亡重生（PRD 3.3-7：离开区域恢复竖直）。
     */
    public void reset() {
        current = new Vec3d(0, 1, 0);
        target = current;
        ticksSinceSwitch = 0;
    }

    /** 两单位向量夹角（弧度），0..π。 */
    public static double angleRadians(Vec3d a, Vec3d b) {
        double dot = clampDot(a.dot(b));
        return Math.acos(dot);
    }

    /**
     * 向量 slerp：沿两向量间最短弧线插值（t ∈ [0,1]）。
     * 两向量平行时退化为线性插值（避免 0/0）。
     */
    public static Vec3d slerp(Vec3d a, Vec3d b, double t) {
        double dot = clampDot(a.dot(b));
        if (dot > 0.999999) {
            return a.add(b.subtract(a).scale(t)).normalize();
        }
        double theta = Math.acos(dot);
        double sinTheta = Math.sin(theta);
        double w1 = Math.sin((1.0 - t) * theta) / sinTheta;
        double w2 = Math.sin(t * theta) / sinTheta;
        return a.scale(w1).add(b.scale(w2)).normalize();
    }

    /** 点积夹到 [-1,1]（浮点误差保护）。 */
    private static double clampDot(double dot) {
        return Math.max(-1.0, Math.min(1.0, dot));
    }
}
