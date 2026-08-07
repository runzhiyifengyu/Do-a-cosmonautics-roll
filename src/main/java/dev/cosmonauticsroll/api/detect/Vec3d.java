package dev.cosmonauticsroll.api.detect;

/**
 * 极简 3D 双精度向量（纯逻辑，无 Minecraft 依赖，可在设备 VM 上单元测试）。
 *
 * <p>
 * 仅提供阶段 3 脚部表面检测所需的数学：构造、加减、缩放、归一化、点积、叉积。 不做任何向量池/序列化等无关功能。
 * </p>
 */
public final class Vec3d {

  public final double x;
  public final double y;
  public final double z;

  public Vec3d(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public Vec3d add(Vec3d other) {
    return new Vec3d(x + other.x, y + other.y, z + other.z);
  }

  public Vec3d subtract(Vec3d other) {
    return new Vec3d(x - other.x, y - other.y, z - other.z);
  }

  public Vec3d scale(double factor) {
    return new Vec3d(x * factor, y * factor, z * factor);
  }

  public double dot(Vec3d other) {
    return x * other.x + y * other.y + z * other.z;
  }

  public Vec3d cross(Vec3d other) {
    return new Vec3d(y * other.z - z * other.y, z * other.x - x * other.z,
        x * other.y - y * other.x);
  }

  public double lengthSquared() {
    return dot(this);
  }

  public double length() {
    return Math.sqrt(lengthSquared());
  }

  /** 归一化；零向量返回自身（调用方应避免传入零向量）。 */
  public Vec3d normalize() {
    double len = length();
    if (len == 0.0) {
      return this;
    }
    return scale(1.0 / len);
  }

  @Override
  public String toString() {
    return String.format("(%.3f, %.3f, %.3f)", x, y, z);
  }
}
