package dev.cosmonauticsroll.debug;

import dev.cosmonauticsroll.CosmonauticsRoll;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * DEBUG 调试功能：总开关、调试日志与日志类别过滤。
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
 *
 * <p>日志类别过滤（阶段 5 新增，用户可自由选择输出哪些日志）：</p>
 * <ul>
 *   <li>{@link #log(String, String, Object...)} 带类别输出，受「全局开关 + 类别开关」双重控制；
 *       类别默认全部开启，可用命令 {@code /cosmonauticsroll debug log <类别> on|off|status}
 *       单独关闭/查看（如验收楼梯时关掉刷屏的 {@code rotation}/{@code foot}，只看 {@code stair}）；</li>
 *   <li>{@link #log(String, Object...)} 不带类别输出，只受全局开关控制（用于必须始终可见的
 *       一次性诊断警告，如 Sable 不可用）。</li>
 * </ul>
 */
public final class Debug {

    /** 区域状态日志类别（进入/离开/心跳/reset，RegionDebugTicker）。 */
    public static final String CATEGORY_REGION = "region";

    /** 脚部检测日志类别（脚部检测结果与采样点详情，RegionDebugTicker / FootSurfaceResolver）。 */
    public static final String CATEGORY_FOOT = "foot";

    /** 旋转过程日志类别（进入/离开/current/target/防抖/恢复竖直，RotationTicker）。 */
    public static final String CATEGORY_ROTATION = "rotation";

    /** 楼梯日志类别（progress 变化事件，RotationTicker）。 */
    public static final String CATEGORY_STAIR = "stair";

    /** 全部已知类别（命令 status 展示用）。 */
    private static final Set<String> ALL_CATEGORIES = new LinkedHashSet<>(Set.of(
            CATEGORY_REGION, CATEGORY_FOOT, CATEGORY_ROTATION, CATEGORY_STAIR));

    /** 调试开关。默认读取系统属性 {@code cosmonauticsroll.debug}。 */
    private static boolean enabled = Boolean.getBoolean("cosmonauticsroll.debug");

    /** 被用户关闭的日志类别（默认全部开启）。 */
    private static final Set<String> DISABLED_CATEGORIES = new HashSet<>();

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

    // ---------- 日志类别过滤 ----------

    /**
     * 类别是否允许输出（默认全部开启；被命令关闭的类别返回 false）。
     *
     * @param category 日志类别（{@link #CATEGORY_REGION} 等）
     */
    public static boolean isCategoryEnabled(String category) {
        return !DISABLED_CATEGORIES.contains(category);
    }

    /**
     * 设置类别开关。
     *
     * @param category 日志类别
     * @param enabled  是否开启
     * @return 是否已知类别（未知类别返回 false 不修改）
     */
    public static boolean setCategoryEnabled(String category, boolean enabled) {
        if (category == null || !ALL_CATEGORIES.contains(category)) {
            return false;
        }
        if (enabled) {
            DISABLED_CATEGORIES.remove(category);
        } else {
            DISABLED_CATEGORIES.add(category);
        }
        return true;
    }

    /** 当前被关闭的类别集合（只读）。 */
    public static Set<String> disabledCategories() {
        return Collections.unmodifiableSet(new HashSet<>(DISABLED_CATEGORIES));
    }

    /** 全部已知类别（只读，命令 status 展示用）。 */
    public static Set<String> allCategories() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ALL_CATEGORIES));
    }

    // ---------- 日志 ----------

    /**
     * 调试日志：仅在调试开启且类别允许时输出，统一带 {@code [Debug]} 前缀便于在日志中过滤。
     * 用于必须始终可见的诊断（不随类别关闭），如 Sable 不可用等一次性警告。
     *
     * @param message 消息模板（slf4j 占位符风格）
     * @param args    模板参数
     */
    public static void log(String message, Object... args) {
        if (enabled) {
            CosmonauticsRoll.LOGGER.info("[Debug] " + message, args);
        }
    }

    /**
     * 类别化调试日志：仅在调试开启且该类别允许输出时输出。
     * 用于可按类别自由开关的常规调试日志（区域/脚部/旋转/楼梯）。
     *
     * @param category 日志类别（{@link #CATEGORY_REGION} 等）
     * @param message  消息模板（slf4j 占位符风格）
     * @param args     模板参数
     */
    public static void log(String category, String message, Object... args) {
        if (enabled && isCategoryEnabled(category)) {
            CosmonauticsRoll.LOGGER.info("[Debug] " + message, args);
        }
    }
}
