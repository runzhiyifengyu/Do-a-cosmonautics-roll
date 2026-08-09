package dev.cosmonauticsroll.test;

/**
 * 全部纯逻辑单元测试的统一入口（由 GitHub Actions 的 runLogicTests 任务执行）。
 *
 * <p>按阶段依次运行各测试类；任一失败会抛出 AssertionError 并中断，
 * 使 Gradle JavaExec 任务失败（CI 可发现）。</p>
 */
public final class LogicTestSuite {

    private LogicTestSuite() {
    }

    public static void main(String[] args) {
        RegionLogicTest.main(args);
        FootSurfaceLogicTest.main(args);
        RotationLogicTest.main(args);
        StairLogicTest.main(args);
        System.out.println("=== 全部逻辑测试通过 ===");
    }
}
