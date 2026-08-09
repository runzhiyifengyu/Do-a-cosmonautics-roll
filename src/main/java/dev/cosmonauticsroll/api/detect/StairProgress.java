package dev.cosmonauticsroll.api.detect;

/**
 * 楼梯行走进度（阶段 5，PRD 3.4-2：根据楼梯碰撞形状判断玩家当前行走进度）。
 *
 * <p>纯逻辑、无 Minecraft 依赖。描述玩家脚底在楼梯斜面/水平台阶上的
 * 相对位置与站立方向：</p>
 * <ul>
 *   <li>{@link #progress()}：行走进度 0~1——0 = 在低一级水平台阶上（脚底全水平），
 *       1 = 在高一级水平台阶上（脚底全水平，上一级）；中间值 = 脚底跨在
 *       45° 斜面上（部分采样点命中斜面、部分命中台阶），站立方向为
 *       竖直方向向斜面方向倾斜 {@code progress × 45°}；</li>
 *   <li>{@link #standingDirection()}：对应身体「上」方向（单位向量，连续非轴向），
 *       作为平滑旋转的目标方向（PRD 3.4-3：根据楼梯方向和高度连续调整玩家身体角度）；</li>
 *   <li>{@link #stair()}：命中的楼梯方块识别信息（朝向与半部）。</li>
 * </ul>
 */
public final class StairProgress {

    /** 标准楼梯斜面角（弧度）：45°。 */
    public static final double STAIR_SLOPE_RADIANS = Math.PI / 4.0;

    /** 斜面方向（水平）单位向量，由 {@link StairInfo#ascentDirection()} 提供。 */
    private final Vec3d ascentDir;

    /** 行走进度 0~1（clamp 后）。 */
    private final double progress;

    /** 楼梯朝向（水平四向）。 */
    private final StairInfo.Facing facing;

    /**
     * @param ascentDir 斜面上升方向（水平单位向量）
     * @param progress  行走进度 0~1
     * @param facing    楼梯朝向
     */
    public StairProgress(Vec3d ascentDir, double progress, StairInfo.Facing facing) {
        if (ascentDir == null || ascentDir.lengthSquared() == 0.0) {
            throw new IllegalArgumentException("ascentDir must be a non-zero vector");
        }
        this.ascentDir = ascentDir.normalize();
        this.progress = clamp(progress, 0.0, 1.0);
        this.facing = facing == null ? StairInfo.Facing.SOUTH : facing;
    }

    /** 行走进度（0~1）。 */
    public double progress() {
        return progress;
    }

    /** 楼梯朝向。 */
    public StairInfo.Facing facing() {
        return facing;
    }

    /** 斜面上升方向（水平单位向量）。 */
    public Vec3d ascentDirection() {
        return ascentDir;
    }

    /**
     * 身体「上」方向：竖直方向向斜面方向倾斜 {@code progress × 45°}。
     * progress=0 → 竖直 (+Y)；progress=1 → 向斜面方向倾斜 45°（跨两级台阶的中点）。
     */
    public Vec3d standingDirection() {
        double tilt = STAIR_SLOPE_RADIANS * progress;
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        return new Vec3d(ascentDir.x * sin, cos, ascentDir.z * sin).normalize();
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    @Override
    public String toString() {
        return String.format("StairProgress{facing=%s, progress=%.2f, dir=%s}",
                facing, progress, standingDirection());
    }
}
