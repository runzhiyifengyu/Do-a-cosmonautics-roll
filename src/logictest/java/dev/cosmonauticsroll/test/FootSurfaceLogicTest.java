package dev.cosmonauticsroll.test;

import dev.cosmonauticsroll.api.detect.FootSurfaceResult;
import dev.cosmonauticsroll.api.detect.SurfaceNormal;
import dev.cosmonauticsroll.api.detect.SurfaceQuery;
import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.detect.FootSamplingLayout;
import dev.cosmonauticsroll.detect.FootSurfaceDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段 3 脚部表面检测 —— 设备 VM 逻辑单元测试（纯逻辑，无 Minecraft 依赖）。
 *
 * <p>覆盖 PRD 3.2：地面/墙面/天花板统一方向表示、合并同一方向、
 * 区分单/多方向表面、身体旋转后检测跟随、只检测脚部、边缘部分接触。</p>
 */
public final class FootSurfaceLogicTest {

    private static int passed = 0;
    private static int failed = 0;

    private FootSurfaceLogicTest() {
    }

    public static void main(String[] args) {
        System.out.println("=== 阶段3 脚部表面检测 逻辑测试 ===");

        testGroundStanding();
        testWallStanding();
        testCeilingStanding();
        testCornerMultiple();
        testEdgePartialContact();
        testNoSurface();
        testRotationFollowsBody();
        testOnlyFeetSampled();
        testLayout();

        System.out.println("----------------------------------------");
        System.out.println("通过: " + passed + "  失败: " + failed);
        if (failed > 0) {
            throw new AssertionError("存在失败用例: " + failed);
        }
        System.out.println("全部通过");
    }

    /** 标准站立姿态：身体竖直，朝北。 */
    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private static final Vec3d FORWARD = new Vec3d(0, 0, -1);

    // ---------- 假世界（模拟 MC 碰撞形状的法线查询） ----------

    /** 地面：y <= 0 为方块，法线朝上 UP。 */
    private static SurfaceQuery ground() {
        return p -> p.y <= 0.0 ? SurfaceNormal.UP : null;
    }

    /** 天花板：y >= 10 为方块，法线朝下 DOWN。 */
    private static SurfaceQuery ceiling() {
        return p -> p.y >= 10.0 ? SurfaceNormal.DOWN : null;
    }

    /** 东墙：x < 0 为方块（y 任意），法线朝东 EAST。 */
    private static SurfaceQuery eastWall() {
        return p -> p.x < -1.0e-6 ? SurfaceNormal.EAST : null;
    }

    /** 地面 + 东墙：y <= 0 为地面（UP）；x < 0 且 y > 0 为东墙（EAST）。 */
    private static SurfaceQuery groundPlusEastWall() {
        return p -> {
            if (p.y <= 0.0) {
                return SurfaceNormal.UP;
            }
            if (p.x < -1.0e-6) {
                return SurfaceNormal.EAST;
            }
            return null;
        };
    }

    // ---------- 用例 ----------

