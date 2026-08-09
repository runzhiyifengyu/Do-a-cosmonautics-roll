package dev.cosmonauticsroll.rot;

import dev.cosmonauticsroll.api.detect.FootSurfaceResult;
import dev.cosmonauticsroll.api.detect.Vec3d;

/**
 * 站立方向状态机（阶段 4，PRD 3.3）。
 *
 * <p>把「脚部表面检测结果」翻译成「当前身体站立方向」：</p>
 * <ul>
 *   <li>SINGLE（单一方向表面，地面/墙面/天花板）→ 目标方向 = 表面法线
 *       （法线即站立时身体的「上」方向）；</li>
 *   <li>NONE（无表面）/ MULTIPLE（多方向表面，墙角）→ 保持当前方向，
 *       不主动切换（PRD 2.4：一般不会主动从一个表面跳转到另一个表面，
 *       只有脚部检测到新表面时才更新；墙角不判定站立，保持方向防抖）；</li>
 * </ul>
 *
 * <p>纯逻辑、无 Minecraft 依赖，可在设备 VM 上单元测试。
 * 与 {@code RotationSmoother} 配合：本类决定「要不要更新目标」，
 * 平滑器负责「怎么平滑过渡」与「防抖」。快速移动/快速离开表面时，
 * NONE/MULTIPLE 保持当前方向，不会瞬间跳回竖直（PRD 3.3-4）。</p>
 */
public final class StandingDirectionState {

    /** 当前站立方向（身体「上」方向，单位向量；初始竖直）。 */
    private Vec3d current = new Vec3d(0, 1, 0);

    /**
     * 每 tick 输入一次脚部表面检测结果，更新当前站立方向。
     *
     * @param result 脚部检测结果（NONE / SINGLE / MULTIPLE）
     * @return 更新后的站立方向
     */
    public Vec3d update(FootSurfaceResult result) {
        if (result != null && result.isSingle()) {
            Vec3d normal = result.normal();
            if (normal != null && normal.lengthSquared() > 0.0) {
                current = normal.normalize();
            }
        }
        // NONE / MULTIPLE：保持当前方向（不主动跳转，防抖）
        return current;
    }

    /** 当前站立方向（身体「上」方向）。 */
    public Vec3d current() {
        return current;
    }

    /** 重置为竖直方向（离开适用区域 / 传送 / 死亡重生时）。 */
    public void reset() {
        current = new Vec3d(0, 1, 0);
    }
}
