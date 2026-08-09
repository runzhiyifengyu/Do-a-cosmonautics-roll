package dev.cosmonauticsroll.debug;

/**
 * 调试用区域规则覆盖（纯逻辑、无 Minecraft 依赖，可在设备 VM 上单元测试）。
 *
 * <p>Debug 验收模式专用（D 模式）：主世界高度阈值默认 {@code 8000}（PRD 2.1），
 * 但建筑高度上限仅到 Y=320，高空无法放置普通方块——楼梯/表面逻辑需要在
 * 正常高度搭方块验收。本类允许临时把主世界高度阈值覆盖到低空（如 0），
 * 验收完用 {@code default} 恢复。</p>
 *
 * <p>仅静态配置，不参与正常游戏逻辑：覆盖值只在调试开启时可设置
 * （{@code /cosmonauticsroll debug region <高度>}），且默认/恢复后即失效；
 * 多人预留：纯静态字段 + 简单 setter，不依赖任何客户端类。</p>
 */
public final class RegionDebugConfig {

    /** 主世界高度阈值覆盖（null = 使用默认 8000）。 */
    private static Double altitudeThresholdOverride;

    private RegionDebugConfig() {
    }

    /** 当前主世界高度阈值覆盖值（null = 未覆盖，用默认 8000）。 */
    public static Double altitudeThresholdOverride() {
        return altitudeThresholdOverride;
    }

    /**
     * 设置主世界高度阈值覆盖（仅 Debug 验收用）。
     *
     * @param value 新阈值；{@code null} = 恢复默认 8000
     */
    public static void setAltitudeThresholdOverride(Double value) {
        altitudeThresholdOverride = value;
    }
}
