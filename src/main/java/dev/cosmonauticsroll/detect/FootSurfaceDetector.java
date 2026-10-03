package dev.cosmonauticsroll.detect;

import dev.cosmonauticsroll.api.detect.FootSurfaceResult;
import dev.cosmonauticsroll.api.detect.SurfaceNormal;
import dev.cosmonauticsroll.api.detect.SurfaceQuery;
import dev.cosmonauticsroll.api.detect.Vec3d;
import dev.cosmonauticsroll.api.detect.WallQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚部表面检测器（阶段 3 核心，PRD 3.2；阶段 5 补丁8 增加脚踝水平探测）。
 *
 * <p>
 * 纯逻辑、无 Minecraft 依赖，可在设备 VM 上单元测试。工作流程：
 * </p>
 * <ol>
 * <li>以脚底中心为基准，按 {@link FootSamplingLayout} 生成局部采样点 （采样点随身体方向旋转，PRD 3.2-1/验收「身体旋转后脚部检测也随之旋转」）；</li>
 * <li>每个采样点沿身体下方（{@code -bodyUp}）下沉 {@link #queryOffset()} 后 调用 {@link SurfaceQuery} 获取接触到的表面法线（下沉进入表面内部一点， 便于碰撞形状查询命中）；查询同时传入 {@code bodyUp}，供实现按「支撑面」 而非「几何最近面」判定（避免平地边界处的幻影侧面，PRD 3.2-4）；</li>
 * <li>脚踝水平探测（可选，{@link WallQuery}）：沿身体水平方向向外、在脚踝高度 取 4 个探测点，识别「顶面高于脚底平面」的真实墙面（PRD 2.4「从地面 走向墙面」/ 3.4-4「进入墙面」）。没有支撑面时不探测（PRD 2.3：离开 方块后进入自由旋转状态，不主动吸附墙面）；</li>
 * <li>合并所有法线（PRD 3.2-4：合并同一方向的多个方块接触）：</li>
 * <li>无接触 → {@link FootSurfaceResult.Type#NONE}；只有单一方向 → {@link FootSurfaceResult.Type#SINGLE}（可站立）；两个及以上不同方向 → {@link FootSurfaceResult.Type#MULTIPLE}（墙角/边缘，不判定站立，PRD 3.2-5）。<b>例外</b>：支撑面 + 单一墙面且玩家 正朝墙面移动（{@link #detect(Vec3d, Vec3d, Vec3d, Vec3d)}）时返回 墙面方向 SINGLE——这是 PRD 2.4「从地面走向墙面时允许平滑旋转并继续 行走」所需的过渡；静止不动时仍为 MULTIPLE（不判定站立）。</li>
 * </ol>
 *
 * <p>
 * 只使用脚底与脚踝探测点，不使用头部/身体其他部位作为站立依据（PRD 3.2-2）。
 * </p>
 */
public final class FootSurfaceDetector {

  /** 默认采样下沉距离（格）：进入表面内部一点，保证碰撞形状查询能命中。 */
  public static final double DEFAULT_QUERY_OFFSET = 0.1;

  /** 「走向墙面」判定：水平移动速度下限（格/tick，0.02 ≈ 0.4 m/s）。 */
  public static final double MIN_MOVE_PER_TICK = 0.02;

  /** 「走向墙面」判定：移动方向与「撞向墙面」方向的最小对齐度（0.5 ≈ 60° 内）。 */
  public static final double MOVE_INTO_WALL_ALIGN = 0.5;

  /** 支撑面判定：法线与 bodyUp 的最小对齐度（0.7 ≈ 45° 内）。 */
  public static final double SUPPORT_ALIGN = 0.7;

  private final FootSamplingLayout layout;
  private final SurfaceQuery query;
  private final WallQuery wallQuery;
  private final double queryOffset;

  public FootSurfaceDetector(FootSamplingLayout layout, SurfaceQuery query) {
    this(layout, query, null, DEFAULT_QUERY_OFFSET);
  }

  public FootSurfaceDetector(FootSamplingLayout layout, SurfaceQuery query, double queryOffset) {
    this(layout, query, null, queryOffset);
  }

  /**
   * @param layout      脚底采样点布局
   * @param query       支撑面法线查询
   * @param wallQuery   脚踝水平墙面查询（可为 null = 不探测墙面）
   * @param queryOffset 采样点沿身体下方下沉距离（格）
   */
  public FootSurfaceDetector(FootSamplingLayout layout, SurfaceQuery query, WallQuery wallQuery,
                             double queryOffset) {
    if (layout == null) {
      throw new IllegalArgumentException("layout must not be null");
    }
    if (query == null) {
      throw new IllegalArgumentException("query must not be null");
    }
    this.layout = layout;
    this.query = query;
    this.wallQuery = wallQuery;
    this.queryOffset = queryOffset;
  }

  public double queryOffset() {
    return queryOffset;
  }

  /** 是否启用脚踝水平墙面探测。 */
  public boolean hasWallProbe() {
    return wallQuery != null;
  }

  /**
   * 检测脚底接触的表面（不启用「走向墙面」过渡：移动方向视为未知）。
   *
   * @param footCenter  脚底中心世界坐标
   * @param bodyUp      身体朝上单位向量（原版站立时为 +Y）
   * @param bodyForward 身体朝前单位向量
   * @return 检测结果：NONE / SINGLE(法线) / MULTIPLE
   */
  public FootSurfaceResult detect(Vec3d footCenter, Vec3d bodyUp, Vec3d bodyForward) {
    return detect(footCenter, bodyUp, bodyForward, null);
  }

  /**
   * 检测脚底接触的表面。
   *
   * @param footCenter    脚底中心世界坐标
   * @param bodyUp        身体朝上单位向量（原版站立时为 +Y）
   * @param bodyForward   身体朝前单位向量
   * @param moveDirection 本 tick 水平移动向量（格/tick，可为 null = 未知）；
   *                      仅用于「支撑面 + 单一墙面」时判断玩家是否正走向墙面
   *                      （PRD 2.4 地面→墙面过渡）
   * @return 检测结果：NONE / SINGLE(法线) / MULTIPLE
   */
  public FootSurfaceResult detect(Vec3d footCenter, Vec3d bodyUp, Vec3d bodyForward,
                                  Vec3d moveDirection) {
    List<SurfaceNormal> support = new ArrayList<>();
    Vec3d bodyDown = bodyUp.scale(-1.0).normalize();
    for (FootSamplingLayout.SamplePoint point : layout.points()) {
      Vec3d worldOffset = point.worldOffset(bodyUp, bodyForward);
      Vec3d sample = footCenter.add(worldOffset).add(bodyDown.scale(queryOffset));
      SurfaceNormal normal = query.query(sample, bodyUp);
      if (normal != null && !support.contains(normal)) {
        support.add(normal);
      }
    }
    if (support.isEmpty()) {
      // 无支撑面：不做墙面探测（PRD 2.3：离开方块后进入自由旋转状态）
      return FootSurfaceResult.none();
    }

    List<SurfaceNormal> walls = probeWalls(footCenter, bodyUp, bodyForward);
    if (walls.isEmpty()) {
      return support.size() == 1 ? FootSurfaceResult.single(support.get(0))
              : FootSurfaceResult.multiple();
    }

    List<SurfaceNormal> merged = new ArrayList<>(support);
    for (SurfaceNormal wall : walls) {
      if (!merged.contains(wall)) {
        merged.add(wall);
      }
    }
    if (merged.size() == 1) {
      return FootSurfaceResult.single(merged.get(0));
    }

    // PRD 2.4：脚部同时接触「支撑面 + 单一墙面」，且玩家正朝墙面移动
    // → 允许平滑转向墙面并继续行走；静止不动时保持 MULTIPLE（不判定站立，3.2-5）。
    if (support.size() == 1 && walls.size() == 1
            && isSupport(support.get(0), bodyUp)
            && isMovingIntoWall(moveDirection, walls.get(0))) {
      return FootSurfaceResult.single(walls.get(0));
    }
    return FootSurfaceResult.multiple();
  }

  /** 脚踝水平探测：收集脚踝高度外侧的真实墙面法线（去重）。 */
  private List<SurfaceNormal> probeWalls(Vec3d footCenter, Vec3d bodyUp, Vec3d bodyForward) {
    List<SurfaceNormal> walls = new ArrayList<>();
    if (wallQuery == null) {
      return walls;
    }
    for (FootSamplingLayout.WallProbe probe : FootSamplingLayout.ankleProbes()) {
      Vec3d sample = footCenter.add(probe.worldOffset(bodyUp, bodyForward));
      Vec3d outward = probe.outward(bodyUp, bodyForward);
      SurfaceNormal normal = wallQuery.query(sample, outward, bodyUp);
      if (normal != null && normal.isHorizontal() && !walls.contains(normal)) {
        walls.add(normal);
      }
    }
    return walls;
  }

  /** 法线是否为「支撑面」（与身体上方向对齐）。 */
  private static boolean isSupport(SurfaceNormal normal, Vec3d bodyUp) {
    Vec3d up = bodyUp.normalize();
    return up.lengthSquared() > 0.0 && normal.vector().dot(up) >= SUPPORT_ALIGN;
  }

  /** 玩家本 tick 是否朝墙面移动（水平速度足够且方向大致撞向墙面）。 */
  private static boolean isMovingIntoWall(Vec3d moveDirection, SurfaceNormal wall) {
    if (moveDirection == null) {
      return false;
    }
    Vec3d horizontal = new Vec3d(moveDirection.x, 0.0, moveDirection.z);
    double speed = horizontal.length();
    if (speed < MIN_MOVE_PER_TICK) {
      return false;
    }
    Vec3d direction = horizontal.scale(1.0 / speed);
    return direction.dot(wall.vector().scale(-1.0)) > MOVE_INTO_WALL_ALIGN;
  }
}
