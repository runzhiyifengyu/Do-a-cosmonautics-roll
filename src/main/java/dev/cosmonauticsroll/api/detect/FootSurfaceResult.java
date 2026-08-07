package dev.cosmonauticsroll.api.detect;

/**
 * 脚部表面检测结果（PRD 3.2-5：区分单一方向表面和多个不同方向表面）。
 *
 * <p>纯逻辑、无 Minecraft 依赖。三类结果：</p>
 * <ul>
 *   <li>{@link Type#NONE}：脚底没有接触任何表面（自由/太空状态）</li>
 *   <li>{@link Type#SINGLE}：脚底接触的所有表面方向一致（可站立，返回统一法线）</li>
 *   <li>{@link Type#MULTIPLE}：脚底同时接触两个及以上不同方向的表面（墙角/边缘，不判定站立）</li>
 * </ul>
 */
public final class FootSurfaceResult {

    /** 结果类型。 */
    public enum Type {
        /** 无表面。 */
        NONE,
        /** 单一方向表面。 */
        SINGLE,
        /** 多个不同方向表面。 */
        MULTIPLE
    }

    private final Type type;
    private final SurfaceNormal normal;

    private FootSurfaceResult(Type type, SurfaceNormal normal) {
        this.type = type;
        this.normal = normal;
    }

    /** 无表面结果。 */
    public static FootSurfaceResult none() {
        return new FootSurfaceResult(Type.NONE, null);
    }

    /** 单一方向表面结果。 */
    public static FootSurfaceResult single(SurfaceNormal normal) {
        return new FootSurfaceResult(Type.SINGLE, normal);
    }

    /** 多个不同方向表面结果。 */
    public static FootSurfaceResult multiple() {
        return new FootSurfaceResult(Type.MULTIPLE, null);
    }

    public Type type() {
        return type;
    }

    /** 单一方向表面的法线；NONE/MULTIPLE 时为 {@code null}。 */
    public SurfaceNormal normal() {
        return normal;
    }

    public boolean isNone() {
        return type == Type.NONE;
    }

    public boolean isSingle() {
        return type == Type.SINGLE;
    }

    public boolean isMultiple() {
        return type == Type.MULTIPLE;
    }

    @Override
    public String toString() {
        return type == Type.SINGLE ? "SINGLE(" + normal + ")" : type.toString();
    }
}
