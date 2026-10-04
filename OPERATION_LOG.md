# Do-a-Cosmonautics-roll 操作日志

> 产品需求见 [PRD.md](PRD.md)，开发规则与任务见 [DEVELOPMENT.md](DEVELOPMENT.md)，检查清单见 [CHECKLIST.md](CHECKLIST.md)。
> 本文件记录 AI 在本项目中的**每次实际操作**（编辑了哪些文件、做了什么改动、验证结果），按日期倒序排列。
> 游戏内验收等由用户执行的操作，由用户在对应条目中补充结果。
> Git 操作（commit/push）全部由用户执行，AI 不执行任何 Git 操作。

---

## 2026-10-04（阶段 5 补丁9：第三轮验收日志分析 + 移动意图跟踪修复）

### 本次操作内容

1. **背景**：用户完成第三轮游戏内验收（补丁8 构建），日志覆盖写入 `log/latest_game.log`（1412 行 / 522 条 `[Debug]`，15:20:19–15:22:38）。
2. **第三轮日志结论（通过项）**：
   - **脚踝墙面探测生效**：3 次真实墙接触（整方块 `oak_planks`，`脚踝探测` 命中日志）→ `result=MULTIPLE`（支撑面 UP + 墙面法线），贴墙/非走向墙面时不判定站立 ✓。
   - **矮方块不误判**：`create:red_seat`（Create 座椅）处 6 个支撑采样 + 4 个探测点都在方块内，结果仍 `SINGLE((0.000,1.000,0.000)) source=block` ✓（既不算楼梯也不算墙）。
   - **楼梯正常**：57 条 `楼梯：`，progress `0.00→0.47`，7 次 `source=stair` 快照；角度 = `progress×45°`。
   - **日志不刷屏**：`防抖` 0 条、`恢复竖直` 0 条。49 条脚部检测 = 44 SINGLE / 3 MULTIPLE / 2 NONE（无异常、无崩溃）。
3. **第三轮未通过项**：**「走向墙面」过渡一次都没触发**（98 条 `旋转` 的 target 全为 UP 或楼梯倾斜）。代码审查定位两个原因：
   - 服务端玩家的 `entity.xo/zo` 在实体 tick 内基本等于当前位置，取不到客户端行走位移 → 补丁8 的 `isMovingIntoWall` 恒为 false；
   - 即便能取到位移，玩家贴住墙后位移本来就是 0——而脚踝探测恰恰只在贴墙时才命中，两者时间上重合。
4. **补丁9 修复**：
   - 新增纯逻辑 `api/detect/MovementIntentTracker`：每 tick 自行求差（同一 tick 重复调用返回同一结果）+ 单 tick 位移 > 1.0 视为传送并丢弃意图 + `intent(tick, window)` 在窗口内保留「最近一次真实移动」。
   - 新增游戏内适配 `detect/PlayerMoveTracker`：按玩家 UUID 维护跟踪器，窗口 `INTENT_WINDOW_TICKS = 10`（约 0.5 秒），登出/维度切换/死亡重生成清。
   - `FootSurfaceResolver` 改用移动意图（不再使用 `xo/zo`）；`RotationTicker` 在 logout 与 reset 时清理跟踪器。
   - 修正脚踝探测调试标签：改为身体坐标系（前/后/左/右），此前按世界轴命名，身体转向后容易读错。
   - 测试：`FootSurfaceLogicTest` 新增 `testMovementIntent`（11 断言：首次无位移、位移记录、窗口内保留意图、窗口外归零、同 tick 重复一致、传送丢弃、reset），78 → **89** 断言；LogicTestSuite 总 **215**。
5. **文档同步**：`CHECKLIST.md` 新增第三轮验证记录与补丁9 待验收项；`PROGRESS.md` 新增本条目；`DEVELOPMENT.md` 阶段 5 补丁9 说明与当前状态。

### 验证状态

