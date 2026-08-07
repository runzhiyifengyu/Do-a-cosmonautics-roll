package dev.cosmonauticsroll.debug;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import dev.cosmonauticsroll.CosmonauticsRoll;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * DEBUG 调试命令：{@code /cosmonauticsroll debug on|off|status}。
 *
 * <p>用于 Debug 验收模式在游戏内运行时开关调试。命令注册到服务端命令分发器；
 * 单人游戏集成服务器与客户端同进程，静态调试开关两端共享。</p>
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
}
