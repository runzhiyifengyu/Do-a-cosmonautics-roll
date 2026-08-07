package dev.cosmonauticsroll.api.detect;

/**
 * 脚部表面查询接口：给定一个世界坐标点，返回该点接触到的表面法线。
 *
 * <p>纯函数式接口，无 Minecraft 依赖。阶段 3 的检测逻辑通过此接口读取
 * 「某采样点接触到的表面法线」，从而把「方块碰撞形状如何换算成法线」这一
 * Minecraft 适配细节隔离在实现之外：逻辑测试用假实现模拟地面/墙面/天花板/
 * 墙角/楼梯，游戏内由 MC 适配层（{@code Level.getCollisionShape}）实现。</p>
 *
 * @see dev.cosmonauticsroll.detect.FootSurfaceDetector
 */
@FunctionalInterface
public interface SurfaceQuery {

    /**
     * 查询世界坐标点接触到的表面法线。
     *
     * @param worldPos 世界坐标采样点（脚底检测点）
     * @return 该点接触到的表面法线；没有接触任何表面时返回 {@code null}
     */
    SurfaceNormal query(Vec3d worldPos);
}
