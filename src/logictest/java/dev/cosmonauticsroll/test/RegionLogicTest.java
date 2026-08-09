package dev.cosmonauticsroll.test;

import dev.cosmonauticsroll.debug.RegionDebugConfig;
import dev.cosmonauticsroll.region.DeepSpaceDimensionRule;
import dev.cosmonauticsroll.region.OverworldAltitudeRule;
import dev.cosmonauticsroll.region.RegionRules;
import dev.cosmonauticsroll.region.RegionStateMachine;
import dev.cosmonauticsroll.region.RegionStateMachine.RegionState;
import dev.cosmonauticsroll.region.SurfaceContactTimer;
import dev.cosmonauticsroll.region.SurfaceContactTimer.ContactState;

/**
 * 阶段 2 适用区域与状态机 —— 设备 VM 逻辑单元测试。
 *
 * <p>运行方式：本类提供 main()，由 CodeAssist 的设备 VM 直接执行（纯逻辑，
 * 无 Minecraft 依赖）。覆盖 PRD 3.1 验收与阶段 2 检查项：边界值 8000、
 * deep_space、维度切换、跨边界状态清理、0.3 秒计时。</p>
 *
 * <p>注意：本测试类包含 main 入口，仅用于开发期逻辑验证；不参与模组游戏逻辑。</p>
 */
public final class RegionLogicTest {

    private static int passed = 0;
    private static int failed = 0;

    private RegionLogicTest() {
    }

    public static void main(String[] args) {
        System.out.println("=== 阶段2 适用区域与状态机 逻辑测试 ===");

        testOverworldRule();
        testDeepSpaceRule();
        testCompositeRule();
        testStateMachine();
        testBoundaryCrossing();
        testDimensionSwitch();
        testSurfaceContactTimer();
        testRegionDebugThresholdOverride();

        System.out.println("----------------------------------------");
        System.out.println("通过: " + passed + "  失败: " + failed);
        if (failed > 0) {
            throw new AssertionError("存在失败用例: " + failed);
        }
        System.out.println("全部通过");
    }

    private static void testOverworldRule() {
        System.out.println("-- 主世界高空规则 (Y >= 8000) --");
        OverworldAltitudeRule rule = new OverworldAltitudeRule();
        // 边界值 8000
        check("Y=8000 启用", rule.isActive("minecraft:overworld", 8000.0));
        check("Y=8000.001 启用", rule.isActive("minecraft:overworld", 8000.001));
        check("Y=7999.999 不启用", !rule.isActive("minecraft:overworld", 7999.999));
        check("Y=0 不启用", !rule.isActive("minecraft:overworld", 0.0));
        check("Y=高负值不启用", !rule.isActive("minecraft:overworld", -100.0));
        // 维度约束
        check("下界高空不启用", !rule.isActive("minecraft:the_nether", 8000.0));
        check("末地高空不启用", !rule.isActive("minecraft:the_end", 8000.0));
    }

    private static void testDeepSpaceRule() {
        System.out.println("-- 深空维度规则 (rocketnautics:deep_space) --");
        DeepSpaceDimensionRule rule = new DeepSpaceDimensionRule();
        check("deep_space Y=0 启用", rule.isActive("rocketnautics:deep_space", 0.0));
        check("deep_space Y=负 启用", rule.isActive("rocketnautics:deep_space", -64.0));
        check("deep_space Y=高 启用", rule.isActive("rocketnautics:deep_space", 100000.0));
        check("moon 不启用", !rule.isActive("rocketnautics:moon", 100.0));
        check("主世界不启用", !rule.isActive("minecraft:overworld", 8000.0));
    }

    private static void testCompositeRule() {
        System.out.println("-- 默认组合规则 --");
        check("组合: 主世界高空启用",
                RegionRules.DEFAULT.isActive("minecraft:overworld", 9000.0));
        check("组合: deep_space 启用",
                RegionRules.DEFAULT.isActive("rocketnautics:deep_space", 123.0));
        check("组合: 主世界低空不启用",
                !RegionRules.DEFAULT.isActive("minecraft:overworld", 7999.0));
        check("组合: 其他维度不启用",
                !RegionRules.DEFAULT.isActive("minecraft:the_end", 9000.0));
    }

    private static void testStateMachine() {
        System.out.println("-- 状态机 进入/保持/离开 --");
        RegionStateMachine sm = new RegionStateMachine(RegionRules.DEFAULT);

        // 初始为区域外
        check("初始 INACTIVE", sm.state() == RegionState.INACTIVE && !sm.isActive());

        // 进入：主世界 Y=8000
        check("进入后 ACTIVE", sm.update("minecraft:overworld", 8000.0) == RegionState.ACTIVE);
        check("进入事件", sm.entered() && !sm.left());

        // 保持：仍在区域内
        check("保持 ACTIVE", sm.update("minecraft:overworld", 9000.0) == RegionState.ACTIVE);
        check("保持无转换事件", !sm.entered() && !sm.left());

        // 深空保持（维度切换但仍在适用区域）
        check("deep_space 保持 ACTIVE", sm.update("rocketnautics:deep_space", 0.0) == RegionState.ACTIVE);
        check("维度切换保持无进入事件", !sm.entered());

        // 离开：Y < 8000
        check("离开后 INACTIVE", sm.update("minecraft:overworld", 7999.0) == RegionState.INACTIVE);
        check("离开事件", sm.left() && !sm.entered());

        // 保持离开
        check("区域外保持 INACTIVE", sm.update("minecraft:overworld", 100.0) == RegionState.INACTIVE);
        check("区域外无转换事件", !sm.entered() && !sm.left());

        // 再次进入
        check("再次进入 ACTIVE", sm.update("minecraft:overworld", 8000.0) == RegionState.ACTIVE);
        check("再次进入事件", sm.entered());

        // reset 清理（死亡/重生/传送）
        sm.reset();
        check("reset 后 INACTIVE", sm.state() == RegionState.INACTIVE);
        check("reset 清空转换标记", !sm.entered() && !sm.left());
    }

