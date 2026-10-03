package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.SupportFaceSelector;
import dev.cosmonauticsroll.api.detect.SurfaceNormal;
import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.api.detect.WallQuery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 脚踝水平墙面查询的游戏内实现（阶段 5 补丁8，PRD 2.4 / 3.4-4）。
 *
 * <p>支撑面采样点沿身体下方下沉，只会落在脚底所踩的方块里，因此普通方块路径
 * 看不到竖直墙面（身体竖直时任何方块都返回上面）。本实现改为在<b>脚踝高度</b>
 * 向外偏移一点取样，并只接受真正「高出一截」的方块：</p>
 * <ul>
 *   <li>探测点落在方块碰撞形状内 → 该方块朝向玩家的水平面即为墙面法线
 *       （用 {@link SupportFaceSelector} 以「朝向玩家」为 up 选取）；</li>
 *   <li>楼梯方块直接排除（楼梯由 {@code StairSurfaceResolver} 处理，不作为墙）；</li>
 *   <li>身体接近竖直时，要求方块顶面高出探测点 ≥ {@value #MIN_WALL_EXTENT} 格
 *       ——同层地板方块探测点在其上方、半砖/活板门等矮方块顶面不够高，
 *       都不会被误判为墙。</li>
 * </ul>
 */
public final class LevelWallQuery implements WallQuery {

    /** 形状边界判定容差（格）。 */
    private static final double EPSILON = 1.0e-4;

    /** 墙面方块需高出探测点的高度（格）：排除半砖（0.5）/台阶等矮方块。 */
    public static final double MIN_WALL_EXTENT = 0.5;

    /** 「身体接近竖直」阈值：|bodyUp.y| 大于该值时启用高度检查。 */
    public static final double UPRIGHT_BODY_UP_Y = 0.9;

    private final Level level;

    public LevelWallQuery(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        this.level = level;
    }

    @Override
    public SurfaceNormal query(Vec3d p, Vec3d outward, Vec3d bodyUp) {
        if (outward == null || outward.lengthSquared() < 1.0e-9) {
            return null;
        }
        BlockPos pos = BlockPos.containing(p.x, p.y, p.z);
        BlockState state = level.getBlockState(pos);
        if (StairBlockQuery.isStair(state)) {
            return null; // 楼梯交给楼梯逻辑处理，不作为墙
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape == null || shape.isEmpty()) {
            return null;
        }
        AABB aabb = shape.move(pos.getX(), pos.getY(), pos.getZ()).bounds();
        if (p.x < aabb.minX - EPSILON || p.x > aabb.maxX + EPSILON
                || p.y < aabb.minY - EPSILON || p.y > aabb.maxY + EPSILON
                || p.z < aabb.minZ - EPSILON || p.z > aabb.maxZ + EPSILON) {
            return null;
        }

        Vec3d up = bodyUp == null ? null : bodyUp.normalize();
        boolean upright = up == null || up.lengthSquared() < 0.5 || Math.abs(up.y) > UPRIGHT_BODY_UP_Y;
        if (upright && aabb.maxY - p.y < MIN_WALL_EXTENT) {
            return null; // 顶面不够高（半砖/台阶/同层方块）：不是墙
        }

        double dWest = p.x - aabb.minX;
        double dEast = aabb.maxX - p.x;
        double dDown = p.y - aabb.minY;
        double dUp = aabb.maxY - p.y;
        double dNorth = p.z - aabb.minZ;
        double dSouth = aabb.maxZ - p.z;
        // 以「朝向玩家」（-outward）为 up 取支撑面 = 面向玩家的那个面
        SurfaceNormal normal = SupportFaceSelector.select(dWest, dEast, dDown, dUp, dNorth, dSouth,
                outward.scale(-1.0));
        return normal != null && normal.isHorizontal() ? normal : null;
    }
}
