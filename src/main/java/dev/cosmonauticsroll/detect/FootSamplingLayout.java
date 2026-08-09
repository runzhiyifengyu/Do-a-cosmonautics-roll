package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚底采样点布局（PRD 3.2-1：定义跟随玩家身体方向变化的脚部检测区域）。
 *
 * <p>采样点定义在玩家身体局部坐标系：以脚底中心为原点，三个轴为
 * 右（{@code right}）、前（{@code forward}）、下（{@code down}）。
 * 身体旋转时，局部轴由 {@code bodyUp} / {@code bodyForward} 决定，
 * 采样点世界坐标随之旋转，从而实现「脚部检测方向跟随玩家当前身体方向」。</p>
 *
 * <p>默认布局为脚底矩形：中心 + 四角，约覆盖 0.5 × 0.3 的脚底区域
 * （玩家碰撞箱 0.6 × 0.6 的脚底近似）。所有采样点位于脚底平面（down=0），
 * 即只检测脚部，不使用头部/身体其他部位作为站立依据（PRD 3.2-2）。</p>
 */
public final class FootSamplingLayout {

    /** 默认脚底矩形半宽（右/左方向）。 */
    public static final double DEFAULT_HALF_WIDTH = 0.25;

    /** 默认脚底矩形半深（前/后方向）。 */
    public static final double DEFAULT_HALF_DEPTH = 0.15;

    /** 单个采样点：局部坐标 (right, forward, down)。 */
    public static final class SamplePoint {
        public final double right;
        public final double forward;
        public final double down;

        public SamplePoint(double right, double forward, double down) {
            this.right = right;
            this.forward = forward;
            this.down = down;
        }

        /**
         * 将局部坐标旋转到世界坐标（相对脚底中心）。
         *
         * @param bodyUp       身体朝上单位向量
         * @param bodyForward  身体朝前单位向量
         * @return 相对脚底中心的世界偏移
         */
        public Vec3d worldOffset(Vec3d bodyUp, Vec3d bodyForward) {
            Vec3d rightVec = bodyForward.cross(bodyUp).normalize();
            Vec3d forwardVec = bodyForward.normalize();
            Vec3d downVec = bodyUp.scale(-1.0).normalize();
            return rightVec.scale(right)
                    .add(forwardVec.scale(forward))
                    .add(downVec.scale(down));
        }
    }

    private final List<SamplePoint> points;

    private FootSamplingLayout(List<SamplePoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("FootSamplingLayout requires at least one sample point");
        }
        this.points = points;
    }

    /** 默认脚底矩形布局：中心 + 四角。 */
    public static FootSamplingLayout rectangle() {
        return rectangle(DEFAULT_HALF_WIDTH, DEFAULT_HALF_DEPTH);
    }

    /** 单点布局：只有脚底中心一个采样点（用于聚焦验证旋转跟随等单一性质）。 */
    public static FootSamplingLayout single() {
        return new FootSamplingLayout(List.of(new SamplePoint(0, 0, 0)));
    }

    /** 指定半宽/半深的脚底矩形布局：中心 + 四角。 */
    public static FootSamplingLayout rectangle(double halfWidth, double halfDepth) {
        return new FootSamplingLayout(List.of(
                new SamplePoint(0, 0, 0),
                new SamplePoint(-halfWidth, -halfDepth, 0),
                new SamplePoint(halfWidth, -halfDepth, 0),
                new SamplePoint(-halfWidth, halfDepth, 0),
                new SamplePoint(halfWidth, halfDepth, 0)));
    }

    /** 楼梯密集网格布局（阶段 5，PRD 3.4-2）：3 列 × 5 行 = 15 点。
     *  用于楼梯行走进度计算——采样点越多，progress 粒度越细
     *  （1/15 ≈ 0.067），上楼时角度变化更连续可见。 */
    public static FootSamplingLayout stairGrid() {
        return stairGrid(DEFAULT_HALF_WIDTH, DEFAULT_HALF_DEPTH);
    }

    /** 指定半宽/半深的楼梯密集网格布局：3 列 × 5 行 = 15 点。 */
    public static FootSamplingLayout stairGrid(double halfWidth, double halfDepth) {
        List<SamplePoint> points = new ArrayList<>();
        int rows = 5;
        for (double right : new double[] {-halfWidth, 0.0, halfWidth}) {
            for (int i = 0; i < rows; i++) {
                double forward = -halfDepth + (2.0 * halfDepth * i) / (rows - 1);
                points.add(new SamplePoint(right, forward, 0));
            }
        }
        return new FootSamplingLayout(points);
    }

    /** 采样点列表（只读使用）。 */
    public List<SamplePoint> points() {
        return points;
    }

    public int size() {
        return points.size();
    }
}
