package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.MovementIntentTracker;
import dev.cosmonauticsroll.api.detect.Vec3d;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家水平移动意图跟踪的游戏内适配（阶段 5 补丁9，PRD 2.4「走向墙面」）。
 *
 * <p>为什么不直接用 {@code Entity.xo}/{@code zo}：服务端玩家的 X/Z 只在网络包
 * 处理阶段更新，实体 tick 内的 {@code x - xo} 基本为 0，取不到客户端行走位移；
 * 而且玩家贴住墙后位移本来就是 0。因此这里按玩家 UUID 维护
 * {@link MovementIntentTracker}，用「每 tick 自行求差 + 窗口内保留最近真实移动」
 * 的方式给出移动意图。</p>
 */
public final class PlayerMoveTracker {

    /** 移动意图窗口（tick）：约 0.5 秒。玩家被墙挡住后位移为 0，
     *  仍能在窗口内保留「走向墙面」意图（PRD 2.4 地面→墙面过渡）。 */
    public static final int INTENT_WINDOW_TICKS = 10;

    private static final Map<UUID, MovementIntentTracker> TRACKERS = new HashMap<>();

    private PlayerMoveTracker() {
    }

    /** 本 tick 的水平移动意图（格/tick；可能来自最近窗口内的真实移动）。 */
    public static Vec3d intentMovement(ServerPlayer player) {
        MovementIntentTracker tracker = TRACKERS.computeIfAbsent(player.getUUID(),
                uuid -> new MovementIntentTracker());
        long tick = player.level().getGameTime();
        tracker.update(tick, player.getX(), player.getZ());
        return tracker.intent(tick, INTENT_WINDOW_TICKS);
    }

    /** 清理玩家状态（登出/重置），防止内存堆积。 */
    public static void reset(UUID uuid) {
        TRACKERS.remove(uuid);
    }
}
