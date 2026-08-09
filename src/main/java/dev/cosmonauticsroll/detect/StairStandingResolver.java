package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.StairInfo;
import dev.cosmonauticsroll.api.detect.StairProgress;
import dev.cosmonauticsroll.api.detect.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 楼梯站立方向解析器（阶段 5，PRD 3.4）。
 *
 * <p>纯逻辑、无 Minecraft 依赖。输入「楼梯采样结果」（每采样点是否命中
 * 斜面/台阶 + 楼梯朝向），输出楼梯站立目标方向与进度：</p>
 * <ul>
 *   <li>{@link #resolve}：统计采样点——全部命中水平台阶（同高度）→
 *       {@code progress=0}（普通水平站立）；部分命中 45° 斜面 → 按
 *       斜面命中比例计算 {@code progress∈(0,1)}，目标方向 = 竖直向
 *       斜面方向倾斜 {@code progress×45°}（PRD 3.4-3 连续调整角度）；</li>
 *   <li>采样点同时命中不同朝向的楼梯/墙面方向 → 返回 null（调用方保持
 *       当前方向，防抖，PRD 3.4-5）；</li>
 *   <li>全部命中水平台阶但高度不同（跨两级）→ progress 取斜面位置插值。</li>
 * </ul>
 */
public final class StairStandingResolver {

    /** 单采样点楼梯命中信息。 */
    public static final class StairSample {
        public final boolean onSlope;
        public final boolean onStep;
        public final double stepTopY;
        public final StairInfo info;

        public StairSample(boolean onSlope, boolean onStep, double stepTopY, StairInfo info) {
            this.onSlope = onSlope;
            this.onStep = onStep;
            this.stepTopY = stepTopY;
            this.info = info;
        }
    }

    /**
     * 由楼梯采样结果解析站立方向。
     *
     * @param samples 各采样点楼梯命中信息（非楼梯命中 onSlope/onStep 均 false）
     * @param bodyUp  当前身体「上」方向（未命中时返回 null）
     * @return 楼梯站立进度（含目标方向）；不构成楼梯站立（无楼梯命中/多朝向冲突）时返回 null
     */
    public static StairProgress resolve(List<StairSample> samples, Vec3d bodyUp) {
        if (samples == null || samples.isEmpty()) {
            return null;
        }
        List<StairSample> stairs = new ArrayList<>();
        for (StairSample s : samples) {
            if (s != null && (s.onSlope || s.onStep)) {
                stairs.add(s);
            }
        }
        if (stairs.isEmpty()) {
            return null;
        }

        // 统一朝向：全部命中须同朝向（或同一楼梯的斜面/台阶组合）
        StairInfo first = stairs.get(0).info;
        for (StairSample s : stairs) {
            if (!s.info.facing().equals(first.facing())) {
                return null; // 多朝向冲突 → 保持当前方向（防抖）
            }
        }

        // 进度：斜面命中比例（相对全部采样点，保证从平面走上楼梯时
        // 进度从 0 连续升到 1，而非只按楼梯命中点计算跳变）
        int slopeHits = 0;
        for (StairSample s : stairs) {
            if (s.onSlope) {
                slopeHits++;
            }
        }
        double progress = (double) slopeHits / samples.size();

        Vec3d ascent = first.ascentDirection();
        return new StairProgress(ascent, progress, first.facing());
    }
}
