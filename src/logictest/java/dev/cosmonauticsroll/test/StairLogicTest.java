package dev.cosmonauticsroll.test;

import dev.cosmonauticsroll.api.detect.StairInfo;
import dev.cosmonauticsroll.api.detect.StairProgress;
import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.detect.StairStandingResolver;
import dev.cosmonauticsroll.rot.SmoothStandingRotation;

import java.util.List;

/**
 * 阶段 5 楼梯旋转 —— 设备 VM 逻辑单元测试（纯逻辑，无 Minecraft 依赖）。
 *
 * <p>覆盖 PRD 3.4：楼梯识别信息（朝向/半部）、行走进度计算（0~1 连续）、
 * 站立方向连续倾斜（progress × 45°）、多朝向冲突防抖、楼梯边缘进度防抖、
 * 平面→楼梯进度从 0 连续上升、重置清理。</p>
 */
public final class StairLogicTest {

    private static int passed = 0;
    private static int failed = 0;

    private StairLogicTest() {
    }

    public static void main(String[] args) {
        System.out.println("=== 阶段5 楼梯旋转 逻辑测试 ===");

        testStairInfoAscent();
        testStairProgressDirection();
        testResolverAllStep();
        testResolverMixedSlope();
        testResolverAllSlope();
        testResolverFloorToStairRamp();
        testResolverMultiFacingConflict();
        testResolverNoStair();
        testSmoothStairTargetDebounce();
        testSmoothStairTransition();
        testSmoothStairResetClearsDebounce();

        System.out.println("----------------------------------------");
        System.out.println("通过: " + passed + "  失败: " + failed);
        if (failed > 0) {
            throw new AssertionError("存在失败用例: " + failed);
        }
        System.out.println("全部通过");
    }