    /** PRD 3.2-6：地面 → 统一方向表示 UP。 */
    private static void testGroundStanding() {
        System.out.println("-- 地面站立 --");
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), ground());
        FootSurfaceResult r = detector.detect(new Vec3d(0, 0.1, 0), UP, FORWARD);
        check("地面站立 SINGLE", r.isSingle());
        check("地面法线 UP", r.normal() == SurfaceNormal.UP);
    }

    /** PRD 3.2-6：墙面 → 统一方向表示 EAST（身体横着贴墙）。 */
    private static void testWallStanding() {
        System.out.println("-- 墙面站立 --");
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), eastWall());
        // 身体 up 朝向 +X（横着站在东墙上），脚底中心贴墙
        FootSurfaceResult r = detector.detect(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), FORWARD);
        check("墙面站立 SINGLE", r.isSingle());
        check("墙面法线 EAST", r.normal() == SurfaceNormal.EAST);
    }

    /** PRD 3.2-6：天花板 → 统一方向表示 DOWN（身体倒立）。 */
    private static void testCeilingStanding() {
        System.out.println("-- 天花板站立 --");
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), ceiling());
        FootSurfaceResult r = detector.detect(new Vec3d(0, 10.1, 0), new Vec3d(0, -1, 0), FORWARD);
        check("天花板站立 SINGLE", r.isSingle());
        check("天花板法线 DOWN", r.normal() == SurfaceNormal.DOWN);
    }

    /**
     * PRD 3.2-5 验收：同时接触两个不同方向表面不会错误站立。
     * 从地面向墙面过渡的倾斜姿态 + 墙角：脚底一半在地面（UP）、一半贴墙（EAST）→ MULTIPLE。
     */
    private static void testCornerMultiple() {
        System.out.println("-- 墙角多方向表面 --");
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), groundPlusEastWall());
        // 身体向 +X 倾斜约 26.6°，脚底横跨地面与东墙交界
        Vec3d tiltUp = new Vec3d(1, 0.5, 0).normalize();
        FootSurfaceResult r = detector.detect(new Vec3d(0, 0.1, 0), tiltUp, FORWARD);
        check("墙角 MULTIPLE", r.isMultiple());
    }

    /** PRD 3.2-4：边缘部分接触（部分采样点悬空），方向一致仍 SINGLE。 */
    private static void testEdgePartialContact() {
        System.out.println("-- 边缘部分接触 --");
        // 地面只覆盖 x < 1 区域
        SurfaceQuery partialGround = p ->
                (p.y <= 0.0 && p.x < 1.0) ? SurfaceNormal.UP : null;
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), partialGround);
        // 脚底中心在边缘外侧：x 范围约 0.85~1.35，部分点悬空
        FootSurfaceResult r = detector.detect(new Vec3d(1.1, 0.1, 0), UP, FORWARD);
        check("边缘部分接触仍 SINGLE", r.isSingle());
        check("边缘法线 UP", r.normal() == SurfaceNormal.UP);
    }

    /** PRD 3.2：无表面 → NONE（太空/自由状态）。 */
    private static void testNoSurface() {
        System.out.println("-- 无表面 --");
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), ground());
        FootSurfaceResult r = detector.detect(new Vec3d(0, 100, 0), UP, FORWARD);
        check("无表面 NONE", r.isNone());
    }

    /**
     * PRD 3.2 验收：身体旋转后脚部检测也随之旋转。
     * 单点布局聚焦验证：同一位置同一世界（只有东墙），
     * 身体转向墙 → 采样点随身体下沉进墙内 → EAST；
     * 身体竖直 → 采样点沿 -Y 下沉，不进入墙 → NONE（检测跟随身体而非世界）。
     */
    private static void testRotationFollowsBody() {
        System.out.println("-- 身体旋转后检测跟随 --");
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.single(), eastWall());
        Vec3d footCenter = new Vec3d(0, 0, 0);

        // 身体横过来贴墙：检测区域随身体旋转，采样点下沉进墙内 → EAST
        FootSurfaceResult rotated = detector.detect(footCenter, new Vec3d(1, 0, 0), FORWARD);
        check("身体转向墙后检测到 EAST", rotated.isSingle() && rotated.normal() == SurfaceNormal.EAST);

        // 身体竖直：采样点沿 -Y 下沉（x=0 不在墙内）→ NONE
        FootSurfaceResult upright = detector.detect(footCenter, UP, FORWARD);
        check("身体竖直时检测不到墙", upright.isNone());
    }

    /**
     * PRD 3.2-2 验收：只检测脚部，不使用头部/身体其他部位作为站立依据。
     * 记录检测器实际查询的所有采样点，断言全部落在脚底矩形附近。
     */
    private static void testOnlyFeetSampled() {
        System.out.println("-- 只检测脚部 --");
        List<Vec3d> queried = new ArrayList<>();
        SurfaceQuery recording = p -> {
            queried.add(p);
            return p.y <= 0.0 ? SurfaceNormal.UP : null;
        };
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), recording);
        detector.detect(new Vec3d(0, 0.1, 0), UP, FORWARD);

        check("采样点数量 = 布局点数(5)", queried.size() == 5);
        for (Vec3d p : queried) {
            // 下沉 0.1 后 y 应接近脚底中心下方（0.0 附近），x/z 在脚底矩形内
            check("采样点 x 在脚底范围内 |x|<=0.25", Math.abs(p.x) <= 0.25 + 1e-6);
            check("采样点 z 在脚底范围内 |z|<=0.15", Math.abs(p.z) <= 0.15 + 1e-6);
            check("采样点 y 在脚底附近 y∈[-0.1,0.1]", p.y >= -0.1 - 1e-6 && p.y <= 0.1 + 1e-6);
        }
    }

    /** 布局：默认 5 点，覆盖约 0.5 × 0.3 脚底矩形。 */
    private static void testLayout() {
        System.out.println("-- 采样点布局 --");
        FootSamplingLayout layout = FootSamplingLayout.rectangle();
        check("默认布局 5 点", layout.size() == 5);
        check("默认半宽 0.25", FootSamplingLayout.DEFAULT_HALF_WIDTH == 0.25);
        check("默认半深 0.15", FootSamplingLayout.DEFAULT_HALF_DEPTH == 0.15);

        // 空/空布局应拒绝
        boolean threw = false;
        try {
            new FootSurfaceDetector(null, ground());
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("null 布局被拒绝", threw);
        threw = false;
        try {
            new FootSurfaceDetector(FootSamplingLayout.rectangle(), null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("null query 被拒绝", threw);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name);
        }
    }
}
