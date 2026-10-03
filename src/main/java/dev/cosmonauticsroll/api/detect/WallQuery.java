package dev.cosmonauticsroll.api.detect;

/**
 * 脚踝水平面上的「墙」查询接口（纯函数式接口，无 Minecraft 依赖）。
 *
 * <p>用途（PRD 2.4「从地面走向墙面」/ 3.4-4「区分正常上楼和进入墙面」）：
 * 支撑面采样点沿身体下方下沉，只会落在脚底所踩的方块里，因此普通方块路径
 * 永远看不到竖直墙面。本接口在<b>脚踝高度</b>、沿身体水平方向向外偏移一点
 * 取样，查找「顶面高于脚底平面」的方块，返回朝向玩家的水平法线。</p>
 *
 * <p>与 {@link SurfaceQuery} 的分工：{@code SurfaceQuery} 解决「脚踩在哪个面」
 * （支撑面，按 {@link SupportFaceSelector} 与 bodyUp 对齐）；{@code WallQuery}
 * 解决「脚踝外侧有没有墙」。实现（{@code detect.LevelWallQuery}）会排除楼梯
 * 方块（楼梯交给楼梯逻辑）与高度不足的方块（半砖/台阶不算墙）。</p>
 *
 * @see dev.cosmonauticsroll.detect.FootSurfaceDetector
 */
@FunctionalInterface
public interface WallQuery {

    /**
     * 查询探测点处的墙面法线。
     *
     * @param worldPos 探测点世界坐标（脚踝高度、已沿 outward 向外偏移）
     * @param outward  探测的外向水平单位向量（从玩家指向探测方向）
     * @param bodyUp   身体「上」方向（单位向量，可为 {@code null}）
     * @return 朝向玩家的水平法线（六个轴向之一）；该点没有墙时返回 {@code null}
     */
    SurfaceNormal query(Vec3d worldPos, Vec3d outward, Vec3d bodyUp);
}
