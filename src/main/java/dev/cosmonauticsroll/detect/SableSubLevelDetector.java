package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.debug.Debug;

import net.minecraft.world.entity.Entity;

import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sable（Cosmonautics 物理化方块引擎）子世界表面方向检测（方案 A，阶段 3）。
 *
 * <p>物理化方块不是方块也不是实体，而是 Sable 的 SubLevel（子世界）。Sable 会
 * 追踪「玩家当前所在的子世界」（{@code Sable.HELPER.getTrackingSubLevel(entity)}），
 * 子世界带任意 3D 朝向（{@code logicalPose().orientation()}），其「上」方向即玩家
 * 站在该子世界上的站立方向（等价于表面法线）。</p>
 *
 * <p>本类通过反射访问 Sable，避免编译期硬依赖：</p>
 * <ul>
 *   <li>Sable 未安装或 API 变化 → 反射失败 → {@link #detectStandingDirection(Entity)}
 *       返回 {@code null}，调用方降级为纯方块检测（不崩溃，PRD 3.5-6）；</li>
 *   <li>Sable 已安装 → 返回子世界「上」方向单位向量。</li>
 * </ul>
 *
 * <p>依赖声明为可选（build.gradle {@code compileOnly} + neoforge.mods.toml
 * {@code optional}），不内嵌、不强制。</p>
 */
public final class SableSubLevelDetector {

    private static final AtomicBoolean REFLECT_WARNED = new AtomicBoolean(false);

    /** Sable 是否可用（类加载时反射探测一次）。 */
    private static final boolean AVAILABLE;

    /** Sable.HELPER 实例（SableCompanion 实现）。 */
    private static Object helper;

    /** SableCompanion.getTrackingSubLevel(Entity) 方法。 */
    private static Method getTrackingSubLevel;

    /** SubLevel.logicalPose() 方法。 */
    private static Method logicalPose;

    static {
        boolean ok = false;
        try {
            Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            Object h = sableClass.getField("HELPER").get(null);
            Method getTracking = h.getClass().getMethod("getTrackingSubLevel", Entity.class);
            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Method logicalPoseMethod = subLevelClass.getMethod("logicalPose");
            helper = h;
            getTrackingSubLevel = getTracking;
            logicalPose = logicalPoseMethod;
            ok = true;
        } catch (Throwable t) {
            warnOnce("Sable 不可用，降级为纯方块表面检测：", t);
        }
        AVAILABLE = ok;
    }

    private SableSubLevelDetector() {
    }

    /**
     * 检测玩家站立方向的 Sable 子世界方向。
     *
     * @param entity 玩家（或任意实体）
     * @return 子世界「上」方向单位向量（等价于站立表面法线）；无子世界或 Sable 不可用时 {@code null}
     */
    public static Vec3d detectStandingDirection(Entity entity) {
        if (!AVAILABLE) {
            return null;
        }
        try {
            Object subLevel = getTrackingSubLevel.invoke(helper, entity);
            if (subLevel == null) {
                return null;
            }
            Object pose = logicalPose.invoke(subLevel);
            Method orientationMethod = pose.getClass().getMethod("orientation");
            Object orientation = orientationMethod.invoke(pose);
            Quaterniondc quat = (Quaterniondc) orientation;
            Vector3d up = new Vector3d(0.0, 1.0, 0.0);
            quat.transform(up);
            return new Vec3d(up.x, up.y, up.z);
        } catch (Throwable t) {
            warnOnce("Sable 子世界方向检测异常：", t);
            return null;
        }
    }

    private static void warnOnce(String message, Throwable t) {
        if (REFLECT_WARNED.compareAndSet(false, true)) {
            Debug.log(message + "{}", t);
        }
    }
}
