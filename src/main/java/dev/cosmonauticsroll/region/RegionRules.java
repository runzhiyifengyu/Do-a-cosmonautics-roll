package dev.cosmonauticsroll.region;

import dev.cosmonauticsroll.api.region.RegionRule;

import java.util.List;

/**
 * 组合区域规则：任一子规则命中即视为适用区域（OR）。
 *
 * <p>第一版默认组合：主世界高空（Y &gt;= 8000）或宇宙维度（deep_space）。
 * 后续其他维度/高度通过向 {@link #composite(List)} 传入新规则扩展，不修改已有代码。</p>
 */
public final class RegionRules {

    /** 第一版默认规则：主世界高空或深空。 */
    public static final RegionRule DEFAULT = composite(List.<RegionRule>of(
            new OverworldAltitudeRule(),
            new DeepSpaceDimensionRule()));

    private RegionRules() {
    }

    /**
     * 组合多个规则：任一命中即启用。
     *
     * @param rules 子规则列表，非空
     * @return 组合规则
     */
    public static RegionRule composite(List<RegionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("RegionRules.composite requires at least one rule");
        }
        return (dimensionId, centerY) -> {
            for (RegionRule rule : rules) {
                if (rule.isActive(dimensionId, centerY)) {
                    return true;
                }
            }
            return false;
        };
    }
}
