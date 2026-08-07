package dev.cosmonauticsroll;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * 纯客户端入口。不会在专用服务器上加载。
 *
 * <p>阶段 1：仅骨架。第一人称镜头、第三人称模型与翻滚渲染等纯客户端逻辑
 * 在后续阶段在此注册。</p>
 */
@Mod(value = CosmonauticsRoll.MODID, dist = Dist.CLIENT)
public class CosmonauticsRollClient {

    public CosmonauticsRollClient() {
        // 阶段 1：仅骨架。
    }
}
