package dev.cosmonauticsroll.api.detect;

/**
 * 支撑面选择（纯逻辑，无 Minecraft 依赖；PRD 3.2-3 / 3.2-4）。
 *
 * <p>脚部采样点会沿身体下方下沉一小段距离（0.05~0.1 格）进入方块碰撞形状
 * 内部。若按「距离采样点最近的轴向面」判断接触面，当采样点靠近方块的竖直
 * 边界时，最近的面会变成侧面——采样网格半宽 0.3 格、方块边界每 1 格一次，
 * 采样点落在距侧面 0.1 格以内很常见，而下沉深度不足以排除这种情况。</p>
 *
 * <p>后果（2026-08-29 游戏内日志确认）：连续平地（全 oak_planks）被判定为
 * {@code MULTIPLE}（混入 WEST/SOUTH 等幻影侧面法线），身体竖直时出现
 * 「站到墙上」的目标方向，并让楼梯的「墙面优先」判定被幻影墙面触发，
 * 楼梯与普通表面的检测结果来回跳（{@code source=stair} ↔
 * {@code source=block}）。</p>
 *
 * <p>本类改为按「与身体上方向对齐」的支撑面判定：六个轴向面中取
 * {@code dot(面外法线, bodyUp)} 最大者（最朝向身体的那个面，即脚底真正
 * 踩住的面），对齐度相同则取距离更近者；{@code bodyUp} 缺失（null 或零
 * 向量）时退化为「最近面」判定，保持旧行为与兼容性。</p>
 *
 * <p>倾斜语义（PRD 2.4「身体旋转后脚部检测方向也必须跟随身体方向」）：
 * 身体竖直时支撑面是地面（UP）；身体倾向某侧超过 45° 时，支撑面就是该侧
 * 墙面——这正是地面↔墙面过渡的判定依据。</p>
 *
 * <p>已知差异：方块路径的「地面 + 墙面」墙角因此会返回单一支撑面
 * （SINGLE），不再产生 MULTIPLE；Sable 物理化方块（子世界）路径的多方向
 * 合并不受影响，仍可产生 MULTIPLE。</p>
 */
public final class SupportFaceSelector {

    /** 浮点比较容差。 */
    private static final double EPSILON = 1.0e-9;

    /** 六个轴向面（与距离参数顺序一致）。 */
    private static final SurfaceNormal[] FACES = {
            SurfaceNormal.UP, SurfaceNormal.DOWN, SurfaceNormal.EAST,
            SurfaceNormal.WEST, SurfaceNormal.SOUTH, SurfaceNormal.NORTH
    };

    private SupportFaceSelector() {
    }

    /**
     * 在六个轴向面中选择脚底实际接触的支撑面。
     *
     * @param dWest  采样点到方块西面（法线 WEST）的距离
     * @param dEast  采样点到方块东面（法线 EAST）的距离
     * @param dDown  采样点到方块下面（法线 DOWN）的距离
     * @param dUp    采样点到方块上面（法线 UP）的距离
     * @param dNorth 采样点到方块北面（法线 NORTH）的距离
     * @param dSouth 采样点到方块南面（法线 SOUTH）的距离
     * @param bodyUp 身体「上」方向（单位向量，可为 {@code null}）
     * @return 选中的支撑面法线（六个轴向之一）
     */
    public static SurfaceNormal select(double dWest, double dEast, double dDown, double dUp,
                                       double dNorth, double dSouth, Vec3d bodyUp) {
        double[] distances = {dUp, dDown, dEast, dWest, dSouth, dNorth};
        boolean useAlignment = bodyUp != null && bodyUp.lengthSquared() > EPSILON;

        double bestAlignment = Double.NEGATIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        SurfaceNormal best = null;
        for (int i = 0; i < FACES.length; i++) {
            double alignment = useAlignment ? FACES[i].vector().dot(bodyUp) : 0.0;
            if (useAlignment && alignment <= 0.0) {
                continue; // 背离身体的面不是支撑面：幻影侧面由此排除
            }
            boolean betterAlignment = alignment > bestAlignment + EPSILON;
            boolean tieButNearer = Math.abs(alignment - bestAlignment) <= EPSILON
                    && distances[i] < bestDistance;
            if (betterAlignment || tieButNearer) {
                bestAlignment = alignment;
                bestDistance = distances[i];
                best = FACES[i];
            }
        }
        return best;
    }
}
