package dev.cosmonauticsroll.debug;

import dev.cosmonauticsroll.CosmonauticsRoll;

/**
 * DEBUG 调试功能：总开关与调试日志。
 *
 * <p>用于支撑 PRD 的 Debug 验收模式（见 DEVELOPMENT.md 第 10 节）：在正常游戏流程之外，
 * 借助日志与命令构造边界场景，验证边界值、状态清理、异常路径与无崩溃。
 * 仅用于开发与验收，不参与正常游戏逻辑。</p>
 *
 * <p>开启方式（三者任一）：</p>
 * <ul>
 *   <li>开发运行环境：build.gradle 的 runs 配置默认注入系统属性
 *       {@code -Dcosmonauticsroll.debug=true}（生产发布不受影响，默认关闭）；</li>
 *   <li>游戏内命令：{@code /cosmonauticsroll debug on|off|status}；</li>
 *   <li>启动参数：{@code -Dcosmonauticsroll.debug=true}。</li>
 * </ul>
 */
public final class Debug {

    /** 调试开关。默认读取系统属性 {@code cosmonauticsroll.debug}。 */
    private static boolean enabled = Boolean.getBoolean("cosmonauticsroll.debug");

    private Debug() {
    }

    /** 调试是否开启。 */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置调试开关。
     *
     * @param value 是否开启
     */
    public static void setEnabled(boolean value) {
        enabled = value;
        // 不经过 log()，保证"已开启/已关闭"提示在两种状态下都能输出。
        CosmonauticsRoll.LOGGER.info("[Debug] DEBUG 调试已{}", value ? "开启" : "关闭");
    }

    /**
     * 调试日志：仅在调试开启时输出，统一带 {@code [Debug]} 前缀便于在日志中过滤。
     *
     * @param message 消息模板（slf4j 占位符风格）
     * @param args    模板参数
     */
    public static void log(String message, Object... args) {
        if (enabled) {
            CosmonauticsRoll.LOGGER.info("[Debug] " + message, args);
        }
    }
}
