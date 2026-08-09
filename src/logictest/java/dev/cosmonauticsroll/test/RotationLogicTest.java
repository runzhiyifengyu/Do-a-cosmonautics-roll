package dev.cosmonauticsroll.test;

import dev.cosmonauticsroll.api.detect.FootSurfaceResult;
import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.api.rot.RotationSmoother;
import dev.cosmonauticsroll.rot.SmoothStandingRotation;
import dev.cosmonauticsroll.rot.StandingDirectionState;

/**
 * 阶段 4 平滑旋转 —— 设备 VM 逻辑单元测试（纯逻辑，无 Minecraft 依赖）。
 *
 * <p>覆盖 PRD 3.3：平滑过渡不跳变、地面/墙面/天花板方向过渡、边缘防抖、
 * 快速移动/快速离开表面、离开区域平滑恢复竖直、重置清理。</p>
 */
public final class RotationLogicTest {

    private static int passed = 0;
    private static int failed = 0;

    private RotationLogicTest() {
    }

    public static void main(String[] args) {
        System.out.println("=== 阶段4 平滑旋转 逻辑测试 ===");

        testNoInstantJump();
        testGroundToWallSmooth();
        testCeilingTransition();
        testAntiShakeSwitchLock();
        testAntiShakeMultipleKeepsCurrent();
        testFastLeaveKeepsDirection();
        testLeaveRegionRestoresVertical();
        testReset();
        testStandingDirectionState();
        testSmootherMath();

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

    /** 跑 n 个 tick 的 setTarget(结果) + update()，返回最终方向。 */
    private static Vec3d run(SmoothStandingRotation rotation, FootSurfaceResult result, int ticks) {
        Vec3d out = null;
        for (int i = 0; i < ticks; i++) {
            rotation.setTarget(result);
            out = rotation.update();
        }
        return out;
    }

    // ---------- 用例 ----------

    /** PRD 3.3 验收：身体转向不能瞬间跳变（每 tick 最大旋转角限制）。 */
    private static void testNoInstantJump() {
        System.out.println("-- 不瞬间跳变 --");
        RotationSmoother smoother = new RotationSmoother();
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d east = new Vec3d(1, 0, 0);
        // 第一次 update：当前 UP → 目标 EAST（90°），单 tick 只转最大角
        smoother.setTarget(east);
        Vec3d afterOne = smoother.update();
        check("单 tick 旋转角 <= 每 tick 最大角",
                angle(up, afterOne) <= RotationSmoother.DEFAULT_MAX_RADIANS_PER_TICK + 1e-9);
        check("单 tick 后未到目标（未瞬间跳变）", angle(afterOne, east) > 1.0e-3);
    }

    /** PRD 3.3-2/3.3 验收：地面→墙面平滑旋转约 90°，过程连续、最终到达。 */
    private static void testGroundToWallSmooth() {
        System.out.println("-- 地面到墙面平滑过渡 --");
        SmoothStandingRotation rotation = new SmoothStandingRotation();
        Vec3d east = new Vec3d(1, 0, 0);
        FootSurfaceResult wall = FootSurfaceResult.singleDirection(east);

        Vec3d prev = rotation.current();
        double maxStep = 0;
        int ticks = 0;
        Vec3d current = null;
        for (int i = 0; i < 200; i++) {
            rotation.setTarget(wall);
            current = rotation.update();
            double step = angle(prev, current);
            maxStep = Math.max(maxStep, step);
            prev = current;
            ticks++;
            if (angle(current, east) < 1e-6) {
                break;
            }
        }
        check("地面→墙面最终到达 EAST", angle(current, east) < 1e-6);
        check("过渡过程每 tick 步长 <= 最大角", maxStep <= RotationSmoother.DEFAULT_MAX_RADIANS_PER_TICK + 1e-9);
        check("过渡 tick 数 > 1（平滑而非跳变）", ticks > 1);
        // 90° / (4°/tick) ≈ 22.5 tick，允许一定容差
        check("约 90° 过渡耗时合理（10~60 tick）", ticks >= 10 && ticks <= 60);
    }

    /** PRD 3.3-2：地面→墙面→天花板（DOWN）连续过渡都能平滑到达。 */
    private static void testCeilingTransition() {
        System.out.println("-- 天花板方向过渡 --");
        SmoothStandingRotation rotation = new SmoothStandingRotation();
        Vec3d east = new Vec3d(1, 0, 0);
        Vec3d down = new Vec3d(0, -1, 0);
        run(rotation, FootSurfaceResult.singleDirection(east), 200); // 地面→墙面
        check("先转到墙面", angle(rotation.current(), east) < 1e-6);
        Vec3d current = run(rotation, FootSurfaceResult.singleDirection(down), 200); // 墙面→天花板
        check("墙面→天花板最终到达 DOWN", angle(current, down) < 1e-6);
    }

    /** PRD 3.3-3：边缘位置两个方向来回交替 → 切换锁阻止反复抖动。 */
    private static void testAntiShakeSwitchLock() {
        System.out.println("-- 边缘防抖（切换锁） --");
        // 切换锁较长，便于观察
        RotationSmoother smoother = new RotationSmoother(
                RotationSmoother.DEFAULT_MAX_RADIANS_PER_TICK,
                RotationSmoother.DEFAULT_SNAP_RADIANS,
                RotationSmoother.DEFAULT_DEAD_ZONE_RADIANS,
                6);
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d east = new Vec3d(1, 0, 0);
        Vec3d west = new Vec3d(-1, 0, 0);

        // 交替提交 EAST / WEST（模拟边缘抖动）：锁定期内方向变化过大被忽略
        smoother.setTarget(east); // 接受（首次）
        Vec3d a = smoother.update();
        smoother.setTarget(west); // 锁定期内，且与旧目标 EAST 夹角 180° > 90° → 忽略
        Vec3d b = smoother.update();
        smoother.setTarget(east); // 仍可能被忽略（锁定期内变化过大）
        Vec3d c = smoother.update();

        check("切换锁内 WEST 被忽略（目标仍为 EAST）", angle(smoother.target(), east) < 1e-9);
        check("切换锁内未反向锁定", angle(c, east) < Math.PI / 2.0);

        // 锁定期结束后允许切换
        for (int i = 0; i < 6; i++) {
            smoother.update();
        }
        smoother.setTarget(west);
        check("锁定期结束后目标切换为 WEST", angle(smoother.target(), west) < 1e-9);
    }

    /** PRD 3.3-3：MULTIPLE（墙角）保持当前方向，不跳变。 */
    private static void testAntiShakeMultipleKeepsCurrent() {
        System.out.println("-- 墙角 MULTIPLE 保持方向 --");
        SmoothStandingRotation rotation = new SmoothStandingRotation();
        Vec3d east = new Vec3d(1, 0, 0);
        run(rotation, FootSurfaceResult.singleDirection(east), 200); // 先转到墙面
        Vec3d onWall = rotation.current();
        check("已转到墙面", angle(onWall, east) < 1e-6);

        // 墙角：MULTIPLE → 保持墙面方向
        Vec3d kept = run(rotation, FootSurfaceResult.multiple(), 20);
        check("MULTIPLE 保持当前方向", angle(kept, onWall) < 1e-6);
    }

    /** PRD 3.3-4：快速离开表面（NONE）→ 保持最后方向，不瞬间跳回竖直。 */
    private static void testFastLeaveKeepsDirection() {
        System.out.println("-- 快速离开表面保持方向 --");
        SmoothStandingRotation rotation = new SmoothStandingRotation();
        Vec3d east = new Vec3d(1, 0, 0);
        run(rotation, FootSurfaceResult.singleDirection(east), 200);
        // 离开表面：NONE → 保持当前墙面方向
        Vec3d afterLeave = run(rotation, FootSurfaceResult.none(), 10);
        check("NONE 保持最后站立方向", angle(afterLeave, east) < 1e-6);
    }

    /** PRD 3.3-7：离开适用区域 → 平滑恢复竖直方向（不瞬间跳变）。 */
    private static void testLeaveRegionRestoresVertical() {
        System.out.println("-- 离开区域平滑恢复竖直 --");
        SmoothStandingRotation rotation = new SmoothStandingRotation();
        Vec3d east = new Vec3d(1, 0, 0);
        run(rotation, FootSurfaceResult.singleDirection(east), 200);
        check("已转到墙面", angle(rotation.current(), east) < 1e-6);

        rotation.leaveRegion();
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d current = rotation.current();
        boolean smooth = angle(current, up) > 1e-6 && angle(current, east) < Math.PI / 2.0;
        for (int i = 0; i < 200; i++) {
            current = rotation.update();
            if (angle(current, up) < 1e-6) {
                break;
            }
        }
        check("离开区域最终恢复竖直", angle(current, up) < 1e-6);
        check("恢复过程为平滑过渡（首 tick 未瞬间到竖直）", smooth);
    }

    /** 重置：立即回到竖直（传送/死亡重生，无需平滑）。 */
    private static void testReset() {
        System.out.println("-- 重置 --");
        SmoothStandingRotation rotation = new SmoothStandingRotation();
        Vec3d east = new Vec3d(1, 0, 0);
        run(rotation, FootSurfaceResult.singleDirection(east), 200);
        rotation.reset();
        check("reset 后立即竖直", angle(rotation.current(), new Vec3d(0, 1, 0)) < 1e-9);
        check("reset 后未处于恢复竖直模式", !rotation.isLeaving());
    }

    /** StandingDirectionState：SINGLE 更新，NONE/MULTIPLE 保持。 */
    private static void testStandingDirectionState() {
        System.out.println("-- 站立方向状态 --");
        StandingDirectionState state = new StandingDirectionState();
        check("初始竖直", angle(state.current(), new Vec3d(0, 1, 0)) < 1e-9);

        Vec3d east = new Vec3d(1, 0, 0);
        state.update(FootSurfaceResult.singleDirection(east));
        check("SINGLE 更新方向", angle(state.current(), east) < 1e-9);

        state.update(FootSurfaceResult.multiple());
        check("MULTIPLE 保持", angle(state.current(), east) < 1e-9);

        state.update(FootSurfaceResult.none());
        check("NONE 保持", angle(state.current(), east) < 1e-9);

        state.update(null);
        check("null 输入保持", angle(state.current(), east) < 1e-9);

        state.reset();
        check("reset 后竖直", angle(state.current(), new Vec3d(0, 1, 0)) < 1e-9);
    }

    /** RotationSmoother 数学：slerp 性质、参数校验、归一化。 */
    private static void testSmootherMath() {
        System.out.println("-- 平滑器数学 --");
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d east = new Vec3d(1, 0, 0);

        // slerp 中点：UP 与 EAST 的 45° 方向
        Vec3d mid = RotationSmoother.slerp(up, east, 0.5);
        check("slerp 中点夹角 45°", Math.abs(angle(up, mid) - Math.PI / 4.0) < 1e-6);
        check("slerp 结果为单位向量", Math.abs(mid.length() - 1.0) < 1e-9);

        // slerp 端点
        check("slerp t=0 为起点", RotationSmoother.slerp(up, east, 0.0).equals(up));
        check("slerp t=1 为终点", RotationSmoother.slerp(up, east, 1.0).equals(east));

        // 平行向量 slerp 不产生 NaN
        Vec3d parallel = RotationSmoother.slerp(up, up, 0.5);
        check("平行向量 slerp 无 NaN", Double.isFinite(parallel.x) && Double.isFinite(parallel.y) && Double.isFinite(parallel.z));

        // angleRadians
        check("同向夹角 0", Math.abs(RotationSmoother.angleRadians(up, up)) < 1e-9);
        check("反向夹角 π", Math.abs(RotationSmoother.angleRadians(up, new Vec3d(0, -1, 0)) - Math.PI) < 1e-9);

        // 参数校验
        boolean threw = false;
        try {
            new RotationSmoother(0, 0.01, 0.01, 0);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("maxRadiansPerTick<=0 被拒绝", threw);
        threw = false;
        try {
            new RotationSmoother(0.1, -1, 0.01, 0);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("snapRadians<0 被拒绝", threw);

        // 死区：目标与当前方向夹角过小 → 忽略（防抖）
        RotationSmoother s = new RotationSmoother();
        Vec3d tiny = new Vec3d(0.001, 1, 0).normalize(); // 与 UP 夹角约 0.057° < 死区 0.25°
        s.setTarget(tiny);
        check("死区内目标被忽略（防抖）", angle(s.target(), new Vec3d(0, 1, 0)) < 1e-9);
    }
}
