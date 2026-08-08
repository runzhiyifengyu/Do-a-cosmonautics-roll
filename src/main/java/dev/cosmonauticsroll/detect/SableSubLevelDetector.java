package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.debug.Debug;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sable（Cosmonautics 物理化方块引擎）子世界表面方向检测（阶段 3）。
 *
 * <p>物理化方块不是方块也不是实体，而是 Sable 的 SubLevel（子世界），子世界带任意
 * 3D 朝向（{@code logicalPose().orientation()}），其「上」方向即玩家站在该子世界上
 * 的站立方向（等价于表面法线）。双级检测：</p>
 * <ul>
 *   <li>方案 A：{@code Sable.HELPER.getTrackingSubLevel(entity)}——Sable 在实体与
 *       子世界碰撞时记录的「当前所在子世界」；</li>
 *   <li>方案 B：{@code Sable.HELPER.getAllIntersecting(level, bounds)}——用脚底薄片
 *       包围盒（世界坐标）查询所有相交子世界（服务端按子世界全局包围盒暴力匹配），
 *       取第一个可读出方向的子世界。A 返回 null（未追踪）时兜底。</li>
 * </ul>
 *
 * <p>注：曾评估 {@code getContaining(entity)}（按 chunk 查 plot 的子世界）作为兜底，
 * 已排除——Sable plot 网格原点在世界坐标约 ±2048 万格处（DEFAULT_ORIGIN=10000 plot，
 * 每 plot 128 chunk），玩家在世界原点附近的 chunk 坐标换算后落在 plot 网格外，恒为
 * null，不适用于正常坐标处的「站在子世界上」查询。</p>
 *
 * <p>本类通过反射访问 Sable，避免编译期硬依赖（build.gradle 无 compileOnly，仅
 * localRuntime transitive=false 供开发环境）：</p>
 * <ul>
 *   <li>Sable 未安装 → {@link #detectStandingDirection(Entity)} 返回 {@code null}，
 *       调用方降级为纯方块检测（不崩溃，PRD 3.5-6）；</li>
 *   <li>Sable 已安装但版本较旧（缺 getTrackingSubLevel 或缺 getAllIntersecting）→
 *       只用仍可用的那条路径，并输出一次性日志说明；</li>
 *   <li>两条路径都未命中 → 一次性日志说明（区分「Sable 不可用」「未追踪」「查询
 *       未命中」，便于游戏内验收定位）。</li>
 * </ul>
 */
public final class SableSubLevelDetector {

    private static final AtomicBoolean REFLECT_WARNED = new AtomicBoolean(false);
    private static final AtomicBoolean API_PART_MISSING_WARNED = new AtomicBoolean(false);
    private static final AtomicBoolean TRACKING_NULL_WARNED = new AtomicBoolean(false);
    private static final AtomicBoolean BOUNDS_MISS_WARNED = new AtomicBoolean(false);

    /** Sable 是否可用（任一检测路径可用即 true）。 */
    private static final boolean AVAILABLE;

    /** Sable.HELPER 实例（SableCompanion 实现）。 */
    private static Object helper;

    /** SableCompanion.getTrackingSubLevel(Entity)（方案 A；旧版 Sable 可能没有）。 */
    private static Method getTrackingSubLevel;

    /** SableCompanion.getAllIntersecting(Level, BoundingBox3dc)（方案 B；旧版可能没有）。 */
    private static Method getAllIntersecting;

    /** SubLevel.logicalPose()。 */
    private static Method logicalPose;

    /** dev.ryanhcode.sable.companion.math.BoundingBox3d 六参数构造器（方案 B）。 */
    private static Constructor<?> boundingBoxCtor;

    static {
        boolean trackingOk = false;
        boolean boundsOk = false;
        try {
            Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            Object h = sableClass.getField("HELPER").get(null);
            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Method logicalPoseMethod = subLevelClass.getMethod("logicalPose");
            helper = h;
            logicalPose = logicalPoseMethod;
            try {
                getTrackingSubLevel = h.getClass().getMethod("getTrackingSubLevel", Entity.class);
                trackingOk = true;
            } catch (NoSuchMethodException e) {
                logApiMissing("getTrackingSubLevel 不存在（Sable 版本过旧），仅使用物理包围盒查询（方案 B）");
            }
            try {
                Class<?> boundsInterface = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
                getAllIntersecting = h.getClass().getMethod("getAllIntersecting", Level.class, boundsInterface);
                boundingBoxCtor = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d")
                        .getConstructor(double.class, double.class, double.class,
                                double.class, double.class, double.class);
                boundsOk = true;
            } catch (Throwable t) {
                logApiMissing("getAllIntersecting/BoundingBox3d 不可用（Sable 版本过旧），仅使用 getTrackingSubLevel（方案 A）");
            }
        } catch (Throwable t) {
            warnOnce("Sable 不可用，降级为纯方块表面检测：", t);
        }
        AVAILABLE = trackingOk || boundsOk;
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
        Vec3d dir = trackingDirection(entity);
        if (dir != null) {
            return dir;
        }
        return intersectingDirection(entity);
    }

    /** 方案 A：Sable 记录的「实体当前所在子世界」方向。 */
    private static Vec3d trackingDirection(Entity entity) {
        if (getTrackingSubLevel == null) {
            return null;
        }
        try {
            Object subLevel = getTrackingSubLevel.invoke(helper, entity);
            if (subLevel == null) {
                logOnce(TRACKING_NULL_WARNED, "Sable 可用但 getTrackingSubLevel 返回 null，改用物理包围盒查询（方案 B）");
                return null;
            }
            return subLevelUpDirection(subLevel);
        } catch (Throwable t) {
            warnOnce("Sable getTrackingSubLevel 调用异常：", t);
            return null;
        }
    }

    /** 方案 B：脚底薄片包围盒（世界坐标）与子世界全局包围盒求交，取第一个命中子世界的方向。 */
    private static Vec3d intersectingDirection(Entity entity) {
        if (getAllIntersecting == null || boundingBoxCtor == null) {
            return null;
        }
        try {
            AABB box = entity.getBoundingBox();
            double pad = 0.05;
            double minX = box.minX - pad;
            double minY = box.minY - 0.1; // 脚底略下沉，保证与脚下表面方块相交
            double minZ = box.minZ - pad;
            double maxX = box.maxX + pad;
            double maxY = box.minY + 0.1; // 只取脚底薄片，避免命中头顶/身侧的子世界
            double maxZ = box.maxZ + pad;
            Object bounds = boundingBoxCtor.newInstance(minX, minY, minZ, maxX, maxY, maxZ);
            Object result = getAllIntersecting.invoke(helper, entity.level(), bounds);
            if (result instanceof Iterable<?> iterable) {
                for (Object subLevel : iterable) {
                    Vec3d dir = subLevelUpDirection(subLevel);
                    if (dir != null) {
                        return dir;
                    }
                }
            }
            logOnce(BOUNDS_MISS_WARNED, "Sable 脚底包围盒查询未命中任何子世界（getAllIntersecting 为空）");
            return null;
        } catch (Throwable t) {
            warnOnce("Sable 物理包围盒查询异常：", t);
            return null;
        }
    }

    /** 从子世界取「上」方向单位向量：logicalPose().orientation() 旋转 (0,1,0)。 */
    private static Vec3d subLevelUpDirection(Object subLevel) throws ReflectiveOperationException {
        Object pose = logicalPose.invoke(subLevel);
        Method orientationMethod = pose.getClass().getMethod("orientation");
        Object orientation = orientationMethod.invoke(pose);
        Quaterniondc quat = (Quaterniondc) orientation;
        Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        quat.transform(up);
        return new Vec3d(up.x, up.y, up.z);
    }

    private static void logApiMissing(String detail) {
        if (API_PART_MISSING_WARNED.compareAndSet(false, true)) {
            Debug.log("Sable 部分 API 不可用：{}", detail);
        }
    }

    private static void logOnce(AtomicBoolean flag, String message) {
        if (flag.compareAndSet(false, true)) {
            Debug.log(message);
        }
    }

    private static void warnOnce(String message, Throwable t) {
        if (REFLECT_WARNED.compareAndSet(false, true)) {
            Debug.log(message + "{}", t);
        }
    }
}
