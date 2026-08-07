package dev.cosmonauticsroll.region;

import dev.cosmonauticsroll.api.region.RegionRule;

/**
 * 宇宙维度规则：玩家在 {@code rocketnautics:deep_space} 维度时启用，不依赖 Y 坐标。
 *
 * <p>PRD 2.2 / 2.3。使用维度注册名字符串判断（多人预留原则：两端通用，不引用
 * Rocketnautics 类）。</p>
 */
public final class DeepSpaceDimensionRule implements RegionRule {

    /** Rocketnautics 深空维度注册名（阶段 0 已从源码确认）。 */
    public static final String DEEP_SPACE_ID = "rocketnautics:deep_space";

    @Override
    public boolean isActive(String dimensionId, double centerY) {
        return DEEP_SPACE_ID.equals(dimensionId);
    }
}
