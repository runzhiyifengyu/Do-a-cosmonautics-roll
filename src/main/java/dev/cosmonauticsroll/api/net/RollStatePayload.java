package dev.cosmonauticsroll.api.net;

import dev.cosmonauticsroll.CosmonauticsRoll;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 玩家旋转状态包（多人预留，第一版不注册、不发送）。
 *
 * <p>按开发规则第 4 节预留网络协议结构：使用 Minecraft 1.20.5+ 的
 * {@link CustomPacketPayload}（NeoForge 网络框架基于该接口）定义玩家旋转状态
 * （pitch/yaw/roll）。第一版只保留结构与编解码器，不注册到
 * {@code RegisterPayloadHandlersEvent}、不发送；多人支持时按 Do a Barrel Roll
 * 的模式（服务端权威 + 客户端复算）注册并同步。</p>
 *
 * @param pitch 俯仰角（度）
 * @param yaw   偏航角（度）
 * @param roll  翻滚角（度）
 */
public record RollStatePayload(float pitch, float yaw, float roll) implements CustomPacketPayload {

    /** 包唯一标识。 */
    public static final CustomPacketPayload.Type<RollStatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CosmonauticsRoll.MODID, "roll_state"));

    /** 编解码器：三个浮点角度。 */
    public static final StreamCodec<ByteBuf, RollStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RollStatePayload::pitch,
            ByteBufCodecs.FLOAT, RollStatePayload::yaw,
            ByteBufCodecs.FLOAT, RollStatePayload::roll,
            RollStatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
