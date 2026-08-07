package dev.cosmonauticsroll.api.detect;

/**
 * 表面法线的统一方向表示（PRD 3.2-6：为地面、墙面、天花板提供统一方向表示）。
 *
 * <p>纯逻辑、无 Minecraft 依赖。六个轴向与世界坐标轴一一对应（等价于
 * Minecraft 的 {@code Direction} 六方向），但不引用 Minecraft 类，保证可在
 * 设备 VM 上单元测试、并可跨服务端/客户端使用。</p>
 *
 * <p>约定：法线指向「表面所在方块」的反方向，即玩家站在表面上时，
 * 法线指向玩家身体外侧（地面 = 朝上 UP，天花板 = 朝下 DOWN，墙面 = 水平）。</p>
 */
public enum SurfaceNormal {

    /** 朝上（站在地面上时，脚底表面的法线）。 */
    UP(0, 1, 0),
    /** 朝下（站在天花板下时，头顶表面的法线）。 */
    DOWN(0, -1, 0),
    /** 朝北（-Z）。 */
    NORTH(0, 0, -1),
    /** 朝南（+Z）。 */
    SOUTH(0, 0, 1),
    /** 朝西（-X）。 */
    WEST(-1, 0, 0),
    /** 朝东（+X）。 */
    EAST(1, 0, 0);

    private final Vec3d vector;

    SurfaceNormal(double x, double y, double z) {
        this.vector = new Vec3d(x, y, z);
    }

    /** 法线单位向量。 */
    public Vec3d vector() {
        return vector;
    }

    /** 是否为竖直方向（地面/天花板）。 */
    public boolean isVertical() {
        return this == UP || this == DOWN;
    }

    /** 是否为水平方向（墙面）。 */
    public boolean isHorizontal() {
        return !isVertical();
    }

    /**
     * 是否为指定轴向（0=X, 1=Y, 2=Z）。
     *
     * @param axis 0/1/2
     */
    public boolean onAxis(int axis) {
        return switch (axis) {
            case 0 -> this == WEST || this == EAST;
            case 1 -> isVertical();
            case 2 -> this == NORTH || this == SOUTH;
            default -> throw new IllegalArgumentException("axis must be 0, 1 or 2, got " + axis);
        };
    }
}
