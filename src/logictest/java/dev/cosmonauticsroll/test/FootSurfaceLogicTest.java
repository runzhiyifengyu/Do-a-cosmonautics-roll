package dev.cosmonauticsroll.test;

import dev.cosmonauticsroll.api.detect.FootSurfaceResult;
import dev.cosmonauticsroll.api.detect.MovementIntentTracker;
import dev.cosmonauticsroll.api.detect.SupportFaceSelector;
import dev.cosmonauticsroll.api.detect.SurfaceNormal;
import dev.cosmonauticsroll.api.detect.SurfaceQuery;
import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.api.detect.WallQuery;
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
        testVec3dMath();
        testContinuousDirection();
        testSupportFaceSelection();
        testFlatGroundBoundaryRegression();
        testAnkleProbeLayout();
        testWallProbeDetection();
        testMovementIntent();

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
        return (p, bodyUp) -> p.y <= 0.0 ? SurfaceNormal.UP : null;
    }

    /** 天花板：y >= 10 为方块，法线朝下 DOWN。 */
    private static SurfaceQuery ceiling() {
        return (p, bodyUp) -> p.y >= 10.0 ? SurfaceNormal.DOWN : null;
    }

    /** 东墙：x < 0 为方块（y 任意），法线朝东 EAST。 */
    private static SurfaceQuery eastWall() {
        return (p, bodyUp) -> p.x < -1.0e-6 ? SurfaceNormal.EAST : null;
    }

    /** 地面 + 东墙：y <= 0 为地面（UP）；x < 0 且 y > 0 为东墙（EAST）。 */
    private static SurfaceQuery groundPlusEastWall() {
        return (p, bodyUp) -> {
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
        check("地面法线 UP", r.normal().equals(SurfaceNormal.UP.vector()));
    }

    /** PRD 3.2-6：墙面 → 统一方向表示 EAST（身体横着贴墙）。 */
    private static void testWallStanding() {
        System.out.println("-- 墙面站立 --");
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), eastWall());
        // 身体 up 朝向 +X（横着站在东墙上），脚底中心贴墙
        FootSurfaceResult r = detector.detect(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), FORWARD);
        check("墙面站立 SINGLE", r.isSingle());
        check("墙面法线 EAST", r.normal().equals(SurfaceNormal.EAST.vector()));
    }

    /** PRD 3.2-6：天花板 → 统一方向表示 DOWN（身体倒立）。 */
    private static void testCeilingStanding() {
        System.out.println("-- 天花板站立 --");
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), ceiling());
        FootSurfaceResult r = detector.detect(new Vec3d(0, 10.1, 0), new Vec3d(0, -1, 0), FORWARD);
        check("天花板站立 SINGLE", r.isSingle());
        check("天花板法线 DOWN", r.normal().equals(SurfaceNormal.DOWN.vector()));
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
        SurfaceQuery partialGround = (p, bodyUp) ->
                (p.y <= 0.0 && p.x < 1.0) ? SurfaceNormal.UP : null;
        FootSurfaceDetector detector = new FootSurfaceDetector(
                FootSamplingLayout.rectangle(), partialGround);
        // 脚底中心在边缘外侧：x 范围约 0.85~1.35，部分点悬空
        FootSurfaceResult r = detector.detect(new Vec3d(1.1, 0.1, 0), UP, FORWARD);
        check("边缘部分接触仍 SINGLE", r.isSingle());
        check("边缘法线 UP", r.normal().equals(SurfaceNormal.UP.vector()));
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
        check("身体转向墙后检测到 EAST", rotated.isSingle() && rotated.normal().equals(SurfaceNormal.EAST.vector()));

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
        SurfaceQuery recording = (p, bodyUp) -> {
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

    /** Vec3d 数学：加减/缩放/点积/叉积/归一化/equals。 */
    private static void testVec3dMath() {
        System.out.println("-- Vec3d 数学 --");
        Vec3d a = new Vec3d(1, 2, 3);
        Vec3d b = new Vec3d(4, 5, 6);
        check("加法", a.add(b).equals(new Vec3d(5, 7, 9)));
        check("减法", b.subtract(a).equals(new Vec3d(3, 3, 3)));
        check("缩放", a.scale(2).equals(new Vec3d(2, 4, 6)));
        check("点积", a.dot(b) == 1 * 4 + 2 * 5 + 3 * 6);
        check("叉积", a.cross(b).equals(new Vec3d(2 * 6 - 3 * 5, 3 * 4 - 1 * 6, 1 * 5 - 2 * 4)));
        check("归一化长度=1", Math.abs(a.normalize().length() - 1.0) < 1e-9);
        check("equals 相同值", new Vec3d(1, 2, 3).equals(a));
        check("equals 不同值", !new Vec3d(1, 2, 4).equals(a));
        check("hashCode 一致", new Vec3d(1, 2, 3).hashCode() == a.hashCode());
    }

    /** 连续方向（物理化方块/子世界）：singleDirection 归一化、null 拒绝。 */
    private static void testContinuousDirection() {
        System.out.println("-- 连续方向（子世界） --");
        // 任意方向（非六轴向）：如 45° 斜向
        Vec3d dir = new Vec3d(1, 1, 0);
        FootSurfaceResult r = FootSurfaceResult.singleDirection(dir);
        check("任意方向 SINGLE", r.isSingle());
        check("任意方向归一化", Math.abs(r.normal().length() - 1.0) < 1e-9);
        check("任意方向方向正确", r.normal().equals(new Vec3d(1, 1, 0).normalize()));

        boolean threw = false;
        try {
            FootSurfaceResult.singleDirection(null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("singleDirection(null) 被拒绝", threw);
    }

    /**
     * 阶段 5 补丁（2026-08-29）：支撑面选择——消除「几何最近面」在平地边界处
     * 返回幻影侧面、把连续平地误判成 MULTIPLE 的问题。
     */
    private static void testSupportFaceSelection() {
        System.out.println("-- 支撑面选择（幻影侧面） --");
        // 采样点在一个全方块 [0,1]^3 内部、靠近东面：距东面 0.05、距上面 0.10
        double dWest = 0.95;
        double dEast = 0.05;
        double dDown = 0.90;
        double dUp = 0.10;
        double dNorth = 0.50;
        double dSouth = 0.50;

        check("旧最近面规则取东面（=幻影侧面，回归对照）",
                nearestFace(dWest, dEast, dDown, dUp, dNorth, dSouth) == SurfaceNormal.EAST);
        check("身体竖直时取上面（不取更近的东面）",
                SupportFaceSelector.select(dWest, dEast, dDown, dUp, dNorth, dSouth, UP) == SurfaceNormal.UP);
        check("身体倒立时取下面（天花板）",
                SupportFaceSelector.select(dWest, dEast, dDown, dUp, dNorth, dSouth, new Vec3d(0, -1, 0))
                        == SurfaceNormal.DOWN);
        check("身体朝东时取东面（墙面站立）",
                SupportFaceSelector.select(dWest, dEast, dDown, dUp, dNorth, dSouth, new Vec3d(1, 0, 0))
                        == SurfaceNormal.EAST);
        check("bodyUp 缺失时退化为最近面（东面）",
                SupportFaceSelector.select(dWest, dEast, dDown, dUp, dNorth, dSouth, null) == SurfaceNormal.EAST);
        check("bodyUp 为零向量时退化为最近面（东面）",
                SupportFaceSelector.select(dWest, dEast, dDown, dUp, dNorth, dSouth, new Vec3d(0, 0, 0))
                        == SurfaceNormal.EAST);
        // 对角 bodyUp（地面→墙面过渡）：上面/东面对齐度相同 → 取距离更近的东面
        check("对角 bodyUp 平手时取更近面（东面）",
                SupportFaceSelector.select(dWest, dEast, dDown, dUp, dNorth, dSouth,
                        new Vec3d(1, 1, 0).normalize()) == SurfaceNormal.EAST);
    }

    /**
     * 回归：连续平地 + 脚底贴近方块竖直边界（采样点距边界 < 下沉量 0.1）——
     * 2026-08-29 日志中该场景被判定 MULTIPLE（24/34 条）。修复后必须 SINGLE(UP)。
     */
    private static void testFlatGroundBoundaryRegression() {
        System.out.println("-- 平地边界回归 --");
        // 连续平地：方块顶面 y=0、竖直边界 x=1.0；脚底中心 x=0.9（采样点跨越边界）
        SurfaceQuery fixedFloor = (p, bodyUp) -> {
            if (p.y > 1.0e-9) {
                return null;
            }
            double dWest = p.x - 0.0;
            double dEast = Math.max(0.0, 1.0 - p.x);
            double dUp = 0.0 - p.y;
            return SupportFaceSelector.select(dWest, dEast, 1.0, dUp, 0.5, 0.5, bodyUp);
        };
        FootSurfaceDetector detector = new FootSurfaceDetector(FootSamplingLayout.rectangle(), fixedFloor);
        FootSurfaceResult r = detector.detect(new Vec3d(0.9, 0.1, 0), UP, FORWARD);
        check("平地贴近方块边界仍 SINGLE", r.isSingle());
        check("平地贴近方块边界法线 UP", r.normal().equals(SurfaceNormal.UP.vector()));
    }

    /** 旧「最近面」规则（修复前 LevelSurfaceQuery 行为），仅用于回归对照。 */
    private static SurfaceNormal nearestFace(double dWest, double dEast, double dDown, double dUp,
                                             double dNorth, double dSouth) {
        double min = Math.min(Math.min(Math.min(dWest, dEast), Math.min(dDown, dUp)),
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

    /**
     * 阶段 5 补丁8：脚踝水平探测布局——4 个探测点在脚踝高度（脚底平面上方 0.25），
     * 水平距离 0.31（略超碰撞箱半宽 0.3），外向方向随身体方向旋转。
     */
    private static void testAnkleProbeLayout() {
        System.out.println("-- 脚踝水平探测布局 --");
        java.util.List<FootSamplingLayout.WallProbe> probes = FootSamplingLayout.ankleProbes();
        check("脚踝探测 4 个方向", probes.size() == 4);

        double maxHeight = 0;
        double maxHorizontal = 0;
        for (FootSamplingLayout.WallProbe probe : probes) {
            Vec3d offset = probe.worldOffset(UP, FORWARD);
            maxHeight = Math.max(maxHeight, offset.y);
            maxHorizontal = Math.max(maxHorizontal,
                    Math.max(Math.abs(offset.x), Math.abs(offset.z)));
            Vec3d outward = probe.outward(UP, FORWARD);
            check("外向量水平（y=0）", Math.abs(outward.y) < 1.0e-9);
            check("外向量为单位长度", Math.abs(outward.length() - 1.0) < 1.0e-9);
        }
        check("探测点高于脚底平面（脚踝高度 0.25）", Math.abs(maxHeight - 0.25) < 1.0e-9);
        check("探测点水平距离 0.31（超出碰撞箱半宽 0.3）", Math.abs(maxHorizontal - 0.31) < 1.0e-9);

        // 身体转向 +X 后，局部 +右 方向映射到世界 +Z
        Vec3d rotatedRight = probes.get(0).outward(UP, new Vec3d(1, 0, 0));
        check("身体转 90° 后探测方向跟随（+Z）", Math.abs(rotatedRight.z - 1.0) < 1.0e-9);
    }

    /**
     * 阶段 5 补丁8：真实墙面探测与「走向墙面」过渡（PRD 2.4 / 3.4-4）。
     * 假世界：地板（y≤0）→ UP；西侧墙（x≥0.5、脚踝高度）→ WEST。
     */
    private static void testWallProbeDetection() {
        System.out.println("-- 脚踝墙面探测与走向墙面 --");
        SurfaceQuery floor = (p, bodyUp) -> p.y <= 0.0 ? SurfaceNormal.UP : null;
        WallQuery westWall = (p, outward, bodyUp) ->
                (p.x >= 0.5 && p.y > 1.0e-9 && p.y <= 2.0) ? SurfaceNormal.WEST : null;
        FootSurfaceDetector detector = new FootSurfaceDetector(FootSamplingLayout.rectangle(),
                floor, westWall, FootSurfaceDetector.DEFAULT_QUERY_OFFSET);

        // 墙在探测距离外（脚底中心 x=0.1，+X 探测点 x=0.41 < 0.5）→ 平地 SINGLE(UP)
        FootSurfaceResult far = detector.detect(new Vec3d(0.1, 0.1, 0), UP, FORWARD);
        check("墙在探测距离外 → SINGLE(UP)", far.isSingle() && far.normal().equals(SurfaceNormal.UP.vector()));

        // 贴墙静止（+X 探测点 x=0.51 命中墙）→ MULTIPLE（不判定站立，PRD 3.2-5）
        Vec3d atWall = new Vec3d(0.2, 0.1, 0);
        FootSurfaceResult still = detector.detect(atWall, UP, FORWARD, null);
        check("贴墙静止 → MULTIPLE", still.isMultiple());

        // 走向墙面（水平移动 +X，撞向法线 WEST 的墙）→ SINGLE(WEST)，允许转向墙面
        FootSurfaceResult walkingIn = detector.detect(atWall, UP, FORWARD, new Vec3d(0.1, 0, 0));
        check("走向墙面 → SINGLE(WEST)", walkingIn.isSingle()
                && walkingIn.normal().equals(SurfaceNormal.WEST.vector()));

        // 背离墙面走动 → 仍 MULTIPLE
        FootSurfaceResult walkingAway = detector.detect(atWall, UP, FORWARD, new Vec3d(-0.1, 0, 0));
        check("背离墙面走动 → MULTIPLE", walkingAway.isMultiple());

        // 移动过快但方向背离（侧向移动）→ MULTIPLE
        FootSurfaceResult sideways = detector.detect(atWall, UP, FORWARD, new Vec3d(0, 0, 0.1));
        check("沿墙侧向移动 → MULTIPLE", sideways.isMultiple());

        // 无支撑面（悬空）→ NONE：不做墙面吸附（PRD 2.3）
        SurfaceQuery air = (p, bodyUp) -> null;
        FootSurfaceDetector airDetector = new FootSurfaceDetector(FootSamplingLayout.rectangle(),
                air, westWall, FootSurfaceDetector.DEFAULT_QUERY_OFFSET);
        check("悬空时不吸附墙面 → NONE",
                airDetector.detect(new Vec3d(0.2, 0.1, 0), UP, FORWARD, new Vec3d(0.1, 0, 0)).isNone());

        // 墙面探测点确实在脚踝高度（高于脚底平面，仍属脚部范围，PRD 3.2-2）
        List<Vec3d> probed = new ArrayList<>();
        WallQuery recording = (p, outward, bodyUp) -> {
            probed.add(p);
            return null;
        };
        FootSurfaceDetector recordingDetector = new FootSurfaceDetector(FootSamplingLayout.rectangle(),
                floor, recording, FootSurfaceDetector.DEFAULT_QUERY_OFFSET);
        recordingDetector.detect(new Vec3d(0, 0.1, 0), UP, FORWARD);
        check("墙面探测点 4 个", probed.size() == 4);
        for (Vec3d p : probed) {
            check("探测点在脚踝高度 y∈[0.2,0.4]", p.y >= 0.2 - 1.0e-6 && p.y <= 0.4 + 1.0e-6);
        }
    }

    /**
     * 阶段 5 补丁9：移动意图跟踪——玩家贴住墙后位移为 0，但窗口内保留
     * 「最近一次真实移动」，使「走向墙面」过渡仍能触发（PRD 2.4）。
     */
    private static void testMovementIntent() {
        System.out.println("-- 移动意图跟踪 --");
        MovementIntentTracker tracker = new MovementIntentTracker();

        check("首次无位移", tracker.update(0, 0.0, 0.0).length() == 0.0);
        check("首次无意图", tracker.intent(0, 10).length() == 0.0);

        Vec3d walked = tracker.update(1, 0.2, 0.0);
        check("第二 tick 记录位移", Math.abs(walked.x - 0.2) < 1.0e-9);
        check("意图 = 当前位移", Math.abs(tracker.intent(1, 10).x - 0.2) < 1.0e-9);

        // 被墙挡住：位置不再变化（位移 0），窗口内意图保留 → 过渡仍可触发
        tracker.update(2, 0.2, 0.0);
        check("贴墙后本 tick 位移为 0", tracker.update(2, 0.2, 0.0).length() == 0.0);
        check("窗口内保留移动意图", Math.abs(tracker.intent(11, 10).x - 0.2) < 1.0e-9);
        check("窗口外意图归零", tracker.intent(12, 10).length() == 0.0);

        // 同一 tick 重复更新返回同一结果（两个 ticker 共用不互相清零）
        check("同一 tick 重复更新结果一致",
                Math.abs(tracker.update(20, 1.0, 0.0).x - tracker.update(20, 1.0, 0.0).x) < 1.0e-9);

        // 传送/切换维度（位移过大）：丢弃意图
        Vec3d teleport = tracker.update(30, 9.0, 0.0);
        check("位移过大视为传送（返回 0）", teleport.length() == 0.0);
        check("传送后意图归零", tracker.intent(30, 10).length() == 0.0);

        tracker.reset();
        check("reset 后无意图", tracker.intent(31, 10).length() == 0.0);
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
