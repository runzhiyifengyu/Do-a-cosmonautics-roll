package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.StairInfo;
import dev.cosmonauticsroll.api.detect.StairProgress;
import dev.cosmonauticsroll.api.detect.SurfaceNormal;
import dev.cosmonauticsroll.api.detect.SurfaceQuery;
import dev.cosmonauticsroll.api.detect.Vec3d;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 楼梯表面组合检测（阶段 5，PRD 3.4）。
 *
 * <p>在 {@link FootSurfaceResolver} 的静态方块兜底路径中，先做楼梯识别：</p>
 * <ol>
 *   <li>脚底矩形采样点逐个查询方块；若命中楼梯行为方块（{@link StairBlockQuery#isStair}），
 *       按碰撞形状区分「斜面」/「水平台阶」；</li>
 *   <li>存在楼梯命中 → 用 {@link StairStandingResolver} 计算行走进度与目标方向
 *       （连续倾斜，PRD 3.4-2/3.4-3）；</li>
 *   <li>无楼梯命中 → 返回 {@code null}，走普通表面合并逻辑
 *       （普通完整方块仍用普通表面判断，PRD 3.4 验收）；</li>
 *   <li>脚底同时出现墙面方向与楼梯 → 区分「进入墙面」：墙面优先，
 *       楼梯不产生进度（PRD 3.4-4）；</li>
 *   <li>楼梯边缘进度波动（防抖）在旋转层用进度阈值处理（PRD 3.4-5）。</li>
 * </ol>
 *
 * <p>半砖/活板门等非楼梯方块：{@link StairBlockQuery#isStair} 返回 false，
 * 不进入楼梯逻辑（PRD 3.4 验收「不当作楼梯」）。</p>
 */
public final class StairSurfaceResolver {

    /** 楼梯采样下沉距离（格）：与普通检测一致，进入形状内部保证命中。 */
    public static final double STAIR_QUERY_OFFSET = 0.05;

    private final Level level;
    private final StairBlockQuery stairQuery;

    public StairSurfaceResolver(Level level) {
        this(level, new StairBlockQuery(level));
    }

    public StairSurfaceResolver(Level level, StairBlockQuery stairQuery) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        if (stairQuery == null) {
            throw new IllegalArgumentException("stairQuery must not be null");
        }
        this.level = level;
        this.stairQuery = stairQuery;
    }

    /**
     * 检测脚底楼梯站立方向。
     *
     * @param footCenter  脚底中心世界坐标
     * @param bodyUp      身体「上」方向（旋转后）
     * @param bodyForward 身体朝前方向（水平）
     * @return 楼梯站立进度；脚底未命中楼梯（或冲突需保持）时返回 {@code null}
     */
    public StairProgress detect(Vec3d footCenter, Vec3d bodyUp, Vec3d bodyForward) {
        // 楼梯用密集网格（3×5=15 点）：progress 粒度 1/15 ≈ 0.067，
        // 上楼时进度连续可见（PRD 3.4-2/3.4-3）。
        FootSamplingLayout layout = FootSamplingLayout.stairGrid();
        Vec3d bodyDown = bodyUp.scale(-1.0).normalize();

        List<StairStandingResolver.StairSample> samples = new ArrayList<>();
        int slopeHits = 0;
        boolean wallDetected = false;
        SurfaceQuery wallQuery = new LevelSurfaceQuery(level);

        for (FootSamplingLayout.SamplePoint point : layout.points()) {
            Vec3d worldOffset = point.worldOffset(bodyUp, bodyForward);
            Vec3d sample = footCenter.add(worldOffset).add(bodyDown.scale(STAIR_QUERY_OFFSET));
            BlockPos pos = BlockPos.containing(sample.x, sample.y, sample.z);
            BlockState state = level.getBlockState(pos);

            boolean onSlope = stairQuery.isOnSlope(sample, state, pos);
            boolean onStep = false;
            double stepTopY = 0;
            StairInfo info = null;
            if (stairQuery.isStair(state)) {
                info = stairQuery.info(state);
                if (!onSlope) {
                    // 水平台阶：采样点高度在台阶顶面（下半 0.5 / 上半 1.0）附近
                    // （容差覆盖 STAIR_QUERY_OFFSET 下沉量）
                    double top = stairQuery.stepTopY(state);
                    double py = sample.y - pos.getY();
                    if (Math.abs(py - top) <= STAIR_QUERY_OFFSET + 0.01) {
                        onStep = true;
                        stepTopY = top;
                    }
                } else {
                    slopeHits++;
                }
            } else {
                // 非楼梯方块：检查是否命中墙面（水平法线）——进入墙面时墙面优先
                SurfaceNormal n = wallQuery.query(sample);
                if (n != null && n.isHorizontal()) {
                    wallDetected = true;
                }
            }
            samples.add(new StairStandingResolver.StairSample(onSlope, onStep, stepTopY, info));
        }

        // 全部未命中楼梯 → 走普通逻辑
        boolean anyStair = false;
        for (StairStandingResolver.StairSample s : samples) {
            if (s.onSlope || s.onStep) {
                anyStair = true;
                break;
            }
        }
        if (!anyStair) {
            return null;
        }

        // PRD 3.4-4：区分正常上楼与进入墙面——若脚底同时命中墙面（水平法线），
        // 墙面优先（返回 null 走普通表面路径，身体转向墙面而非继续爬楼梯）。
        // （日志由 RotationTicker 统一输出，避免每 tick 刷屏）
        if (wallDetected) {
            return null;
        }

        StairProgress progress = StairStandingResolver.resolve(samples, bodyUp);
        return progress; // null = 多朝向冲突：保持当前方向（防抖）
    }
}
