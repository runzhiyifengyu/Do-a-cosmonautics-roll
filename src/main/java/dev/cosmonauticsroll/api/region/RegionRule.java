package dev.cosmonauticsroll.api.region;

/**
 * 适用区域规则：判断玩家当前所处维度/位置是否启用本模组功能。
 *
 * <p>纯函数接口，不依赖 Minecraft 类与客户端类，可在设备 VM 上做逻辑单元测试。
 * 输入为维度注册名（如 {@code "minecraft:overworld"}、{@code "rocketnautics:deep_space"}）
 * 与玩家碰撞箱中心 Y 坐标，输出是否启用。</p>
 *
 * <p>第一版实现（阶段 2）：</p>
 * <ul>
 *   <li>主世界：碰撞箱中心 Y &gt;= 8000</li>
 *   <li>{@code rocketnautics:deep_space}：不依赖 Y 坐标</li>
 * </ul>
 *
 * <p>后续其他维度/高度通过新增实现扩展，多个规则由组合规则统一判断。</p>
 */
@FunctionalInterface
public interface RegionRule {

    /**
     * 判断给定维度与高度是否属于适用区域。
     *
     * @param dimensionId 维度注册名（{@code namespace:path} 形式）
     * @param centerY     玩家碰撞箱中心 Y 坐标
     * @return 是否启用本模组功能
     */
    boolean isActive(String dimensionId, double centerY);
}