    // ---------- 工具 ----------

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("  [FAIL] " + name);
        }
    }

    private static double angle(Vec3d a, Vec3d b) {
        double dot = Math.max(-1.0, Math.min(1.0, a.dot(b)));
        return Math.acos(dot);
    }

    private static StairStandingResolver.StairSample sample(
            boolean onSlope, boolean onStep, StairInfo info) {
        return new StairStandingResolver.StairSample(onSlope, onStep, 0.5, info);
    }

    // ---------- 用例 ----------

    /** PRD 3.4-1：楼梯朝向 → 斜面上升方向。 */
    private static void testStairInfoAscent() {
        System.out.println("-- 楼梯朝向/上升方向 --");
        check("NORTH 上升方向 -Z", new StairInfo(StairInfo.Facing.NORTH, StairInfo.Half.BOTTOM)
                .ascentDirection().equals(new Vec3d(0, 0, -1)));
        check("SOUTH 上升方向 +Z", new StairInfo(StairInfo.Facing.SOUTH, StairInfo.Half.BOTTOM)
                .ascentDirection().equals(new Vec3d(0, 0, 1)));
        check("WEST 上升方向 -X", new StairInfo(StairInfo.Facing.WEST, StairInfo.Half.BOTTOM)
                .ascentDirection().equals(new Vec3d(-1, 0, 0)));
        check("EAST 上升方向 +X", new StairInfo(StairInfo.Facing.EAST, StairInfo.Half.BOTTOM)
                .ascentDirection().equals(new Vec3d(1, 0, 0)));
    }

    /** PRD 3.4-2/3.4-3：站立方向随进度连续倾斜 0→45°。 */
    private static void testStairProgressDirection() {
        System.out.println("-- 站立方向连续倾斜 --");
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d east = new Vec3d(1, 0, 0);

        StairProgress p0 = new StairProgress(east, 0.0, StairInfo.Facing.EAST);
        check("progress=0 → 竖直", angle(p0.standingDirection(), up) < 1e-9);

        StairProgress p05 = new StairProgress(east, 0.5, StairInfo.Facing.EAST);
        check("progress=0.5 → 倾斜 22.5°", Math.abs(angle(p05.standingDirection(), up)
                - Math.toRadians(22.5)) < 1e-6);
        check("progress=0.5 朝 +X 倾斜", p05.standingDirection().x > 0.3 && p05.standingDirection().y > 0.9);

        StairProgress p1 = new StairProgress(east, 1.0, StairInfo.Facing.EAST);
        check("progress=1 → 倾斜 45°", Math.abs(angle(p1.standingDirection(), up)
                - Math.toRadians(45.0)) < 1e-6);

        // 连续性：progress 每次 +0.1，角度单调上升
        double prev = -1;
        boolean monotonic = true;
        for (int i = 0; i <= 10; i++) {
            double a = angle(new StairProgress(east, i / 10.0, StairInfo.Facing.EAST)
                    .standingDirection(), up);
            if (a < prev) {
                monotonic = false;
            }
            prev = a;
        }
        check("角度随进度单调连续", monotonic);
    }

    /** PRD 3.4-2：全部命中水平台阶 → progress=0（普通水平站立）。 */
    private static void testResolverAllStep() {
        System.out.println("-- 全水平台阶 --");
        StairInfo info = new StairInfo(StairInfo.Facing.SOUTH, StairInfo.Half.BOTTOM);
        List<StairStandingResolver.StairSample> samples = List.of(
                sample(false, true, info), sample(false, true, info), sample(false, true, info),
                sample(false, true, info), sample(false, true, info));
        StairProgress p = StairStandingResolver.resolve(samples, new Vec3d(0, 1, 0));
        check("progress=0", p != null && Math.abs(p.progress()) < 1e-9);
        check("方向竖直", p != null && angle(p.standingDirection(), new Vec3d(0, 1, 0)) < 1e-9);
    }

    /** PRD 3.4-2/3.4-3：部分命中斜面 → 进度按斜面命中比例（相对全部采样点）。 */
    private static void testResolverMixedSlope() {
        System.out.println("-- 部分命中斜面 --");
        StairInfo info = new StairInfo(StairInfo.Facing.SOUTH, StairInfo.Half.BOTTOM);
        // 5 点中 2 点命中斜面 → progress = 2/5 = 0.4
        List<StairStandingResolver.StairSample> samples = List.of(
                sample(true, false, info), sample(true, false, info),
                sample(false, true, info), sample(false, true, info), sample(false, true, info));
        StairProgress p = StairStandingResolver.resolve(samples, new Vec3d(0, 1, 0));
        check("progress=0.4", p != null && Math.abs(p.progress() - 0.4) < 1e-9);
        check("方向向 +Z 倾斜 18°", p != null && Math.abs(angle(p.standingDirection(),
                new Vec3d(0, 1, 0)) - Math.toRadians(18.0)) < 1e-6);
    }

    /** PRD 3.4-2：全部命中斜面 → progress=1（跨两级台阶中点，45°）。 */
    private static void testResolverAllSlope() {
        System.out.println("-- 全命中斜面 --");
        StairInfo info = new StairInfo(StairInfo.Facing.EAST, StairInfo.Half.BOTTOM);
        List<StairStandingResolver.StairSample> samples = List.of(
                sample(true, false, info), sample(true, false, info), sample(true, false, info),
                sample(true, false, info), sample(true, false, info));
        StairProgress p = StairStandingResolver.resolve(samples, new Vec3d(0, 1, 0));
        check("progress=1", p != null && Math.abs(p.progress() - 1.0) < 1e-9);
        check("方向倾斜 45°", p != null && Math.abs(angle(p.standingDirection(),
                new Vec3d(0, 1, 0)) - Math.toRadians(45.0)) < 1e-6);
    }

    /** PRD 3.4-3：从平面走上楼梯——采样点从「全部命中台阶」逐渐过渡到「部分斜面」，进度连续上升。 */
    private static void testResolverFloorToStairRamp() {
        System.out.println("-- 平面→楼梯进度连续 --");
        StairInfo info = new StairInfo(StairInfo.Facing.SOUTH, StairInfo.Half.BOTTOM);
        double[] progresses = new double[6];
        for (int i = 0; i <= 5; i++) {
            // i 个点命中斜面，其余 5-i 个点命中水平台阶（站在楼梯最底部台阶 = 全台阶）
            List<StairStandingResolver.StairSample> samples = List.of(
                    sample(i >= 1, i < 1, info), sample(i >= 2, i < 2, info),
                    sample(i >= 3, i < 3, info), sample(i >= 4, i < 4, info), sample(i >= 5, i < 5, info));
            StairProgress p = StairStandingResolver.resolve(samples, new Vec3d(0, 1, 0));
            progresses[i] = p == null ? -1 : p.progress();
        }
        check("无斜面（全台阶）→ 0", progresses[0] == 0.0);
        check("全斜面 → 1", progresses[5] == 1.0);
        boolean increasing = true;
        for (int i = 1; i < 6; i++) {
            if (progresses[i] < progresses[i - 1]) {
                increasing = false;
            }
        }
        check("进度单调不降（连续爬升）", increasing);
    }

    /** PRD 3.4-5：多朝向冲突（两个不同朝向楼梯）→ 返回 null 保持当前方向。 */
    private static void testResolverMultiFacingConflict() {
        System.out.println("-- 多朝向冲突防抖 --");
        StairInfo south = new StairInfo(StairInfo.Facing.SOUTH, StairInfo.Half.BOTTOM);
        StairInfo east = new StairInfo(StairInfo.Facing.EAST, StairInfo.Half.BOTTOM);
        List<StairStandingResolver.StairSample> samples = List.of(
                sample(true, false, south), sample(true, false, east),
                sample(true, false, south), sample(true, false, south), sample(true, false, south));
        check("冲突 → null", StairStandingResolver.resolve(samples, new Vec3d(0, 1, 0)) == null);
    }

    /** 非楼梯命中（无任何楼梯采样）→ null（走普通表面逻辑）。 */
    private static void testResolverNoStair() {
        System.out.println("-- 无楼梯命中 --");
        List<StairStandingResolver.StairSample> samples = List.of(
                sample(false, false, null), sample(false, false, null), sample(false, false, null),
                sample(false, false, null), sample(false, false, null));
        check("无楼梯 → null", StairStandingResolver.resolve(samples, new Vec3d(0, 1, 0)) == null);
    }

    /** PRD 3.4-5：楼梯边缘进度防抖——进度变化小于死区时目标不更新；
     *  密集采样粒度 1/15≈0.067 > 死区 0.03，单粒度变化仍被接受（目标跟随灵敏，
     *  视觉平滑由平滑器保证）。 */
    private static void testSmoothStairTargetDebounce() {
        System.out.println("-- 楼梯边缘进度防抖 --");
        SmoothStandingRotation rotation = new SmoothStandingRotation();
        Vec3d east = new Vec3d(1, 0, 0);
        StairProgress p1 = new StairProgress(east, 0.6, StairInfo.Facing.EAST);
        StairProgress p2 = new StairProgress(east, 0.62, StairInfo.Facing.EAST); // 变化 0.02 < 死区

        check("首次接受", rotation.setStairTarget(p1));
        Vec3d t1 = rotation.target();
        check("微小进度变化被忽略（目标保持）", !rotation.setStairTarget(p2));
        check("目标未改变", rotation.target().equals(t1));

        StairProgress p3 = new StairProgress(east, 0.67, StairInfo.Facing.EAST); // 变化 0.05 ≥ 0.03（1 个粒度附近）
        check("单粒度进度变化被接受（目标跟随灵敏）", rotation.setStairTarget(p3));

        StairProgress p4 = new StairProgress(east, 0.9, StairInfo.Facing.EAST); // 变化 0.23 > 死区
        check("明显进度变化被接受", rotation.setStairTarget(p4));
    }

    /** PRD 3.4 验收：楼梯目标方向平滑过渡（不瞬间跳变）。 */
    private static void testSmoothStairTransition() {
        System.out.println("-- 楼梯平滑过渡 --");
        SmoothStandingRotation rotation = new SmoothStandingRotation();
        Vec3d east = new Vec3d(1, 0, 0);
        StairProgress target = new StairProgress(east, 1.0, StairInfo.Facing.EAST);

        Vec3d prev = rotation.current();
        double maxStep = 0;
        int ticks = 0;
        Vec3d current = null;
        for (int i = 0; i < 200; i++) {
            rotation.setStairTarget(target);
            current = rotation.update();
            maxStep = Math.max(maxStep, angle(prev, current));
            prev = current;
            ticks++;
            if (angle(current, target.standingDirection()) < 1e-6) {
                break;
            }
        }
        check("楼梯目标最终到达", angle(current, target.standingDirection()) < 1e-6);
        check("过渡每 tick 步长受限（平滑）", maxStep
                <= dev.cosmonauticsroll.api.rot.RotationSmoother.DEFAULT_MAX_RADIANS_PER_TICK + 1e-9);
        check("过渡 tick 数 > 1", ticks > 1);
    }

    /** reset 清除楼梯进度防抖状态（换楼梯/传送后可重新接受目标）。 */
    private static void testSmoothStairResetClearsDebounce() {
        System.out.println("-- reset 清除楼梯状态 --");
        SmoothStandingRotation rotation = new SmoothStandingRotation();
        Vec3d east = new Vec3d(1, 0, 0);
        rotation.setStairTarget(new StairProgress(east, 0.5, StairInfo.Facing.EAST));
        rotation.reset();
        check("reset 后重新接受楼梯目标", rotation.setStairTarget(
                new StairProgress(east, 0.5, StairInfo.Facing.EAST)));
        check("reset 后方向竖直", angle(rotation.current(), new Vec3d(0, 1, 0)) < 1e-9);
    }
}
