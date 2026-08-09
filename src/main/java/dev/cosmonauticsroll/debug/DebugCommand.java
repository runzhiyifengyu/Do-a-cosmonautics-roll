package dev.cosmonauticsroll.debug;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * DEBUG 调试命令：{@code /cosmonauticsroll debug ...}。
 *
 * <p>用于 Debug 验收模式在游戏内运行时开关调试。命令注册到服务端命令分发器；
 * 单人游戏集成服务器与客户端同进程，静态调试开关两端共享。</p>
 *
 * <p>子命令：</p>
 * <ul>
 *   <li>{@code debug on|off|status}：全局开关与状态；</li>
 *   <li>{@code debug region <高度>}：临时覆盖主世界高度阈值（仅调试开启时生效，
 *       用于低空放置方块验收楼梯/表面逻辑；高空 Y=8000 超出建筑高度上限无法放方块）；
 *       {@code debug region default} 恢复默认阈值；{@code debug region} 查看状态；</li>
 *   <li>{@code debug log <类别> on|off|status}：按类别自由开关日志输出（region / foot /
 *       rotation / stair），如验收楼梯时关掉刷屏的 rotation、foot 只看 stair；
 *       {@code debug log status} 列出全部类别状态。</li>
 * </ul>
 *
 * <p>第一版仅单人：命令权限设为 0 级，任何单人玩家（含未开作弊的世界）都可直接使用，
 * 便于 Debug 验收；多人支持时再收紧权限。</p>
 */
public final class DebugCommand {

    private DebugCommand() {
    }

    /** 注册到 NeoForge 游戏事件总线（由主模组构造函数调用）。 */
    public static void register() {
        NeoForge.EVENT_BUS.register(DebugCommand.class);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("cosmonauticsroll")
                        .requires(source -> source.hasPermission(0))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("debug")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on")
                                        .executes(ctx -> setDebug(ctx.getSource(), true)))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
                                        .executes(ctx -> setDebug(ctx.getSource(), false)))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("region")
                                        .then(Commands.argument("height",
                                                        DoubleArgumentType.doubleArg(0.0))
                                                .executes(ctx -> setRegionThreshold(
                                                        ctx.getSource(),
                                                        DoubleArgumentType.getDouble(ctx, "height"))))
                                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("default")
                                                .executes(ctx -> resetRegionThreshold(ctx.getSource())))
                                        .executes(ctx -> showRegionThreshold(ctx.getSource())))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("log")
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on")
                                                        .executes(ctx -> setCategory(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "category"),
                                                                true)))
                                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
                                                        .executes(ctx -> setCategory(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "category"),
                                                                false)))
                                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("status")
                                                        .executes(ctx -> showCategory(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "category")))))
                                        .executes(ctx -> showAllCategories(ctx.getSource())))
                                .executes(ctx -> showStatus(ctx.getSource()))));
    }

    private static int setDebug(CommandSourceStack source, boolean value) {
        Debug.setEnabled(value);
        source.sendSuccess(
                () -> Component.literal("[CosmonauticsRoll] DEBUG 调试已" + (value ? "开启" : "关闭")),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("[CosmonauticsRoll] DEBUG 调试当前" + (Debug.isEnabled() ? "开启" : "关闭")),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** 临时覆盖主世界高度阈值（仅 Debug 开启时；验收用，低空放方块）。 */
    private static int setRegionThreshold(CommandSourceStack source, double height) {
        if (!Debug.isEnabled()) {
            source.sendSuccess(
                    () -> Component.literal("[CosmonauticsRoll] 请先开启 DEBUG（/cosmonauticsroll debug on）再设置区域覆盖"),
                    false);
            return Command.SINGLE_SUCCESS;
        }
        RegionDebugConfig.setAltitudeThresholdOverride(height);        source.sendSuccess(
                () -> Component.literal("[CosmonauticsRoll] DEBUG 主世界高度阈值已临时覆盖为 " + format(height)
                        + "（验收用；/cosmonauticsroll debug region default 恢复 8000）"),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** 恢复默认主世界高度阈值 8000。 */
    private static int resetRegionThreshold(CommandSourceStack source) {
        RegionDebugConfig.setAltitudeThresholdOverride(null);
        source.sendSuccess(
                () -> Component.literal("[CosmonauticsRoll] DEBUG 主世界高度阈值已恢复默认 8000"),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** 查看当前主世界高度阈值覆盖状态。 */
    private static int showRegionThreshold(CommandSourceStack source) {
        Double v = RegionDebugConfig.altitudeThresholdOverride();
        source.sendSuccess(
                () -> Component.literal("[CosmonauticsRoll] DEBUG 主世界高度阈值当前"
                        + (v == null ? "默认 8000" : "覆盖为 " + format(v))),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** 设置单个日志类别开关。 */
    private static int setCategory(CommandSourceStack source, String category, boolean enabled) {
        boolean known = Debug.setCategoryEnabled(category, enabled);
        if (!known) {
            source.sendSuccess(
                    () -> Component.literal("[CosmonauticsRoll] 未知日志类别「" + category + "」，可用："
                            + String.join(" / ", Debug.allCategories())),
                    false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(
                () -> Component.literal("[CosmonauticsRoll] 日志类别「" + category + "」已"
                        + (enabled ? "开启" : "关闭")),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** 查看单个类别状态。 */
    private static int showCategory(CommandSourceStack source, String category) {
        source.sendSuccess(
                () -> Component.literal("[CosmonauticsRoll] 日志类别「" + category + "」当前"
                        + (Debug.isCategoryEnabled(category) ? "开启" : "关闭")),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** 查看全部类别状态。 */
    private static int showAllCategories(CommandSourceStack source) {
        List<String> lines = new ArrayList<>();
        for (String c : Debug.allCategories()) {
            lines.add(c + "=" + (Debug.isCategoryEnabled(c) ? "开" : "关"));
        }
        source.sendSuccess(
                () -> Component.literal("[CosmonauticsRoll] 日志类别状态：" + String.join("  ", lines)),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static String format(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
