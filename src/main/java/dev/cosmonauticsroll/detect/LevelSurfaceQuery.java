package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.SupportFaceSelector;
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
 * 碰撞形状（{@code BlockState.getCollisionShape}），若采样点位于形状内部则由
 * {@link SupportFaceSelector} 返回「与身体上方向对齐」的支撑面法线（轴向近似）。</p>
 *
 * <p>2026-08-29 修复：此前按「距离采样点最近的面」判定，采样点靠近平地方块竖直
 * 边界时会返回侧面（幻影墙面）→ 连续平地误判 MULTIPLE（34 条脚部检测中 24 条）；
 * 改为支撑面判定后，身体竖直时恒取上面，幻影侧面被排除。已知差异：方块路径的
 * 「地面 + 墙面」墙角会返回 SINGLE（支撑面）；Sable 子世界路径仍可 MULTIPLE。</p>
 *
 * <p>限制：本实现按轴向面近似（全方块为精确结果）；楼梯/斜面的 45° 斜面在
 * 阶段 5 用专用楼梯逻辑处理，本查询对斜面会返回支撑面轴向近似。</p>
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
    public SurfaceNormal query(Vec3d p, Vec3d bodyUp) {
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

        // 六个面的距离：由 SupportFaceSelector 按「与身体上方向对齐」选支撑面
        // （bodyUp 缺失时退化为最近面，保持旧行为）。
        double dWest = p.x - aabb.minX;   // 朝西面（法线 WEST）
        double dEast = aabb.maxX - p.x;   // 朝东面（法线 EAST）
        double dDown = p.y - aabb.minY;   // 朝下面（法线 DOWN）
        double dUp = aabb.maxY - p.y;     // 朝上面（法线 UP）
        double dNorth = p.z - aabb.minZ;  // 朝北面（法线 NORTH）
        double dSouth = aabb.maxZ - p.z;  // 朝南面（法线 SOUTH）
        return SupportFaceSelector.select(dWest, dEast, dDown, dUp, dNorth, dSouth, bodyUp);
    }
}
