package dev.cosmonauticsroll.region;

import dev.cosmonauticsroll.api.region.RegionRule;
import dev.cosmonauticsroll.debug.RegionDebugConfig;

/**
 * 主世界高空规则：玩家在主世界且碰撞箱中心 Y &gt;= 8000 时启用。
 *
 * <p>PRD 2.1：主世界使用碰撞箱中心位置，Y &gt;= 8000 进入适用区域，Y &lt; 8000 离开。</p>
 *
 * <p>Debug 验收覆盖：当 {@link RegionDebugConfig#altitudeThresholdOverride()} 非 null
 * （仅调试开启时经命令设置）时，用覆盖值替代 8000——用于在低空（建筑高度上限内）
 * 放置方块验收楼梯/表面逻辑（D 模式验收手段，不参与正常游戏）。</p>
 */
public final class OverworldAltitudeRule implements RegionRule {

    /** 主世界维度注册名。 */
    public static final String OVERWORLD_ID = "minecraft:overworld";

    /** 适用高度阈值。 */
    public static final double ALTITUDE_THRESHOLD = 8000.0;

    @Override
    public boolean isActive(String dimensionId, double centerY) {
        if (!OVERWORLD_ID.equals(dimensionId)) {
            return false;
        }
        double threshold = ALTITUDE_THRESHOLD;
        Double override = RegionDebugConfig.altitudeThresholdOverride();
        if (override != null) {
            threshold = override;
        }
        return centerY >= threshold;
    }
}
