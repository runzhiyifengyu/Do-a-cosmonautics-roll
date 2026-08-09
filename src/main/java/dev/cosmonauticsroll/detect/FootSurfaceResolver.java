package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.FootSurfaceResult;
import dev.cosmonauticsroll.api.detect.SurfaceQuery;
import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.debug.Debug;
import dev.cosmonauticsroll.api.detect.StairProgress;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 脚部表面组合检测（阶段 4 起正式旋转逻辑使用，阶段 3 的调试观察共用同一实现）。
 *
 * <p>检测顺序（与阶段 3 RegionDebugTicker 一致）：</p>
 * <ol>
 *   <li><b>Sable 子世界（物理化方块）优先</b>：脚底薄片包围盒内所有相交子世界
 *       的方向都收集（阶段 4 增强：不再只取第一个，避免物理化墙角只返回单一方向）；</li>
 *   <li>收集到多个不同方向 → MULTIPLE（不站立）；单一方向 → SINGLE（可站立）；</li>
 *   <li>子世界未命中时，<b>静态方块兜底</b>：先楼梯识别（阶段 5，
 *       {@link StairSurfaceResolver}，source=stair，带行走进度），
 *       再脚底矩形 5 点采样 + 法线合并（多方向 → MULTIPLE，PRD 3.2-5）。</li>
 * </ol>
 *
 * <p>返回结果带来源标记（{@code sublevel} / {@code stair} / {@code block} / {@code none}），
 * 供调试日志定位（PRD 3.3 验收需观察旋转过程与防抖日志）。</p>
 */
public final class FootSurfaceResolver {

    private final Level level;

    public FootSurfaceResolver(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        this.level = level;
    }

    /** 检测结果（结果 + 来源）。 */
    public static final class Resolved {
        public final FootSurfaceResult result;
        /** 来源：{@code sublevel}（物理化方块）/ {@code stair}（楼梯）/ {@code block}（静态方块）/ {@code none}。 */
        public final String source;
        /** 楼梯行走进度（仅 {@code source=stair} 时非 null，供旋转层进度防抖）。 */
        public final StairProgress stair;

        public Resolved(FootSurfaceResult result, String source) {
            this(result, source, null);
        }

        public Resolved(FootSurfaceResult result, String source, StairProgress stair) {
            this.result = result;
            this.source = source;
            this.stair = stair;
        }
    }

    /**
     * 检测玩家脚部表面。
     *
     * @param player 玩家（服务端）
     * @param bodyUp 身体「上」方向（阶段 4 起为旋转后的真实身体方向）
     * @return 检测结果（含来源）
     */
    public Resolved resolve(ServerPlayer player, Vec3d bodyUp) {
        Vec3d footCenter = footCenter(player);
        Vec3d bodyForward = bodyForward(player);

        // 1) Sable 子世界优先：收集脚底薄片包围盒内所有相交子世界方向
        List<Vec3d> subLevelDirs = SableSubLevelDetector.detectStandingDirections(player);
        if (subLevelDirs != null && !subLevelDirs.isEmpty()) {
            FootSurfaceResult result = merge(subLevelDirs);
            if (result != null) {
                return new Resolved(result, "sublevel");
            }
        }

        // 2) 静态方块兜底：先楼梯（阶段 5，PRD 3.4），再普通表面
        StairProgress stair = new StairSurfaceResolver(level).detect(footCenter, bodyUp, bodyForward);
        if (stair != null) {
            // 楼梯站立方向作为 SINGLE 结果交给旋转层（source=stair，带进度供防抖）
            return new Resolved(FootSurfaceResult.singleDirection(stair.standingDirection()),
                    "stair", stair);
        }
        SurfaceQuery query = new LevelSurfaceQuery(level);
        FootSurfaceDetector detector = new FootSurfaceDetector(FootSamplingLayout.rectangle(), query);
        FootSurfaceResult blockResult = detector.detect(footCenter, bodyUp, bodyForward);
        if (!blockResult.isNone()) {
            return new Resolved(blockResult, "block");
        }
        return new Resolved(FootSurfaceResult.none(), "none");
    }

    /** 合并多个子世界方向：单一方向 → SINGLE；多个不同方向 → MULTIPLE；空 → null（走方块兜底）。 */
    private static FootSurfaceResult merge(List<Vec3d> dirs) {
        Vec3d first = null;
        boolean multiple = false;
        for (Vec3d dir : dirs) {
            if (dir == null || dir.lengthSquared() == 0.0) {
                continue;
            }
            Vec3d n = dir.normalize();
            if (first == null) {
                first = n;
            } else if (angleRadians(first, n) > 1.0e-3) {
                multiple = true;
                break;
            }
        }
        if (first == null) {
            return null;
        }
        return multiple ? FootSurfaceResult.multiple() : FootSurfaceResult.singleDirection(first);
    }

    private static double angleRadians(Vec3d a, Vec3d b) {
        double dot = Math.max(-1.0, Math.min(1.0, a.dot(b)));
        return Math.acos(dot);
    }

    /** 脚底中心：碰撞箱底部中心。 */
    private static Vec3d footCenter(Entity entity) {
        AABB box = entity.getBoundingBox();
        return new Vec3d((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
    }

    /** 身体朝前方向（仅用偏航角）：0° = 朝南 +Z，顺时针为正。 */
    private static Vec3d bodyForward(Entity entity) {
        double yawRad = Math.toRadians(entity.getYRot());
        return new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
    }

    /** 供调试日志输出采样点详情（与阶段 3 一致）。 */
    public static void logFootBlockDetail(Level level, Vec3d footCenter, Vec3d bodyUp, Vec3d bodyForward) {
        Vec3d bodyDown = bodyUp.scale(-1.0).normalize();
        Vec3d footSample = footCenter.add(bodyDown.scale(FootSurfaceDetector.DEFAULT_QUERY_OFFSET));
        logBlockAt(level, "脚底中心下方", footSample);
        FootSamplingLayout layout = FootSamplingLayout.rectangle();
        for (int i = 0; i < layout.points().size(); i++) {
            FootSamplingLayout.SamplePoint p = layout.points().get(i);
            Vec3d sample = footCenter
                    .add(p.worldOffset(bodyUp, bodyForward))
                    .add(bodyDown.scale(FootSurfaceDetector.DEFAULT_QUERY_OFFSET));
            logBlockAt(level, "采样点" + i, sample);
        }
    }

    private static void logBlockAt(Level level, String label, Vec3d p) {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(p.x, p.y, p.z);
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, pos);
        Debug.log("  脚部采样[{}]：sample=({},{},{}) block={} pos={} collisionEmpty={}",
                label, String.format("%.2f", p.x), String.format("%.2f", p.y), String.format("%.2f", p.z),
                state.getBlock().toString(), pos, shape.isEmpty());
    }
}
