package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.StairInfo;
import dev.cosmonauticsroll.api.detect.Vec3d;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.Locale;

/**
 * 楼梯方块识别与碰撞形状查询的游戏内适配层（阶段 5，PRD 3.4-1/3.4-2）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>{@link #isStair}：判断 BlockState 是否为楼梯行为方块——原版
 *       {@link StairBlock}（instanceof，兼容继承原版楼梯行为的模组方块，
 *       PRD 2.5「继承或实现原版楼梯行为的楼梯」）；或模组楼梯
 *       （注册名含 {@code stair} 且同时具有 {@code shape/facing/half}
 *       三属性，两端通用字符串判断，PRD 2.5「模组添加的楼梯」）；</li>
 *   <li>{@link #info}：从 BlockState 解析 {@link StairInfo}（朝向 + 半部）；</li>
 *   <li>{@link #isOnSlope}：判断世界坐标采样点是否位于楼梯 45° 斜面的
 *       碰撞形状内（标准楼梯碰撞形状：下半水平台阶 + 上半 45° 斜面）。
 *       半砖/活板门等非楼梯方块返回 false（PRD 3.4 验收「不当作楼梯」）。</li>
 * </ul>
 */
public final class StairBlockQuery {

    /**
     * 采样点与斜面/台阶面的高度容差（格）：采样点会沿身体下方下沉
     * {@value StairSurfaceResolver#STAIR_QUERY_OFFSET}，容差需覆盖该下沉量。
     */
    public static final double SLOPE_TOLERANCE = 0.06;

    private final Level level;

    public StairBlockQuery(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        this.level = level;
    }

    /** BlockState 是否为楼梯行为方块。 */
    public static boolean isStair(BlockState state) {
        if (state == null) {
            return false;
        }
        if (state.getBlock() instanceof StairBlock) {
            return true;
        }
        // 模组楼梯：注册名含 stair 且具备标准楼梯三属性（两端通用字符串判断）
        net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = key != null ? key.getPath().toLowerCase(Locale.ROOT) : "";
        if (!path.contains("stair")) {
            return false;
        }
        return state.hasProperty(StairBlock.SHAPE)
                && state.hasProperty(StairBlock.FACING)
                && state.hasProperty(StairBlock.HALF);
    }

    /** 解析楼梯识别信息（调用前需 {@link #isStair} 为 true）。 */
    public StairInfo info(BlockState state) {
        Direction facing = state.getValue(StairBlock.FACING);
        Half half = state.getValue(StairBlock.HALF);
        return new StairInfo(
                switch (facing) {
                    case NORTH -> StairInfo.Facing.NORTH;
                    case SOUTH -> StairInfo.Facing.SOUTH;
                    case WEST -> StairInfo.Facing.WEST;
                    default -> StairInfo.Facing.EAST;
                },
                half == Half.TOP ? StairInfo.Half.TOP : StairInfo.Half.BOTTOM);
    }

    /**
     * 世界坐标采样点是否位于楼梯 45° 斜面碰撞形状内。
     *
     * <p>标准楼梯（下半）：水平台阶占 y∈[0,0.5]，斜面占 y∈[0.5,1.0]、
     * 沿 facing 方向从低侧（y=0.5）升到高侧（y=1.0）。上半楼梯镜像
     * （水平台阶占 y∈[0.5,1.0]，斜面占 y∈[0,0.5]）。</p>
     *
     * <p>非楼梯方块（半砖/活板门/完整方块）直接返回 false。</p>
     *
     * @param p     世界坐标采样点
     * @param state 采样点所在方块 BlockState
     * @param pos   采样点所在方块坐标
     */
    public boolean isOnSlope(Vec3d p, BlockState state, BlockPos pos) {
        if (state == null || !isStair(state)) {
            return false;
        }
        StairsShape shape = state.getValue(StairBlock.SHAPE);
        if (shape != StairsShape.STRAIGHT) {
            // 内/外转角楼梯：碰撞形状复杂（多个斜面板），保守不按斜面处理，
            // 交由普通表面逻辑（可能 MULTIPLE 不站立，符合「不错误站立」目标）。
            return false;
        }
        StairInfo info = info(state);
        double px = p.x - pos.getX();
        double py = p.y - pos.getY();
        double pz = p.z - pos.getZ();

        // 沿斜面方向的水平位置 t：0 = 低侧，1 = 高侧
        double t = switch (info.facing()) {
            case SOUTH -> pz;      // facing SOUTH：+Z 为高侧
            case NORTH -> 1.0 - pz;
            case EAST -> px;       // facing EAST：+X 为高侧
            case WEST -> 1.0 - px;
        };
        t = Math.max(0.0, Math.min(1.0, t));

        if (info.half() == StairInfo.Half.BOTTOM) {
            // 下半楼梯：斜面 y ∈ [0.5, 1.0]，y = 0.5 + 0.5 * t。
            // 站在水平台阶上时采样点 py≈0.45（下沉 0.05）低于台阶顶面 0.5，
            // 必须 py >= 0.5 才可能是斜面表面（否则台阶上会被误判为斜面）。
            double slopeY = 0.5 + 0.5 * t;
            return py >= 0.5 && Math.abs(py - slopeY) <= SLOPE_TOLERANCE;
        }
        // 上半楼梯：斜面 y ∈ [0, 0.5]，y = 0.5 - 0.5 * t（镜像条件）
        double slopeY = 0.5 - 0.5 * t;
        return py <= 0.5 && Math.abs(py - slopeY) <= SLOPE_TOLERANCE;
    }

    /** 采样点在楼梯水平台阶（非斜面部分）的高度（格，方块局部 y）。 */
    public double stepTopY(BlockState state) {
        Half half = state.getValue(StairBlock.HALF);
        return half == Half.TOP ? 1.0 : 0.5;
    }
}