    private static void testBoundaryCrossing() {
        System.out.println("-- 跨 Y=8000 边界状态清理 --");
        RegionStateMachine sm = new RegionStateMachine(RegionRules.DEFAULT);

        sm.update("minecraft:overworld", 9000.0);
        check("边界前 ACTIVE", sm.isActive());

        // 跨边界到 8000 以下：离开事件触发，外部据此清理旋转状态
        sm.update("minecraft:overworld", 7999.9);
        check("跨边界 left=true", sm.left());
        check("跨边界后 INACTIVE", !sm.isActive());

        // 再次跨回：进入事件
        sm.update("minecraft:overworld", 8000.0);
        check("跨回 entered=true", sm.entered());
    }

    private static void testDimensionSwitch() {
        System.out.println("-- 维度切换 --");
        RegionStateMachine sm = new RegionStateMachine(RegionRules.DEFAULT);

        // 高空 → 深空：仍适用，应无转换事件（保持）
        sm.update("minecraft:overworld", 9000.0);
        sm.update("rocketnautics:deep_space", 0.0);
        check("主世界→深空保持 ACTIVE", sm.isActive());
        check("主世界→深空无转换事件", !sm.entered() && !sm.left());

        // 高空 → 末地：离开事件
        sm.update("minecraft:the_end", 100.0);
        check("→末地 left=true", sm.left());
        check("→末地 INACTIVE", !sm.isActive());

        // 末地 → 深空：进入事件
        sm.update("rocketnautics:deep_space", -10.0);
        check("末地→深空 entered=true", sm.entered());
    }

    private static void testSurfaceContactTimer() {
        System.out.println("-- 离开方块 0.3 秒计时器 --");
        SurfaceContactTimer timer = new SurfaceContactTimer();

        check("接触 -> CONTACTED", timer.update(true) == ContactState.CONTACTED);
        check("持续接触保持 CONTACTED", timer.update(true) == ContactState.CONTACTED);
        check("ticksSinceContact=0", timer.ticksSinceContact() == 0);

        // 离开 1..6 tick 仍为 RECENTLY_LEFT（0.3 秒 = 6 tick）
        for (int i = 1; i <= 6; i++) {
            ContactState s = timer.update(false);
            if (s != ContactState.RECENTLY_LEFT) {
                check("离开第" + i + " tick 应为 RECENTLY_LEFT", false);
            }
        }
        check("离开 6 tick 仍 RECENTLY_LEFT",
                timer.ticksSinceContact() == 6);

        // 第 7 tick 进入 FREE（超过 0.3 秒）
        check("离开第 7 tick -> FREE", timer.update(false) == ContactState.FREE);
        check("继续离开保持 FREE", timer.update(false) == ContactState.FREE);

        // 重新接触重置
        check("重新接触 -> CONTACTED", timer.update(true) == ContactState.CONTACTED);
        check("重新接触后 ticks=0", timer.ticksSinceContact() == 0);

        // reset
        timer.reset();
        check("reset 后 ticks=0", timer.ticksSinceContact() == 0);
        check("reset 后再次离开第1tick RECENTLY_LEFT",
                timer.update(false) == ContactState.RECENTLY_LEFT);
    }

    /** Debug 验收覆盖（阶段 5 验收手段）：主世界高度阈值可临时覆盖到低空。 */
    private static void testRegionDebugThresholdOverride() {
        System.out.println("-- Debug 区域高度阈值覆盖 --");
        OverworldAltitudeRule rule = new OverworldAltitudeRule();
        try {
            // 默认（无覆盖）：低空不启用
            check("默认 y=100 不启用", !rule.isActive("minecraft:overworld", 100.0));

            // 覆盖到 0：低空启用、边界值 0 启用、负值不启用
            RegionDebugConfig.setAltitudeThresholdOverride(0.0);
            check("覆盖0 y=100 启用", rule.isActive("minecraft:overworld", 100.0));
            check("覆盖0 y=0 启用", rule.isActive("minecraft:overworld", 0.0));
            check("覆盖0 y=-1 不启用", !rule.isActive("minecraft:overworld", -1.0));

            // 覆盖不影响其他维度约束
            check("覆盖0 下界仍不启用", !rule.isActive("minecraft:the_nether", 100.0));

            // 恢复默认：低空恢复不启用
            RegionDebugConfig.setAltitudeThresholdOverride(null);
            check("恢复默认 y=100 不启用", !rule.isActive("minecraft:overworld", 100.0));
            check("恢复默认 y=8000 启用", rule.isActive("minecraft:overworld", 8000.0));
        } finally {
            // 全局静态状态清理：无论断言成败都恢复，避免影响后续测试/游戏
            RegionDebugConfig.setAltitudeThresholdOverride(null);
        }
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
