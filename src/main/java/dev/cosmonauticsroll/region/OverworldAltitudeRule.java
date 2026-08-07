package dev.cosmonauticsroll.region;

import dev.cosmonauticsroll.api.region.RegionRule;

/**
 * 主世界高空规则：玩家在主世界且碰撞箱中心 Y &gt;= 8000 时启用。
 *
 * <p>PRD 2.1：主世界使用碰撞箱中心位置，Y &gt;= 8000 进入适用区域，Y &lt; 8000 离开。</p>
 */
public final class OverworldAltitudeRule implements RegionRule {

    /** 主世界维度注册名。 */
    public static final String OVERWORLD_ID = "minecraft:overworld";

    /** 适用高度阈值。 */
    public static final double ALTITUDE_THRESHOLD = 8000.0;

    @Override
    public boolean isActive(String dimensionId, double centerY) {
        return OVERWORLD_ID.equals(dimensionId) && centerY >= ALTITUDE_THRESHOLD;
    }
}
