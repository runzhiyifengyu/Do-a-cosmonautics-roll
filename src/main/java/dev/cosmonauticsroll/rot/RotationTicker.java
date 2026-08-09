package dev.cosmonauticsroll.rot;

import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.debug.Debug;
import dev.cosmonauticsroll.detect.FootSurfaceResolver;
import dev.cosmonauticsroll.region.RegionStateMachine;
import dev.cosmonauticsroll.region.RegionRules;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 阶段 4 正式旋转逻辑（PRD 3.3 平滑旋转）：
 * 在适用区域内驱动「脚部检测 → 平滑旋转」，离开区域平滑恢复竖直。
 *
 * <p>每玩家状态（多人预留）：{@link RegionStateMachine}（适用区域判断）+
 * {@link SmoothStandingRotation}（平滑站立方向）。每 tick 流程：</p>
 * <ol>
 *   <li>区域状态机更新（进入/离开/保持，复用阶段 2 逻辑）；</li>
 *   <li>区域内：脚部组合检测（Sable 子世界优先 + 静态方块兜底，
 *       {@link FootSurfaceResolver}）→ 平滑器按最大角速度向目标过渡；</li>
 *   <li>离开区域：{@link SmoothStandingRotation#leaveRegion()} 平滑恢复竖直方向
 *       （PRD 3.3-7），恢复完成前每 tick 继续输出当前方向供调试观察。</li>
 * </ol>
 *
 * <p>Do a Barrel Roll 兼容（PRD 3.3-5/3.3-6）：本阶段只计算「身体上方向」
 * 平滑结果，不直接修改玩家 yRot/pitch/roll；DABR 翻滚叠加顺序在阶段 6
 * 接入时实现（约定：本模组表面方向作为基础旋转，DABR 翻滚在其上叠加，
 * 不覆盖 DABR 的俯仰/偏航/翻滚，见 DEVELOPMENT.md 阶段 6 规划）。</p>
 *
 * <p>调试日志（PRD 3.3 验收，Debug 验收模式）：旋转过程每 tick 输出
 * {@code 旋转：current=... target=...}；防抖事件（目标被忽略）单独输出
 * {@code 防抖：...}；离开区域恢复输出 {@code 恢复竖直：...}。</p>
 */
public final class RotationTicker {

    /** 旋转过程日志间隔（tick）：10 tick = 0.5 秒，避免刷屏。 */
    private static final int ROTATION_LOG_INTERVAL = 10;

    /** 每玩家旋转状态（多人预留：按玩家 UUID 区分实例）。 */
    private static final Map<UUID, PlayerRotationState> PLAYERS = new HashMap<>();

    private static int tickCount;

    private RotationTicker() {
    }

    /** 注册到 NeoForge 游戏事件总线（由主模组构造函数调用）。 */
    public static void register() {
        NeoForge.EVENT_BUS.register(RotationTicker.class);
    }

    private static final class PlayerRotationState {
        final RegionStateMachine region = new RegionStateMachine(RegionRules.DEFAULT);
        final SmoothStandingRotation rotation = new SmoothStandingRotation();
        boolean active;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCount++;
        boolean logRotation = Debug.isEnabled() && tickCount % ROTATION_LOG_INTERVAL == 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerRotationState state = PLAYERS.computeIfAbsent(player.getUUID(), uuid -> new PlayerRotationState());
            updatePlayer(player, state, logRotation);
        }
    }

    private static void updatePlayer(ServerPlayer player, PlayerRotationState state, boolean logRotation) {
        String dim = player.level().dimension().location().toString();
        double centerY = player.getBoundingBox().getCenter().y;
        boolean active = state.region.update(dim, centerY) == RegionStateMachine.RegionState.ACTIVE;

        if (active && !state.active) {
            // 进入适用区域：开始平滑站立旋转
            state.active = true;
            Debug.log("旋转 >> 进入：player={}", player.getGameProfile().getName());
        } else if (!active && state.active) {
            // 离开适用区域：平滑恢复竖直方向（PRD 3.3-7）
            state.rotation.leaveRegion();
            state.active = false;
            Debug.log("旋转 << 离开：player={} 开始平滑恢复竖直", player.getGameProfile().getName());
        }

        if (state.active) {
            Vec3d bodyUp = state.rotation.current();
            FootSurfaceResolver.Resolved resolved = new FootSurfaceResolver(player.level())
                    .resolve(player, bodyUp);
            boolean accepted = state.rotation.setTarget(resolved.result);
            if (!accepted && resolved.result != null && resolved.result.isSingle()) {
                // 单一方向表面但被防抖忽略（死区/切换锁）：输出防抖日志（PRD 3.3-3 D 模式验收）
                Debug.log("防抖：目标被忽略 target={} current={} player={}",
                        state.rotation.target(), state.rotation.current(),
                        player.getGameProfile().getName());
            }
            Vec3d current = state.rotation.update(); // 每 tick 推进一次平滑过渡
            if (logRotation) {
                Debug.log("旋转：current={} target={} result={} source={} player={}",
                        current, state.rotation.target(),
                        resolved.result, resolved.source, player.getGameProfile().getName());
            }
        } else {
            // 离开区域：继续平滑恢复，直到回到竖直（目标已设为 +Y）
            state.rotation.update();
            if (logRotation) {
                Debug.log("旋转（恢复竖直）：current={} target={} player={}",
                        state.rotation.current(), state.rotation.target(),
                        player.getGameProfile().getName());
            }
        }
    }

    /** 维度切换：立即丢弃残留状态（复用阶段 2 的清理时机）。 */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resetPlayer(event.getEntity().getUUID(), "维度切换");
    }

    /** 死亡重生（含末地返回主世界）：立即丢弃残留状态。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        resetPlayer(event.getEntity().getUUID(), "死亡重生");
    }

    /** 玩家退出：清理状态实例，防止内存堆积。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYERS.remove(event.getEntity().getUUID());
    }

    private static void resetPlayer(UUID uuid, String reason) {
        PlayerRotationState state = PLAYERS.get(uuid);
        if (state != null) {
            state.rotation.reset();
            state.active = false;
            state.region.reset();
            Debug.log("旋转 reset：player={} reason={}", uuid, reason);
        }
    }
}
