package dev.cosmonauticsroll.region;

import dev.cosmonauticsroll.debug.Debug;
import dev.cosmonauticsroll.region.RegionStateMachine.RegionState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 调试观察挂接：游戏内实时观察区域状态机（阶段 2 游戏内验收用）。
 *
 * <p>服务端每个 tick 对每个在线玩家调用 {@link RegionStateMachine#update}，
 * 并在调试开启时输出日志：状态转换（进入/离开）立即输出，稳定状态每
 * {@value #HEARTBEAT_INTERVAL} tick（5 秒）输出一次心跳。维度切换与死亡重生时
 * 调用 {@link RegionStateMachine#reset()}，用于验收「进入/退出无残留状态」
 * （PRD 3.1-6）。</p>
 *
 * <p>仅用于 Debug 验收模式（{@code /cosmonauticsroll debug on}），不参与正常
 * 游戏逻辑；日志与状态机实例在调试关闭时不产生任何输出与副作用（仅保留每玩家
 * 状态机实例，供后续阶段正式逻辑复用）。</p>
 */
public final class RegionDebugTicker {

    /** 心跳间隔（tick）：100 tick = 5 秒。 */
    private static final int HEARTBEAT_INTERVAL = 100;

    /** 每玩家状态机（多人预留：按玩家 UUID 区分实例）。 */
    private static final Map<UUID, RegionStateMachine> MACHINES = new HashMap<>();

    private static int tickCount;

    private RegionDebugTicker() {
    }

    /** 注册到 NeoForge 游戏事件总线（由主模组构造函数调用）。 */
    public static void register() {
        NeoForge.EVENT_BUS.register(RegionDebugTicker.class);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Debug.isEnabled()) {
            return;
        }
        tickCount++;
        boolean heartbeat = tickCount % HEARTBEAT_INTERVAL == 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            RegionStateMachine machine = MACHINES.computeIfAbsent(player.getUUID(),
                    uuid -> new RegionStateMachine(RegionRules.DEFAULT));
            String dim = player.level().dimension().location().toString();
            double centerY = player.getBoundingBox().getCenter().y;
            RegionState state = machine.update(dim, centerY);
            if (machine.entered()) {
                Debug.log("区域 >> 进入：dim={} y={} player={}（{} -> {}）",
                        dim, formatY(centerY), player.getGameProfile().getName(),
                        RegionState.INACTIVE, RegionState.ACTIVE);
            } else if (machine.left()) {
                Debug.log("区域 << 离开：dim={} y={} player={}（{} -> {}）",
                        dim, formatY(centerY), player.getGameProfile().getName(),
                        RegionState.ACTIVE, RegionState.INACTIVE);
            } else if (heartbeat) {
                Debug.log("区域心跳：state={} dim={} y={} player={}",
                        state, dim, formatY(centerY), player.getGameProfile().getName());
            }
        }
    }

    /** 维度切换：立即丢弃残留状态，重新进入时从 INACTIVE 开始。 */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resetMachine(event.getEntity(), "维度切换");
    }

    /** 死亡重生（含末地返回主世界）：立即丢弃残留状态。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        resetMachine(event.getEntity(), "死亡重生");
    }

    /** 玩家退出：清理状态机实例，防止内存堆积。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MACHINES.remove(event.getEntity().getUUID());
    }

    private static void resetMachine(Player player, String reason) {
        if (!Debug.isEnabled()) {
            return;
        }
        RegionStateMachine machine = MACHINES.get(player.getUUID());
        if (machine != null) {
            machine.reset();
            Debug.log("区域 reset：player={} reason={}", player.getGameProfile().getName(), reason);
        }
    }

    private static String formatY(double y) {
        return String.format("%.1f", y);
    }
}
