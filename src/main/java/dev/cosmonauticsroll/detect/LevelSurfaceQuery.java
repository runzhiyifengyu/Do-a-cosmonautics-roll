package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.SurfaceNormal;
import dev.cosmonauticsroll.api.detect.SurfaceQuery;
import dev.cosmonauticsroll.api.detect.Vec3d;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 基于 Minecraft 碰撞形状的表面法线查询（PRD 3.2-3：获取脚部接触的方块碰撞面法线）。
 *
 * <p>{@link SurfaceQuery} 的游戏内实现：给定世界坐标采样点，读取该点所在方块的
 * 碰撞形状（{@code BlockState.getCollisionShape}），若采样点位于形状内部则返回
 * 距离采样点最近的面之外法线（轴向近似）。</p>
 *
 * <p>限制：本实现按轴向面近似（全方块为精确结果）；楼梯/斜面的 45° 斜面在
 * 阶段 5 用专用楼梯逻辑处理，本查询对斜面会返回最近的轴向面（可能产生
 * MULTIPLE 而保守不站立，符合阶段 3「不错误站立」的目标）。</p>
 */
public final class LevelSurfaceQuery implements SurfaceQuery {

    /** 形状边界判定容差（格）。 */
    private static final double EPSILON = 1.0e-4;

    private final Level level;

    public LevelSurfaceQuery(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        this.level = level;
    }

    @Override
    public SurfaceNormal query(Vec3d p) {
        BlockPos pos = BlockPos.containing(p.x, p.y, p.z);
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (shape == null || shape.isEmpty()) {
            return null;
        }
        AABB aabb = shape.move(pos.getX(), pos.getY(), pos.getZ()).bounds();

        // 采样点必须位于形状内部（含边界）
        if (p.x < aabb.minX - EPSILON || p.x > aabb.maxX + EPSILON
                || p.y < aabb.minY - EPSILON || p.y > aabb.maxY + EPSILON
                || p.z < aabb.minZ - EPSILON || p.z > aabb.maxZ + EPSILON) {
            return null;
        }

        // 距离采样点最近的面之外法线即为接触法线
        double dWest = p.x - aabb.minX;   // 朝西面（法线 WEST）
        double dEast = aabb.maxX - p.x;   // 朝东面（法线 EAST）
        double dDown = p.y - aabb.minY;   // 朝下面（法线 DOWN）
        double dUp = aabb.maxY - p.y;     // 朝上面（法线 UP）
        double dNorth = p.z - aabb.minZ;  // 朝北面（法线 NORTH）
        double dSouth = aabb.maxZ - p.z;  // 朝南面（法线 SOUTH）

        double min = Math.min(
                Math.min(Math.min(dWest, dEast), Math.min(dDown, dUp)),
                Math.min(dNorth, dSouth));

        if (min == dUp) {
            return SurfaceNormal.UP;
        }
        if (min == dDown) {
            return SurfaceNormal.DOWN;
        }
        if (min == dEast) {
            return SurfaceNormal.EAST;
        }
        if (min == dWest) {
            return SurfaceNormal.WEST;
        }
        if (min == dSouth) {
            return SurfaceNormal.SOUTH;
        }
        return SurfaceNormal.NORTH;
    }
}
