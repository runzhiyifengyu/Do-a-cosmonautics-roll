package dev.cosmonauticsroll.api.detect;

/**
 * 楼梯方块识别信息（阶段 5，PRD 3.4-1：识别原版和模组楼梯）。
 *
 * <p>纯逻辑、无 Minecraft 依赖。由游戏内适配层（{@code StairBlockQuery}）
 * 从 {@code BlockState} 解析：</p>
 * <ul>
 *   <li>原版楼梯：{@code net.minecraft.world.level.block.StairBlock}
 *       （属性 {@code shape/facing/half}，碰撞形状下半水平台阶 + 上半 45° 斜面）；</li>
 *   <li>模组楼梯：注册名含 {@code stair}（如 {@code modid:xxx_stairs}）且
 *       BlockState 具有 {@code shape/facing/half} 三属性 + 碰撞形状非完整方块；</li>
 *   <li>半砖/活板门等非楼梯方块：不满足上述条件，不识别为楼梯。</li>
 * </ul>
 */
public final class StairInfo {

    /** 楼梯朝向（水平四向）：斜面从该方向侧的低台阶（y=0.5）升向高侧（y=1.0）。
     *  与 Minecraft {@code StairBlock.FACING} 一致——楼梯的完整块一侧朝向 facing，
     *  玩家沿 facing 方向上行（ascentDirection == facing 方向）。 */
    public enum Facing {
        /** -Z：斜面沿 -Z 上升（高侧在 -Z）。 */
        NORTH,
        /** +Z：斜面沿 +Z 上升（高侧在 +Z）。 */
        SOUTH,
        /** -X：斜面沿 -X 上升（高侧在 -X）。 */
        WEST,
        /** +X：斜面沿 +X 上升（高侧在 +X）。 */
        EAST
    }

    /** 楼梯半部。 */
    public enum Half {
        /** 上半（斜面在下半之上）。 */
        TOP,
        /** 下半（标准楼梯，斜面占据上半）。 */
        BOTTOM
    }

    private final Facing facing;
    private final Half half;

    public StairInfo(Facing facing, Half half) {
        if (facing == null || half == null) {
            throw new IllegalArgumentException("facing and half must not be null");
        }
        this.facing = facing;
        this.half = half;
    }

    public Facing facing() {
        return facing;
    }

    public Half half() {
        return half;
    }

    /** 斜面上升的水平方向单位向量（XZ 平面）。 */
    public Vec3d ascentDirection() {
        return switch (facing) {
            case NORTH -> new Vec3d(0, 0, -1);
            case SOUTH -> new Vec3d(0, 0, 1);
            case WEST -> new Vec3d(-1, 0, 0);
            case EAST -> new Vec3d(1, 0, 0);
        };
    }

    @Override
    public String toString() {
        return "StairInfo{facing=" + facing + ", half=" + half + "}";
    }
}
