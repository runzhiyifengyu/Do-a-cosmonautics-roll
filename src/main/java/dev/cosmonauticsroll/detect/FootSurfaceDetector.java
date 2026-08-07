package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.FootSurfaceResult;
import dev.cosmonauticsroll.api.detect.SurfaceNormal;
import dev.cosmonauticsroll.api.detect.SurfaceQuery;
import dev.cosmonauticsroll.api.detect.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚部表面检测器（阶段 3 核心，PRD 3.2）。
 *
 * <p>
 * 纯逻辑、无 Minecraft 依赖，可在设备 VM 上单元测试。工作流程：
 * </p>
 * <ol>
 * <li>以脚底中心为基准，按 {@link FootSamplingLayout} 生成局部采样点 （采样点随身体方向旋转，PRD 3.2-1/验收「身体旋转后脚部检测也随之旋转」）；</li>
 * <li>每个采样点沿身体下方（{@code -bodyUp}）下沉 {@link #queryOffset()} 后 调用 {@link SurfaceQuery} 获取接触到的表面法线（下沉进入表面内部一点， 便于碰撞形状查询命中）；</li>
 * <li>合并所有采样点的法线（PRD 3.2-4：合并同一方向的多个方块接触）：</li>
 * <li>无接触 → {@link FootSurfaceResult.Type#NONE}；只有单一方向 → {@link FootSurfaceResult.Type#SINGLE}（可站立）；两个及以上不同方向 → {@link FootSurfaceResult.Type#MULTIPLE}（墙角/边缘，不判定站立，PRD 3.2-5）。</li>
 * </ol>
 *
 * <p>
 * 只使用脚底采样点，不使用头部/身体其他部位作为站立依据（PRD 3.2-2）。
 * </p>
 */
public final class FootSurfaceDetector {

  /** 默认采样下沉距离（格）：进入表面内部一点，保证碰撞形状查询能命中。 */
  public static final double DEFAULT_QUERY_OFFSET = 0.1;

  private final FootSamplingLayout layout;
  private final SurfaceQuery query;
  private final double queryOffset;

  public FootSurfaceDetector(FootSamplingLayout layout, SurfaceQuery query) {
    this(layout, query, DEFAULT_QUERY_OFFSET);
  }

  /**
   * @param layout      脚底采样点布局
   * @param query       表面法线查询
   * @param queryOffset 采样点沿身体下方下沉距离（格）
   */
  public FootSurfaceDetector(FootSamplingLayout layout, SurfaceQuery query, double queryOffset) {
    if (layout == null) {
      throw new IllegalArgumentException("layout must not be null");
    }
    if (query == null) {
      throw new IllegalArgumentException("query must not be null");
    }
    this.layout = layout;
    this.query = query;
    this.queryOffset = queryOffset;
  }

  public double queryOffset() {
    return queryOffset;
  }

  /**
   * 检测脚底接触的表面。
   *
   * @param footCenter  脚底中心世界坐标
   * @param bodyUp      身体朝上单位向量（原版站立时为 +Y）
   * @param bodyForward 身体朝前单位向量
   * @return 检测结果：NONE / SINGLE(法线) / MULTIPLE
   */
  public FootSurfaceResult detect(Vec3d footCenter, Vec3d bodyUp, Vec3d bodyForward) {
    List<SurfaceNormal> normals = new ArrayList<>();
    Vec3d bodyDown = bodyUp.scale(-1.0).normalize();
    for (FootSamplingLayout.SamplePoint point : layout.points()) {
      Vec3d worldOffset = point.worldOffset(bodyUp, bodyForward);
      Vec3d sample = footCenter.add(worldOffset).add(bodyDown.scale(queryOffset));
      SurfaceNormal normal = query.query(sample);
      if (normal != null && !normals.contains(normal)) {
        normals.add(normal);
      }
    }
    if (normals.isEmpty()) {
      return FootSurfaceResult.none();
    }
    if (normals.size() == 1) {
      return FootSurfaceResult.single(normals.get(0));
    }
    return FootSurfaceResult.multiple();
  }
}