- 设备内 JDK 21 `javac` 编译纯逻辑 + 逻辑测试源码：**无错误**；`java LogicTestSuite`：**215/215 通过**（Region 57 / Foot 89 / Rotation 39 / Stair 30）。
- MC 适配层（`PlayerMoveTracker` / `FootSurfaceResolver` / `RotationTicker`）静态审查无语法错误；**GitHub Actions 验证通过**：commit `960d7da` → run [37185892663](https://github.com/runzhiyifengyu/Do-a-cosmonautics-roll/actions/runs/37185892663) `./gradlew build runLogicTests` **success**（编译含 MC 适配层 + 215 断言，`mod-jar` 产物已上传）。
- 已由 AI 提交推送（用户授权 AI 执行 Git；`log/` 不提交）。

### 待办

1. ✅ Actions 已验证（run 37185892663 成功）。
2. **第四轮游戏内复验（重点：走向墙面）**：面对 1 格以上高的墙**按住前进**走过去 → 应出现 `result=SINGLE((水平法线)) source=block`、`旋转` target 变水平；停下不动（或从未走向它）应保持 `MULTIPLE`/UP。
3. 仍未覆盖：模组楼梯（3.4-1 后半）、半砖/活板门不误判（本轮只有 Create 座椅矮方块）、楼梯尽头有墙时 progress 停止推进（3.4-4）、上下楼/倒退/横向确认（3.4-6）、离开区域恢复竖直。

---

## 2026-10-03（阶段 5 补丁8：脚踝水平墙面探测 + 走向墙面过渡）

### 本次操作内容

1. **背景**：用户确认补丁7 复验结果（阻塞 BUG 已消除）后，AI 指出 A 方案留下的缺口——支撑面采样点沿身体下方下沉，身体竖直时任何方块都返回上面，**普通方块路径永远看不到竖直墙面**，因此「地面→墙面」（PRD 2.4）、站墙面/站天花板、3.4-4「进入墙面」在普通方块上都没有触发条件（阶段 3/4 的墙面验收走的是 Sable 物理化方块路径）。用户选择「现在就做 B」。
2. **新增纯逻辑 `api/detect/WallQuery`**：脚踝探测接口 `query(Vec3d worldPos, Vec3d outward, Vec3d bodyUp)`。
3. **新增游戏内实现 `detect/LevelWallQuery`**：读该点方块碰撞形状；排除楼梯方块（`StairBlockQuery.isStair`）；身体接近竖直时要求方块顶面高出探测点 ≥ `MIN_WALL_EXTENT`（0.5 格）——半砖（0.5）/台阶/同层地板都不算墙；用 `SupportFaceSelector` 以「朝向玩家」（`-outward`）为 up 取面向玩家的面，仅返回水平法线。
4. **`FootSamplingLayout.ankleProbes()`**：脚踝高度 0.25 格、水平外偏 0.31 格（略超碰撞箱半宽 0.3）的右/左/前/后 4 个探测点（`WallProbe`，含外向方向，随身体方向旋转）。
5. **`FootSurfaceDetector`**：构造函数支持注入 `WallQuery`；新增 `detect(footCenter, bodyUp, bodyForward, moveDirection)`；无支撑面时不探测墙面（PRD 2.3）；「支撑面 + 单一墙面」且玩家正走向墙面（`MIN_MOVE_PER_TICK` 0.02、`MOVE_INTO_WALL_ALIGN` 0.5）→ 返回墙面方向（PRD 2.4 地面→墙面平滑旋转）；静止/背离/侧向 → MULTIPLE（PRD 3.2-5）。
6. **`FootSurfaceResolver`**：接入 `LevelWallQuery`；用 `entity.getX() - entity.xo` / `getZ() - entity.zo` 计算本 tick 水平移动向量；调试日志新增「脚踝探测[±右/±前]」命中点（`isSolidAt` 过滤，平地不刷屏）。
7. **`StairSurfaceResolver`**：墙面优先判定改用脚踝探测，且只取前方 ±60° 锥内的墙（`outward.dot(bodyForward) >= 0.5`）——侧墙/背墙不算「进入墙面」，否则贴着墙壁上楼会误停楼梯倾斜。
8. **测试**：`FootSurfaceLogicTest` 新增 `testAnkleProbeLayout`（8 断言：4 方向、脚踝高度 0.25、外偏 0.31、单位外向量、随身体转向）+ `testWallProbeDetection`（15 断言：墙在探测距离外 SINGLE(UP)、贴墙静止 MULTIPLE、走向墙面 SINGLE(WEST)、背离/侧向 MULTIPLE、悬空不吸附 NONE、探测点 4 个且在脚踝高度）；该文件 55 → **78** 断言，LogicTestSuite 总 **204**。
9. **文档同步**：`DEVELOPMENT.md`（阶段 5 补丁8 实现说明 + 当前状态 + 第 9 节进度）、`CHECKLIST.md`（补丁7 已知差异标注为已由补丁8 补回）、`PROGRESS.md`（本补丁条目）。

### 验证状态

- 设备内 JDK 21 `javac` 编译纯逻辑 + 逻辑测试源码：**无错误**；`java LogicTestSuite`：**204/204 通过**（Region 57 / Foot 78 / Rotation 39 / Stair 30）。
- MC 适配层（`LevelWallQuery` / `FootSurfaceResolver` / `StairSurfaceResolver`）静态审查无语法错误；**GitHub Actions 验证通过**：commit `0925474` → run [37090382318](https://github.com/runzhiyifengyu/Do-a-cosmonautics-roll/actions/runs/37090382318) `./gradlew build runLogicTests` **success**（编译含 MC 适配层 + 204 断言，`mod-jar` 产物已上传）。
- 已由 AI 提交推送（用户授权 AI 执行 Git；`log/` 不提交）。

### 待办

1. 游戏内复验（`debug on` + `debug region 0`）：普通方块走向 1 格以上高的墙 → `脚踝探测` 命中 + `result=SINGLE(水平)`（移动时）/ 停下回 `MULTIPLE`；楼梯尽头有墙 → progress 停止推进；楼梯侧/背有墙 → 上楼倾斜照常；半砖/活板门旁边 → 仍 `SINGLE(UP)`（不算墙也不算楼梯）；模组楼梯、上下楼/倒退/横向（3.4-1 后半、3.4-6）。
2. 复验通过后收尾阶段 5：勾选 CHECKLIST 3.4-1~6 与验收项。

---

## 2026-10-03（阶段 5 补丁7 复验：第二份验收日志分析 —— 阻塞 BUG 已消除）

### 本次操作内容

1. **背景**：用户完成补丁7 复验（`debug on` + `debug region 0`），日志覆盖写入 `log/latest_game.log`（1091 行 / 201 条 `[Debug]`，10:00:52–10:03:06）。AI 逐条分析并与第一轮对比。
2. **结论（阻塞 BUG 消除）**：
   - 脚部检测 18 条 = **13 `SINGLE((0,1,0)) source=block`** + 3 `SINGLE(...) source=stair` + 2 `NONE`；`MULTIPLE` 由第一轮 24 条 → **0 条**（平地幻影侧面消失）。
   - 36 条 `旋转` 的 target 只有 UP 与楼梯倾斜（0.40→(0.309,0.951,0)=18°、0.33→(0,0.966,-0.259)=15°、0.13→(0,0.995,-0.105)=6°），与 `progress×45°` 完全吻合；无 `(0,0,1)`/`(-1,0,0)` 类幻影墙面目标。
   - 日志刷屏消除：`防抖：目标被忽略` 124 → **0**；`旋转（恢复竖直）` 86 → **0**。
   - 楼梯识别正常：47 次采样命中 `oak_stairs`；进度序列 `0.00→0.07→0.13→0.20→0.27→0.33→0.40`；无 CosmonauticsRoll 异常/崩溃。
3. **仍未覆盖**：模组楼梯（3.4-1 后半）、半砖/活板门不误判（3.4 验收）、进墙面（3.4-4，需在楼梯尽头搭墙）、上下楼/倒退/横向的操作确认（3.4-6）、离开区域恢复竖直日志（本轮未离开区域，`isLeaving` 分支未触发；阶段 4 已验收该功能）。
4. **观察（记录待决定）**：① 进度实测上限约 0.40（两轮 0.40 / 0.47）→ 倾斜最大约 18–21°，未达映射上限 45°；② 上楼梯时目标在「台阶顶面 UP ↔ 斜面约 18°」随台阶切换（10:02:50 stair 15° → 10:02:51 block UP → 10:02:52 stair 18° → 10:02:52 block UP），阶段 6 应用身体旋转后可能表现为约 1 Hz 轻微俯仰摆动。
5. **文档同步**：`CHECKLIST.md` 阶段 5 新增第二轮验证记录表（含未覆盖项与观察）；`PROGRESS.md` 新增复验条目；`DEVELOPMENT.md` 阶段 5 当前状态更新；本条目。

### 验证状态

- 第一轮发现的阻塞性 BUG（平地误判 MULTIPLE + 幻影墙面 + 日志刷屏）**已在第二轮日志中确认消除**。
- 阶段 5 收尾仍缺 4 项补测（模组楼梯 / 半砖活板门 / 进墙面 / 上下楼倒退横向），以及 2 项待决定的观察。
- 本次文档更新由 AI 提交推送（用户已授权 AI 执行 Git；`log/` 已在 `.gitignore` 中不提交）。

### 待办

1. 用户补测：模组楼梯、半砖/活板门、楼梯尽头进墙面、上下楼/倒退/横向（说明操作含义即可判定 3.4-6）。
2. 用户决定观察项 ① 进度上限 0.40（约 18–21°）与 ② 台阶顶面/斜面目标切换，是否可接受或需调整映射/采样。
3. 确认后更新 CHECKLIST 阶段 5 勾选状态（3.4-2 / 3.4-3 / 3.4-5 + 普通方块判断已有充分证据；模组楼梯、进墙面、半砖活板门、上下楼倒退横向补测后勾选）。

---

## 2026-10-03（阶段 5 补丁7：验收日志分析 + 支撑面法线修复 + 日志降噪）

### 本次操作内容

1. **背景**：用户完成阶段 5 游戏内验收（`/cosmonauticsroll debug on` + `debug region 0`，测试机日志放在 `log/latest_game.log`，561 条 `[Debug]`）。AI 逐条分析日志，判定「楼梯路径基本可用，但发现一个阻塞性 BUG」。
2. **日志结论（通过项）**：区域高度覆盖命令生效（覆盖 0 → 立即进入 ACTIVE）；原版 `oak_stairs` 识别成功（`source=stair`）；进度连续 `0.00→0.07→0.20→0.27→0.33→0.40→0.47`（密集网格粒度 1/15≈0.067）；`progress=0.47 → target=(0.000,0.934,-0.358)` 与 `progress×45°=21.2°` 完全吻合；无崩溃、无异常堆栈。
3. **发现的阻塞性 BUG（幻影侧面法线）**：`detect/LevelSurfaceQuery` 按「距离采样点最近的轴向面」判定接触面，而脚部采样点沿身体下方下沉 0.1 格；采样网格半宽 0.3 格、方块边界每 1 格一次，采样点经常落在距竖直侧面 <0.1 格处 → 返回侧面法线。后果：**连续平地（全 oak_planks）被判 MULTIPLE**（34 条脚部检测中 24 条为 `MULTIPLE source=block`，如 L963）；身体竖直时出现 `target=(0,0,1)`/`(-1,0,0)` 的幻影墙面目标；`StairSurfaceResolver` 的「墙面优先」被幻影墙面触发，楼梯与普通表面结果来回跳（`source=stair` ↔ `source=block`）。
4. **修复 A（支撑面法线）**：
   - 新增纯逻辑 `api/detect/SupportFaceSelector`：六个轴向面中取 `dot(面外法线, bodyUp)` 最大者（最朝向身体的支撑面），对齐度相同取更近者；`bodyUp` 缺失/零向量时退化为「最近面」（保持旧行为）。
   - `api/detect/SurfaceQuery` 签名扩展为 `query(Vec3d worldPos, Vec3d bodyUp)`（假实现可忽略该参数）；`detect/LevelSurfaceQuery`、`detect/FootSurfaceDetector`、`detect/StairSurfaceResolver`（墙面判定）同步传入 `bodyUp`。
5. **修复 C（日志降噪）**：
   - `rot/RotationTicker`：防抖日志仅在「本 tick 期望方向与当前目标夹角 > 平滑器死区」时输出（稳态不再每 tick 刷屏；日志 124 条 → 预期个位数）；`旋转（恢复竖直）` 仅在 `SmoothStandingRotation.isLeaving()` 期间输出（区域外常态不再刷屏；日志 86 条）。
   - `rot/SmoothStandingRotation.update()`：恢复竖直完成（与竖直夹角 ≤ `RESTORE_DONE_RADIANS`）后自动退出恢复模式，避免恢复模式长期挂起导致的日志刷屏。
6. **测试扩展**：
   - `FootSurfaceLogicTest`：新增 `testSupportFaceSelection`（7 断言：旧最近面规则对照、竖直取上/倒立取下/朝东取东、bodyUp 缺失与零向量退化、对角 bodyUp 平手取更近面）+ `testFlatGroundBoundaryRegression`（2 断言：连续平地贴近方块边界仍 `SINGLE(UP)`）；该文件 46 → 55 断言。
   - `RotationLogicTest`：新增 `testLeaveRegionCompletes`（3 断言：进入恢复模式、完成后退出、退出时已竖直）；该文件 30+ → 39 断言。
   - `LogicTestSuite` 总计 181 断言（Region 57 / Foot 55 / Rotation 39 / Stair 30）。

### 验证状态

- 设备内 `javac`（JDK 21）编译纯逻辑 + 逻辑测试源码：**无错误**；`java LogicTestSuite`：**181/181 通过**。
- MC 适配层（LevelSurfaceQuery / StairSurfaceResolver / RotationTicker / FootSurfaceDetector）仅静态审查（设备无 NeoForge/MC classpath），待 Actions 编译验证。
- Git：本次 commit/push 由用户明确指示 AI 执行（`.gitignore` 新增 `log/`，本地游戏日志不提交），与开发规则 3「Git 操作由用户执行」的例外已由用户授权。
- **GitHub Actions 验证通过**：commit `65b2b68` → run [37087294992](https://github.com/runzhiyifengyu/Do-a-cosmonautics-roll/actions/runs/37087294992)「Build with Gradle」`success`（工作流执行 `./gradlew build runLogicTests`，即 `compileJava`（含 MC 适配层）+ 181 条逻辑断言全部通过；产物 `mod-jar` 已上传，58959 B）。

### 已知差异（按开发规则 10 记录）

- 方块路径的「地面 + 墙面」墙角现在返回单一支撑面（SINGLE），不再产生 MULTIPLE；Sable 物理化方块（子世界）路径不受影响，仍可 MULTIPLE。此为本轮 A 方案（支撑面法线）的取舍；若需在方块路径恢复 PRD 2.4 的墙角 MULTIPLE 语义，需追加「脚底平面水平探测」（只把顶面高于脚底平面的方块算墙）的 B 方案。→ **补丁8 已实现 B 方案**（见上方补丁8 条目）。

### 待办

1. ✅ 已完成：AI 按用户指示 commit `65b2b68` 并 push（`log/` 已加入 `.gitignore`，未提交）；Actions run 37087294992 成功。
2. 游戏内复验（`debug on` + `debug region 0`）：平地应见 `result=SINGLE((0.000,1.000,0.000)) source=block`（不再 MULTIPLE）；幻影墙面目标消失；楼梯 `progress` 连续、`source` 不再在 stair/block 间跳；日志不再刷屏（防抖/恢复竖直）。
3. 仍待验收：模组楼梯识别、半砖/活板门不误判、上下楼/倒退/横向（3.4-1 后半、3.4-6、3.4 验收）。

---

## 2026-08-11（阶段 5 补丁4：日志类别过滤命令——自由选择输出哪些日志）

### 本次操作内容

1. **背景**：用户验收时被日志刷屏困扰，需要能自由选择输出哪些日志的指令。
2. **`debug/Debug.java` 扩展**：
   - 新增 4 个日志类别常量：`CATEGORY_REGION`（区域）/ `CATEGORY_FOOT`（脚部检测）/ `CATEGORY_ROTATION`（旋转）/ `CATEGORY_STAIR`（楼梯）；
   - 类别开关集合（默认全部开启）+ `isCategoryEnabled` / `setCategoryEnabled`（未知类别返回 false）/ `disabledCategories` / `allCategories`；
   - 新增 `log(String category, String message, Object...)` 重载——受「全局开关 + 类别开关」双重控制；原 `log(String, Object...)` 保留（不带类别，只受全局开关，用于 Sable 不可用等必须始终可见的一次性诊断）。
3. **`debug/DebugCommand.java` 新增 `log` 子命令**：`/cosmonauticsroll debug log <类别> on|off|status`（未知类别列出可用项）、`/cosmonauticsroll debug log status`（列出全部类别开关状态）。region 高度覆盖子命令（补丁2）保持兼容。
4. **调用点归类**：RegionDebugTicker（区域→region、脚部检测→foot）、FootSurfaceResolver 采样详情（→foot）、RotationTicker（进入/离开/防抖/current/恢复竖直/reset→rotation、楼梯 progress 事件→stair）；SableSubLevelDetector 一次性警告保持无类别（始终显示）。

### 验证状态

- 纯逻辑（Debug 类别集合）get_diagnostics 仅 slf4j `info` 假阳性；DebugCommand / 调用点仅已知 MC/Brigadier 假阳性，以 Actions 编译为准。
- 未 push 验证；预期 compileJava 通过 + runLogicTests 157+ 全部通过（本次无逻辑测试改动）。

### 待办

1. 用户 commit/push → Actions 验证。
2. 低空验收时可自由选择日志：如 `/cosmonauticsroll debug log foot off` + `/cosmonauticsroll debug log rotation off` 关掉刷屏，只看 `/cosmonauticsroll debug log stair on` 的楼梯进度；验收完 `debug log <类别> on` 恢复或全局 `debug off`。

---

## 2026-08-11（阶段 5 补丁3：楼梯日志刷屏修复 + 进度连续化）

### 本次操作内容

1. **背景**：用户游戏内反馈——日志刷屏难看清；能识别原版楼梯（3.4-1 通过）；progress 无明显连续增大、相关日志过少。根因：① `楼梯：` 日志在 `StairSurfaceResolver` 内部**每 tick 输出**（有楼梯命中就打印，每秒 20 条）；② 楼梯采样沿用普通 5 点矩形布局（progress 粒度 0.2），且玩家走一个台阶仅约 2~3 tick，progress 瞬间 0→1，0.5s 一次的旋转日志采样时已到顶。
2. **密集采样**：`FootSamplingLayout` 新增 `stairGrid()`（3 列 × 5 行 = 15 点，半宽 0.25 × 半深 0.15），progress 粒度 1/15 ≈ 0.067；`StairSurfaceResolver.detect` 改用该布局。
3. **日志去刷屏 + 变化事件**：删除 `StairSurfaceResolver` 内部每 tick 的 `楼梯：` 日志与「墙面优先」日志；改由 `RotationTicker` 统一输出——**progress 变化事件日志**（相对上次输出变化 ≥ 0.05 才打印，稳定站立零输出，上楼时完整记录 0→1 序列；离开楼梯重置基线）。
4. **防抖调灵敏**：`SmoothStandingRotation.STAIR_PROGRESS_DEAD_ZONE` 0.05 → 0.03（约半个采样粒度）——目标跟随灵敏（PRD 3.4-3 连续调整角度），视觉平滑由平滑器（每 tick 最大 4°）保证（PRD 3.4-5）。
5. **测试**：`StairLogicTest.testSmoothStairTargetDebounce` 增加「单粒度变化（0.05 ≥ 0.03）被接受」断言（原 0.02 忽略 / 0.2 接受保留），断言数不变（仍 4 条），语义对齐新死区。

### 验证状态

- 纯逻辑文件（FootSamplingLayout / SmoothStandingRotation / StairLogicTest）get_diagnostics 零错误；MC 适配层（StairSurfaceResolver / RotationTicker）仅已知会话假阳性。
- 未 push 验证；预期 compileJava 通过 + runLogicTests 157+ 全部通过。

### 待办

1. 用户 commit/push → Actions 验证。
2. 低空游戏内验收（`debug on` + `debug region 0`）：上楼梯时看 `楼梯：progress=0.07→0.13→...→1.00` 变化事件序列 + `旋转：current=...` 平滑渐变；稳定站立时无 `楼梯：` 刷屏。

---

## 2026-08-11（阶段 5 补丁2：Debug 低空验收手段——主世界高度阈值覆盖命令）

### 本次操作内容

1. **背景**：游戏内验收发现 Y=8000 超出主世界建筑高度上限（-64~320），无法放置普通方块；物理化（Sable 子世界）楼梯走 sublevel 路径不触发楼梯 BlockState 逻辑。用户选方案 A（Debug 临时覆盖命令，低空验收）。
2. **新增 `debug/RegionDebugConfig.java`**（纯逻辑、无 MC 依赖）：静态主世界高度阈值覆盖（null = 默认 8000），setter/getter。
3. **修改 `region/OverworldAltitudeRule.java`**：`isActive` 在覆盖值非 null 时用覆盖值替代 8000（默认行为不变，不影响正常游戏；仅 Debug 验收可设）。
4. **修改 `debug/DebugCommand.java`**：新增子命令 `/cosmonauticsroll debug region <高度>`（DoubleArgumentType，仅 Debug 开启时生效，否则提示先开 debug）、`/cosmonauticsroll debug region default`（恢复 8000）、`/cosmonauticsroll debug region`（查看当前覆盖状态）。
5. **`RegionLogicTest` 新增 `testRegionDebugThresholdOverride`**（7 断言，finally 恢复全局状态）：默认低空不启用；覆盖 0 后 y=100/0 启用、y=-1 不启用；下界约束不变；恢复默认后低空不启用、y=8000 启用。RegionLogicTest 总 50→57 断言，LogicTestSuite 总 157+。
6. **文档同步**：CHECKLIST.md 头部 Debug 功能说明新增区域高度覆盖条目；DEVELOPMENT.md 阶段 5 实现说明与当前状态补充低空验收手段（`debug on` + `debug region 0`，验收完 `region default` 恢复）。

### 验证状态

- 纯逻辑文件（RegionDebugConfig / OverworldAltitudeRule / RegionLogicTest）get_diagnostics 零错误；DebugCommand 仅已知会话假阳性（Brigadier/MC 符号），以 Actions 编译为准。
- 未 push 验证；预期 compileJava 通过 + runLogicTests 157+ 全部通过。

### 待办

1. 用户 commit/push → Actions 验证。
2. 低空游戏内验收（`/cosmonauticsroll debug on` + `/cosmonauticsroll debug region 0`）：原版/模组楼梯、上楼角度连续、楼梯边缘不抖、普通方块/半砖/活板门不误判；验收完 `/cosmonauticsroll debug region default` 恢复。

---

## 2026-08-11（阶段 5 补丁1：Actions 两轮修复——import 缺失 + 测试用例 bug）

### 本次操作内容

1. **补丁1a（compileJava 失败）**：`StairBlockQuery.java:70` `cannot find symbol: Direction`——此前清理 `isOnSlope` 未用变量时误删了 `import net.minecraft.core.Direction;`，但 `info(BlockState)` 仍使用 `Direction`。已恢复 import（第 7 行）。
2. **补丁1b（runLogicTests 1 失败）**：`StairLogicTest.testResolverFloorToStairRamp` 的 `[FAIL] 无斜面 → 0`——测试自身 bug：i=0 时 5 个采样点都写成 `(onSlope=false, onStep=false)`（完全未命中楼梯），`StairStandingResolver.resolve` 正确返回 null（无楼梯命中 → 保持），而测试期望 progress=0。已修正为「全部命中水平台阶」（`onStep=true`，语义 = 站在楼梯最底部台阶 → progress 0）；其余 i=1..5 保持「i 个斜面 + 5-i 个台阶」递增。

### 验证状态

- 纯逻辑测试文件 get_diagnostics 零错误；生产代码本次仅恢复一行 import。
- Actions 二跑结果：compileJava 通过（import 修复生效）+ 阶段 2/3/4 测试全过（50/46/35）+ 阶段 5 仅上述 1 处测试用例 bug 失败（生产逻辑未报错，28/29 断言通过，失败项确认为测试数据问题）。
- 修复后预期：runLogicTests 150+ 全部通过。

### 待办

1. 用户 commit/push → Actions（预期编译 OK + runLogicTests 150+ 全部通过）。
2. 游戏内验收（S+D）：原版/模组楼梯、上楼角度连续、楼梯边缘不抖、普通方块/半砖/活板门不误判。

---

## 2026-08-11（阶段 5 实现：楼梯旋转核心 + 检测链接入 + 逻辑测试）

### 本次操作内容

1. **新增纯逻辑楼梯模型**（无 MC 依赖，设备 VM 可测试）：
   - `api/detect/StairInfo.java`：楼梯识别信息（朝向 Facing 四向 + 半部 Half TOP/BOTTOM），`ascentDirection()` 返回斜面上升方向（水平单位向量）。
   - `api/detect/StairProgress.java`：行走进度（0~1，clamp）+ `standingDirection()`——身体「上」方向 = 竖直向斜面方向倾斜 `progress × 45°`（PRD 3.4-2/3.4-3 连续调整角度）；progress=0 竖直、0.5 倾斜 22.5°、1 倾斜 45°。
   - `detect/StairStandingResolver.java`：由「每采样点是否命中斜面/台阶 + 朝向」解析进度——**进度 = 斜面命中数 / 全部采样点数**（从平面走上楼梯时进度从 0 连续升到 1，而非只按楼梯命中点跳变）；多朝向冲突 → 返回 null（保持当前方向，防抖，PRD 3.4-5）。

2. **游戏内楼梯适配层**：
   - `detect/StairBlockQuery.java`：`isStair`（原版 `StairBlock` instanceof，兼容继承原版楼梯行为的模组方块；或注册名含 `stair` + 标准三属性 shape/facing/half，两端通用字符串判断，PRD 3.4-1）+ `info`（解析朝向/半部）+ `isOnSlope`（按 45° 斜面碰撞形状判定采样点是否在斜面上，容差覆盖 0.05 采样下沉；转角楼梯保守不按斜面处理）+ `stepTopY`。半砖/活板门等非楼梯方块不识别（PRD 3.4 验收）。
   - `detect/StairSurfaceResolver.java`：脚底矩形 5 点逐个判定斜面/台阶；**墙面优先**——非楼梯采样点命中水平法线（进入墙面）时返回 null 走普通表面路径（PRD 3.4-4 区分上楼与进墙面）；调试日志 `楼梯：facing=... progress=... slopeHits=.../... target=...`。

3. **检测链接入**：`FootSurfaceResolver.resolve` 静态方块兜底路径先做楼梯识别（source=stair，`Resolved` 新增 `stair` 字段携带 `StairProgress`），无楼梯命中再走普通表面合并（普通完整方块仍用普通表面判断，PRD 3.4 验收）。

4. **旋转层楼梯支持**：`SmoothStandingRotation` 新增 `setStairTarget(StairProgress)`——**楼梯边缘进度防抖**（进度变化 < 0.05 不更新目标，PRD 3.4-5；行走速度约 0.2 格/tick、进度变化约 0.1+/tick，0.05 只滤除边缘单 tick 噪声）+ reset 清除防抖状态；`RotationTicker` 按 `resolved.stair != null` 走楼梯目标，否则走普通表面。

5. **逻辑测试 `StairLogicTest`**（11 用例 30+ 断言）：朝向/上升方向、站立方向连续倾斜（0/22.5°/45°/单调）、全台阶 progress=0、部分斜面 progress=0.4、全斜面 progress=1、平面→楼梯进度单调连续、多朝向冲突防抖、无楼梯返回 null、楼梯边缘进度防抖（微小变化忽略/明显变化接受）、平滑过渡不跳变、reset 清除楼梯状态；注册进 `LogicTestSuite`（总 150+ 断言）。

### 验证状态

- 纯逻辑文件（StairInfo/StairProgress/StairStandingResolver/StairLogicTest）get_diagnostics 零错误；MC 适配层（StairBlockQuery/StairSurfaceResolver/FootSurfaceResolver/RotationTicker）仅有已知会话索引假阳性（net.minecraft 无法解析，所有 MC 文件一致），以 GitHub Actions 编译为准。
- **本设备不编译不运行**（规则）；等待用户 push 后 Actions 验证（compileJava + runLogicTests）。

### 待办

1. 用户 commit/push → Actions（预期编译 OK + runLogicTests 150+ 全部通过）。
2. 游戏内验收（S+D）：原版/模组楼梯正常行走（source=stair，`楼梯：progress=...` 连续变化）、上楼角度连续无突然翻转、普通完整方块仍普通表面判断、半砖/活板门不当作楼梯（D 模式日志确认）。
3. 验收通过后更新 CHECKLIST.md 阶段 5 分组（3.4-1~6 + 验收）。

---

## 2026-08-11（文档整理：PRD/DEVELOPMENT/CHECKLIST 一致性同步）

### 本次操作内容

1. **PRD.md**：3.2（脚部表面检测）与 3.3（平滑旋转）两组功能需求勾选状态从 `[ ]` 同步为 `[x]`（阶段 3、阶段 4 已验收通过，与 CHECKLIST.md 一致）；3.4-3.7 与第 5 节验收标准保持 `[ ]`（未实现/阶段 9 复查）。
2. **DEVELOPMENT.md**：
   - 阶段 3 段落：目标 5 项、检查 4 项勾选状态同步为 `[x]`（阶段 3 已完成）；出口条件更新（用户确认并 commit 已完成）；2026-08-09 补丁6 排查条目补「已解决」标注。
   - 第 9 节当前进度：**删除过时的「⚠️ 待排查（2026-08-09 晚）Sable 未生效」整段**（阶段 3 早已验收通过，该段为历史排查计划，造成文档自相矛盾）。
   - 10.2-10.6 节：阶段 3/4 状态更新为已验收通过（含 S 模式随阶段 6 补验说明），阶段 5-7 未开始。
3. **CHECKLIST.md**：
   - 阶段 3 验证记录「已知边界说明」第一条（Sable 路径只返回单一方向盲区）补「已解决」标注（阶段 4 已实现多方向收集，2026-08-11 游戏内墙角确认 MULTIPLE）。
   - 第 9 节全局验收表加覆盖说明：明确 5-1/5-2/5-3/5-5/5-6/5-7（D）/5-12（D）/5-18（D）/7-1/7-2/7-5 已由阶段 2/3/4 验收覆盖，其余待对应阶段，阶段 9 统一复查勾选（不提前改状态标记）。
4. **无代码改动**（纯文档整理；PROGRESS.md / OPERATION_LOG.md 顶部已有最新记录，本次仅补本条目）。

### 验证状态

- 纯文档变更，无编译/测试要求（规则：本设备不编译不运行）。

### 待办

1. 用户 commit 本次文档整理（可与阶段 5 一起提交）。
2. 确认后进入阶段 5（PRD 3.4 楼梯旋转）。

---

## 2026-08-11（阶段 4 收尾：游戏内验收通过，文档状态更新）

### 本次操作内容

1. **确认阶段 4 验收依据**：用户游戏内重测（最新 Artifact jar）：
   - 站侧立物理化木板：`旋转：current=(0.000,1.000,0.000) target=(0.985,0.060,0.158)` → current 每 0.5s 逐步渐变至目标，**平滑过渡可见，无跳变**（3.3-1 验收通过）；
   - 跳开悬空：`result=NONE`，target 保持最后方向（3.3-4 快速离开不跳回竖直）；
   - 降回 y<8000：`旋转 << 离开` 后 current 逐步回到 `(0.000,1.000,0.000)`（3.3-7 恢复竖直平滑）；
   - 物理化墙角：`result=MULTIPLE source=sublevel`，方向保持（3.3-3 防抖；**Sable 多方向盲区解决**）。
   - 用户确认「确实都有了」→ 验收通过。
2. **补丁1 修复记录（补记）**：`阶段4(补丁1)` commit——Actions 首跑 compileJava 失败：`RotationTicker` 调用 `state.rotation.target()` 但 `SmoothStandingRotation` 只有 `current()` 无 `target()`（3 处 `cannot find symbol: method target()`）；修复：SmoothStandingRotation 补充 `target()` 委托方法（get_diagnostics 零错误），再跑 Actions 通过（编译 OK + runLogicTests 126+）。
3. **CHECKLIST.md 阶段 4 分组更新**：3.3-1~7 + 验收全部 `[x]`，补验证记录（逻辑测试覆盖明细 + 游戏内验收步骤表 + 差异说明：S 模式身体实际旋转随阶段 6 补验；DABR 叠加顺序已文档化，本阶段不修改玩家旋转值故不覆盖 DABR）。
4. **PROGRESS.md**：顶部新增 2026-08-11（阶段 4 游戏内验收通过）条目。
5. **DEVELOPMENT.md**：阶段 4「当前状态」改为「阶段 4 完成（验收通过）」；第 9 节当前进度同步更新。
6. **无代码改动**（纯文档收尾；补丁1 代码修复已在用户 commit 中）。

### 验证状态

- 纯文档变更，无编译/测试要求（规则：本设备不编译不运行）。
- 阶段 4 验收通过（用户游戏内验证 + Actions 编译/逻辑测试 126+ 通过）。

### 待办

1. 用户 commit 本次文档更新（CHECKLIST / PROGRESS / DEVELOPMENT / OPERATION_LOG 多条）。
2. 确认后进入阶段 5（PRD 3.4 楼梯旋转）：识别原版/模组楼梯、按碰撞形状判断行走进度、连续调整身体角度、防抖、上下楼/倒退/横向移动。

---

## 2026-08-11（阶段 4 实现：平滑旋转核心 + 组合检测共享化 + Sable 多方向增强）

### 本次操作内容

1. **确认阶段 4 启动条件**：Git HEAD = `阶段3(收尾完成)` 210be874（阶段 3 已由用户 commit 收尾），CHECKLIST 阶段 4 分组（PRD 3.3 平滑旋转 8 项）就绪 → 开始阶段 4。

2. **新增纯逻辑平滑旋转核心**（无 MC 依赖，设备 VM 可测试）：
   - `api/rot/RotationSmoother.java`：向量 slerp 插值 + 每 tick 最大旋转角（默认 4°/tick）+ 吸附阈值；三重防抖——目标死区（与当前方向夹角 <0.25° 忽略）、切换锁（4 tick 内 >90° 方向变化忽略）、吸附（<0.5° 直接吸附）。参数可配置，含参数校验。
   - `rot/StandingDirectionState.java`：SINGLE → 更新方向；NONE/MULTIPLE/null → 保持当前（墙角不站立不跳转，快速离开表面不瞬间跳回）。
   - `rot/SmoothStandingRotation.java`：合成器（setTarget / update / leaveRegion 平滑恢复竖直 / reset 立即竖直）。

3. **游戏内应用 `rot/RotationTicker.java`**：无条件注册（正式逻辑，与 Debug 观察解耦）；每 tick 每玩家「RegionStateMachine（复用阶段 2）→ FootSurfaceResolver 组合检测 → 平滑旋转」；离开区域 `leaveRegion()` 平滑恢复竖直；维度切换/死亡重生/登出清理（复用阶段 2 事件时机）；调试开启时输出 `旋转：current=... target=... result=... source=...`（每 0.5 秒）与恢复竖直日志。

4. **组合检测共享化 `detect/FootSurfaceResolver.java`**：Sable 子世界优先 + 静态方块兜底，返回 result + source（sublevel/block/none）；`RegionDebugTicker.logFootDetection` 重构复用（删除重复代码，日志格式不变）。

5. **Sable 多方向增强 `SableSubLevelDetector`**：新增 `detectStandingDirections(Entity)`——收集脚底薄片内**所有**相交子世界方向（原只取第一个），物理化墙角现可合并为 MULTIPLE（解决阶段 3 已知盲区）；原 `detectStandingDirection` 保留兼容。

6. **逻辑测试 `RotationLogicTest.java`**（8 用例 30+ 断言）：不瞬间跳变、地面→墙面平滑约 90°、墙面→天花板过渡、边缘防抖（切换锁/死区/MULTIPLE 保持）、快速离开表面保持方向、离开区域平滑恢复竖直、重置、平滑器数学（slerp/夹角/参数校验）；注册进 `LogicTestSuite`（总 126+ 断言）。

7. **主类注册**：`CosmonauticsRoll` 构造器新增 `RotationTicker.register()`。

8. **文档同步**（规则 11）：DEVELOPMENT.md 阶段 4 段落（目标/实现说明/检查/测试说明/当前状态）+ 第 9 节当前进度；PROGRESS.md 顶部新增阶段 4 实现条目；CHECKLIST.md 阶段 4 分组保持 `[ ]`（未验收不勾选，收尾时更新）。

### 验证状态

- 本设备不编译不运行（规则，MC 代码）；纯逻辑新文件（RotationSmoother / StandingDirectionState / SmoothStandingRotation / RotationLogicTest）`get_diagnostics` 无错误；MC 相关文件（RotationTicker / FootSurfaceResolver / SableSubLevelDetector / RegionDebugTicker / CosmonauticsRoll）仅剩 net.minecraft 符号假阳性（已知，以 Actions 编译为准）。
- 已修复一处真实错误：`FootSurfaceResolver` 初版 `new SableSubLevelDetector()`（构造器为 private，全静态方法）→ 改为直接静态调用；清理未使用 import。
- 待 Actions：编译 + runLogicTests（预期 126+ 全部通过）。

### 待办

1. 用户 commit/push → Actions 验证。
2. 游戏内验收（D 模式日志为主）：平滑过渡、边缘防抖（MULTIPLE）、离开区域恢复竖直；S 模式（身体实际旋转）随阶段 6 补验。
3. 验收通过后更新 CHECKLIST 阶段 4 分组 + PROGRESS/DEVELOPMENT 收尾，确认后进入阶段 5。

---

## 2026-08-10（阶段 3 收尾：文档状态更新，验收通过）

### 本次操作内容

1. **确认阶段 3 验收依据**：用户游戏内重测（最新 Artifact jar + 双级 Sable 检测）——水平木板 `SINGLE((-0.001,1.000,-0.000)) source=sublevel` ✔；侧立木板（侧面朝上）`SINGLE(0.985,0.060,0.158) source=sublevel` → **方向跟随表面旋转验收通过** ✔；脚部采样 block=air/void_air、collisionEmpty=true；无一次性警告。用户选 A 方案收尾（Sable 多方向盲区记录为已知项，随阶段 4 增强）。
2. **CHECKLIST.md 阶段 3 分组更新**：3.2-1~7 + 验收全部 `[x]`，补验证记录表（逻辑测试覆盖明细 + 游戏内验收步骤表 + 已知边界说明：Sable 路径单方向盲区、楼梯斜面阶段 5 处理）。
3. **PROGRESS.md**：新增 2026-08-10（阶段 3 游戏内验收通过）条目，阶段 0/1/2/3 全部完成。
4. **DEVELOPMENT.md**：阶段 3「当前状态」改为「阶段 3 完成（验收通过）」，记录验证结果与已知盲区。
5. **无代码改动**（纯文档收尾）。

### 验证状态

- 纯文档变更，无编译/测试要求（规则：本设备不编译不运行）。
- 阶段 3 验收通过（用户游戏内验证 + Actions 96/96 逻辑测试）。

### 待办

1. 用户 commit 本次文档更新（CHECKLIST / PROGRESS / DEVELOPMENT 规则 11 / OPERATION_LOG 多条）。
2. 确认后进入阶段 4（PRD 3.3 平滑旋转）：站立方向平滑过渡、防抖、离开区域恢复竖直、与 DABR 翻滚叠加顺序；含 Sable 路径多方向检测增强（已知盲区）。

---

## 2026-08-10（补充开发规则：操作日志强制记录）

### 本次操作内容

1. **DEVELOPMENT.md 开发规则新增第 11 条**：每次 AI 实际操作（编辑了哪些文件、做了什么改动、验证结果）都必须写入 OPERATION_LOG.md（按日期倒序新增条目，不遗漏任何一次操作）；PROGRESS.md 与 DEVELOPMENT.md 的阶段状态同步更新。
   - 背景：原规则 1–10 中无此条（OPERATION_LOG.md 头部仅有说明性文字），用户要求将其固化为正式规则。
2. 本次更新本身即为该规则的首条执行记录。

### 验证状态

- 纯文档变更，无编译/测试要求。

---

## 2026-08-10（阶段 3 游戏内验收：双级 Sable 检测验证通过）

### 本次操作内容

1. **确认 Git 状态（只读，不执行 Git 操作）**：
   - `.git/logs/HEAD` 与 `refs/heads/main` 均为 `1cd5cbaf`（提交「阶段3(7)」，runzhiyifengyu，1786175159 +0800），位于「阶段3(补丁6)」6f6f29e 之后。
   - 结论：双级 Sable 检测改动已 commit，本地工作区与提交一致，无未提交代码改动；push 与 Actions 六跑由用户执行。
2. **用户游戏内重测结果（最新 Artifact jar，物理化木板）**：
   - **水平木板**：`脚部检测：result=SINGLE((-0.001, 1.000, -0.000)) source=sublevel`，`bodyUp=(0.000,1.000,0.000)`——竖直方向正确。
   - **侧立木板（侧面朝上）**：`脚部检测：result=SINGLE(0.985, 0.060, 0.158) source=sublevel`，`bodyUp=(0.000,1.000,0.000)`——**法线跟随表面方向（y 分量≈0.06，近似水平），方向跟随验证通过**，即 3.2 验收核心项达成。
   - 脚部采样点：`block=air/void_air`、`collisionEmpty=true`——符合预期（物理化方块不是普通方块，全靠 sublevel 路径命中）。
   - 所贴日志中未见一次性警告（Sable 不可用 / 部分 API 不可用 / getTrackingSubLevel 返回 null / 包围盒查询未命中），双级路径正常。
3. **未做代码改动**（本次仅更新本日志）。

### 验证状态

- 本设备不编译不运行（规则），未做本地编译。
- 阶段 3 核心验收（方向跟随）**已通过**；剩余可选补测：墙角多方向表面（期望 MULTIPLE）、头部/身体其他部位接触（期望不触发站立）。

### 待办

1. 用户补测墙角（`result=MULTIPLE`）与头部接触（不触发）两项（可选，不阻塞验收）。
2. 确认后更新 CHECKLIST.md 阶段 3 分组（3.2-1~7 + 验收全 `[x]`）与 PROGRESS.md / DEVELOPMENT.md 阶段状态 → 阶段 3 收尾。
3. 进入阶段 4（PRD 3.3 平滑旋转）。

---

## 2026-08-10（阶段 3 排查与加固）

### 本次操作内容

1. **新增文档文件**：
   - 新建 `CHECKLIST.md`（PRD 检查清单，从 DEVELOPMENT.md 第 10 节独立出来，作为唯一维护位置）。
   - 新建 `OPERATION_LOG.md`（本文件，记录 AI 每次实际操作）。
   - 修改 `DEVELOPMENT.md`：第 10 节改为指向 `CHECKLIST.md` 的简短说明（原检查清单内容已迁移）。
   - 修改 `PRD.md`：文件头增加 `CHECKLIST.md` 与 `OPERATION_LOG.md` 的引用说明。

2. **SableSubLevelDetector 升级为双级反射检测**（`src/main/java/dev/cosmonauticsroll/detect/SableSubLevelDetector.java`）：
   - 方案 A：`Sable.HELPER.getTrackingSubLevel(entity)` 主查（Sable 碰撞时记录的当前所在子世界），方向取 `subLevel.logicalPose().orientation()` 旋转 (0,1,0)。
   - 方案 B（新增兜底）：`Sable.HELPER.getAllIntersecting(level, 脚底薄片包围盒)` 世界坐标查询相交子世界，取第一个可读方向；解决「子世界存在但未被 tracking 记录」的情况。
   - 两条路径各自 try/catch，旧版 Sable 缺任一 API 时仍可用另一条（兼容 2.0.3 之前的版本）。
   - 新增一次性诊断日志：`Sable 不可用` / `部分 API 不可用` / `getTrackingSubLevel 返回 null` / `包围盒查询未命中`。

3. **对照 Sable 源码（github.com/ryanhcode/sable main）逐一核实反射签名**：
   - `Sable.HELPER`、`ActiveSableCompanion.getTrackingSubLevel(Entity)`、`getAllIntersecting(Level, BoundingBox3dc)`、`SubLevel.logicalPose()`、`BoundingBox3d` 六参数构造器均存在。
   - 服务端 `getAllIntersecting` 按子世界全局包围盒暴力匹配，世界坐标语义成立（Sable 自身 `wakeUpObjectsAt` 即如此调用）。
   - 排除 `getContaining(Entity)`：Sable plot 网格原点在约 ±2048 万格处，玩家正常坐标换算后落在网格外恒为 null。
   - Sable NeoForge 1.21.1 最新版确认为 2.0.3（Modrinth，2026-06-17 发布）。

4. **DEVELOPMENT.md 阶段 3 记录同步更新**（实现说明、排查记录、当前状态）。

### 验证状态

- 本设备不编译不运行（规则），未做任何本地验证。
- 本次改动**未 commit、未跑 Actions**，待用户 push 后触发 Actions 六跑（预期编译 OK + runLogicTests 96/96）。

### 当前进度（截至本日志）

- 阶段 0/1/2：已完成（PRD 3.1 验收通过）。
- 阶段 3：实现完成 + Actions 五跑通过（编译 OK + runLogicTests 96/96）；**游戏内验收未通过**——站物理化木板（y=8000）`脚部检测` 仍 NONE。已做双级反射加固 + 诊断日志，**待用户 push 触发六跑后重测**。

### 待办（用户操作）

1. commit/push 本次改动 → Actions 六跑（预期编译 OK + 96/96）。
2. 确认测试机 jar 为最新 Artifact（上次测出 NONE 很可能用了旧 jar——补丁6 有两个同名 commit）。
3. 游戏内重测（站物理化木板 y=8000，`/cosmonauticsroll debug on`），收集日志：
   - `脚部检测` 行：result= / source=（sublevel 还是 block）；
   - 一次性警告：`Sable 不可用` / `Sable 部分 API 不可用` / `getTrackingSubLevel 返回 null` / `包围盒查询未命中`；
   - `脚部采样` 的 block= 是否 void_air。
4. 若仍 NONE 且两条路径日志均未命中 → 查 `/sable` 状态与 Cosmonautics 内嵌 Sable 版本。

---

## 2026-08-09（阶段 3 实现与 Actions 验证）

### 本次操作内容

1. **阶段 3 实现完成**：
   - `api/detect/`：Vec3d（3D 向量）、SurfaceNormal（六轴向枚举）、SurfaceQuery（函数式接口）、FootSurfaceResult（NONE/SINGLE/MULTIPLE）。
   - `detect/`：FootSamplingLayout（脚底矩形 5 点 / 单点）、FootSurfaceDetector（采样 + 下沉 0.1 + 合并法线）、LevelSurfaceQuery（BlockState.getCollisionShape → 最近面法线）、SableSubLevelDetector（反射方案 A）。
   - `region/RegionDebugTicker`：组合检测（Sable 优先 source=sublevel，方块兜底 source=block）+ 脚底采样点详情日志。
   - `logictest/FootSurfaceLogicTest`（46 断言）+ LogicTestSuite 总计 96 断言。
2. **Actions 五跑验证通过**（编译成功 + runLogicTests 96/96）：
   - 一跑：`Level.getBlockCollisionShape(BlockPos)` 不存在 → 改为 `BlockState.getCollisionShape(BlockGetter, BlockPos)`。
   - 二跑：83/83 通过。
   - 三跑：Sable compileOnly 传递依赖 veil 无法解析 → 删除 compileOnly，localRuntime 设 `transitive=false`。
   - 四跑：浮点严格相等断言失败 → 改为容差比较。
   - 五跑：96/96 全部通过。
3. **Sable 依赖策略**：反射访问（无 compileOnly），仅 `localRuntime("dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.3") { transitive = false }`；neoforge.mods.toml 声明 sable optional `[2.0.3,)`。

### 验证状态

- GitHub Actions 编译成功 + runLogicTests 96/96。
- 游戏内验收：站物理化木板（y=8000，void_air）脚部检测仍 NONE —— 待排查（进入 2026-08-10 工作）。

---

## 2026-08-07 ~ 08-08（阶段 1、2）

### 阶段 2（验收完成）

- 区域状态机 `RegionStateMachine` + `RegionRules`（主世界 Y>=8000 / rocketnautics:deep_space → ACTIVE）。
- `RegionLogicTest` 50 断言，Actions 通过。
- 游戏内验收通过（含 debug 命令、维度切换/死亡重生状态清理、心跳日志），用户 commit「阶段2(验收完成)」。

### 阶段 1（验收完成）

- 模组元数据（mod_id=cosmonautics_roll，version=0.1.0，group=dev.cosmonauticsroll）。
- 最小依赖配置（仅 NeoForge 21.1.235）。
- GitHub Actions：JDK 21 + `./gradlew build` + 上传 mod JAR Artifact；修复 `./gradlew: Permission denied`（workflow 先 chmod +x，.gitattributes 固定 LF）。
- `.gitignore` 排除 `.platform/` 等本地文件。
- 用户 commit「阶段1(补丁2)」后验收通过。

---

## 2026-08-06 及之前（阶段 0）

- 项目模板与构建方式确认（NeoForge 1.21.1 + GitHub Actions 编译）。
- 外部依赖确认：Do a Barrel Roll（3.7.3+1.21-neoforge）、Cosmonautics/Rocketnautics（`rocketnautics:deep_space`）、Aeronautics（HandleBlock 铁把手）、Sable（子世界引擎）。
- 决策：不依赖 Gravity API（无 NeoForge 1.21.1 版本）。
- 用户 commit「阶段1（initial）」。
