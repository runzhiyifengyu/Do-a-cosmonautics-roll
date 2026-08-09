# Do-a-Cosmonautics-roll 制作进度日志

> 本文件按日期倒序记录开发进度与关键事件。开发规则见 [DEVELOPMENT.md](DEVELOPMENT.md)，产品需求见 [PRD.md](PRD.md)。
> Git 操作（commit/push）全部由用户执行；本日志随代码一起由用户提交。

---

## 2026-08-11（阶段 5 补丁2：Debug 低空验收手段）

### 完成
- **新增主世界高度阈值覆盖（D 模式验收手段）**：
  - `debug/RegionDebugConfig`：纯逻辑静态配置（主世界高度阈值覆盖，null = 默认 8000）。
  - `region/OverworldAltitudeRule`：读取覆盖值（默认行为不变）。
  - `debug/DebugCommand`：新增 `/cosmonauticsroll debug region <高度>|default|无参`（仅 debug 开启时生效）。
  - 背景：Y=8000 超出建筑高度上限（320），高空无法放方块验收楼梯；物理化楼梯走 sublevel 路径不触发楼梯 BlockState 逻辑 → 需低空搭普通楼梯验收。
- **逻辑测试**：`RegionLogicTest` 新增覆盖用例（7 断言，finally 恢复全局状态），总 57 断言；LogicTestSuite 总 157+。

### 状态
- **阶段 5：实现完成 + 低空验收手段就绪，待 Actions 验证与游戏内验收**。

### 待办（用户操作）
1. commit/push → Actions（预期编译 OK + runLogicTests 157+ 全部通过）。
2. 低空游戏内验收：`/cosmonauticsroll debug on` + `/cosmonauticsroll debug region 0` → 搭普通原版楼梯上行看 `source=stair` + `progress` 连续变化；验收完 `/cosmonauticsroll debug region default` 恢复 8000。

---

## 2026-08-11（阶段 5 实现完成，待 Actions 验证与游戏内验收）

### 完成
- **楼梯旋转核心（纯逻辑，可设备 VM 测试）**：
  - `api/detect/StairInfo`：楼梯识别信息（朝向 + 半部 + 斜面上升方向）。
  - `api/detect/StairProgress`：行走进度 0~1 + 站立方向（竖直向斜面方向倾斜 `progress × 45°`，连续非轴向）。
  - `detect/StairStandingResolver`：进度 = 斜面命中采样数 / 全部采样数（平面→楼梯进度连续 0→1）；多朝向冲突 → null（保持方向防抖）。
- **游戏内楼梯适配层**：
  - `detect/StairBlockQuery`：`isStair`（原版 StairBlock instanceof + 注册名含 stair + 三属性）、`info`、`isOnSlope`（45° 斜面碰撞形状判定，容差覆盖采样下沉）、`stepTopY`；半砖/活板门不识别。
  - `detect/StairSurfaceResolver`：脚底 5 点斜面/台阶判定 + 墙面优先（进入墙面走普通路径，PRD 3.4-4）+ 调试日志 `楼梯：facing=... progress=...`。
- **检测链接入**：`FootSurfaceResolver` 静态方块兜底先楼梯（source=stair，Resolved 带 StairProgress）再普通表面（普通完整方块仍普通判断）。
- **旋转层**：`SmoothStandingRotation.setStairTarget`（楼梯边缘进度防抖 <0.05 不更新 + reset 清除）；`RotationTicker` 按 stair 分支喂目标。
- **逻辑测试 `StairLogicTest`**（11 用例 30+ 断言），LogicTestSuite 总 150+ 断言。

### 状态
- 阶段 0/1/2/3/4：全部完成（PRD 3.1 / 3.2 / 3.3 验收通过）。
- **阶段 5：实现完成，待 Actions 验证（编译 + runLogicTests）与用户游戏内验收**。

### 待办（用户操作）
1. commit/push 本次改动 → Actions（预期编译 OK + runLogicTests 150+ 全部通过）。
2. 游戏内验收（S+D，`/cosmonauticsroll debug on`）：
   - 原版/模组楼梯上行：`旋转：... source=stair` + `楼梯：progress=...` 连续变化，角度连续无突然翻转（3.4-2/3.4-3）；
   - 楼梯边缘：进度微小波动不引起方向抖动（3.4-5）；
   - 普通完整方块仍普通表面判断；半砖/活板门不当作楼梯（3.4 验收，D 模式日志确认）；
   - 上下楼/倒退/横向移动（3.4-6）。
3. 验收通过后更新 CHECKLIST.md 阶段 5 分组（3.4-1~6 + 验收）。

---

## 2026-08-11（阶段 4 游戏内验收通过）

### 完成
- **阶段 4（平滑旋转，PRD 3.3）游戏内验收通过**：
  - 用户游戏内观察 `[Debug]` 日志（`/cosmonauticsroll debug on`，物理化木板，玩家身体不转、看 `current` 数值渐变）：
    - **平滑过渡**：站侧立物理化木板，`target=(0.985,0.060,0.158)` 时 `current` 每 0.5s 逐步渐变至目标，无跳变（3.3-1 验收）；
    - **快速离开**：跳开悬空 `result=NONE`，target 保持最后方向，不瞬间跳回竖直（3.3-4）；
    - **恢复竖直**：降回 y<8000，`旋转 << 离开` 后 current 逐步回到 `(0.000,1.000,0.000)`（3.3-7 验收）；
    - **边缘防抖**：物理化墙角 `result=MULTIPLE source=sublevel`，方向保持（3.3-3，**Sable 多方向盲区已解决**）。
  - Actions 验证：编译 OK + runLogicTests 126+ 全部通过。
  - CHECKLIST.md 阶段 4 分组（3.3-1~7 + 验收）全部 `[x]`，验证记录已补（含差异说明：S 模式身体实际旋转随阶段 6 补验；DABR 叠加顺序已文档化，本阶段不修改玩家旋转值故不覆盖 DABR）。
- **补丁修复记录**：`阶段4(补丁1)`——`SmoothStandingRotation` 补充 `target()` 方法（RotationTicker 日志用，首跑 compileJava 失败 3 处 `cannot find symbol: method target()`），修复后 Actions 通过。

### 状态
- 阶段 0/1/2/3/4：全部完成（PRD 3.1 / 3.2 / 3.3 验收通过）。
- **阶段 4 收尾**，等待用户 commit 后进入阶段 5（PRD 3.4 楼梯旋转）。

### 待办（用户操作）
1. commit 本次文档更新（CHECKLIST / PROGRESS / DEVELOPMENT / OPERATION_LOG，可随阶段 5 一起提交）。
2. 确认后进入阶段 5：楼梯识别（原版/模组楼梯）、行走进度、连续角度调整、防抖、上下楼/倒退/横向移动。

---

## 2026-08-11（阶段 4 实现完成，待 Actions 验证与游戏内验收）

### 完成
- **平滑旋转核心（纯逻辑，可设备 VM 测试）**：
  - `api/rot/RotationSmoother`：向量 slerp 插值 + 每 tick 最大旋转角（默认 4°/tick，约 90° 用 22 tick 平滑过渡）+ 吸附阈值；**三重防抖**（PRD 3.3-3）：目标死区（与当前夹角过小忽略）、切换锁（锁定期 4 tick 内 >90° 方向变化忽略，防边缘来回切换）、吸附（夹角 <0.5° 直接吸附消除残余抖动）。
  - `rot/StandingDirectionState`：SINGLE → 更新站立方向；NONE/MULTIPLE → 保持（墙角不站立、不跳转；快速离开表面不瞬间跳回竖直，PRD 3.3-4）。
  - `rot/SmoothStandingRotation`：合成器——`setTarget`（检测结果→目标）/ `update`（平滑过渡）/ `leaveRegion`（**离开区域平滑恢复竖直**，PRD 3.3-7）/ `reset`（传送/死亡立即竖直）。
- **游戏内应用 `rot/RotationTicker`**（无条件注册，与调试观察解耦）：每 tick 每玩家「区域状态机（复用阶段 2）→ 脚部组合检测 → 平滑旋转」；进入区域开始平滑站立，离开区域平滑恢复竖直；维度切换/死亡重生 reset；调试开启时输出旋转过程日志（每 0.5 秒 `旋转：current=... target=... result=... source=...`）与恢复竖直日志。
- **组合检测共享化 `detect/FootSurfaceResolver`**：Sable 子世界优先 + 静态方块兜底（result + source）；**Sable 多方向增强**——`SableSubLevelDetector.detectStandingDirections` 收集脚底薄片内所有相交子世界方向并合并，物理化墙角现可产生 MULTIPLE（解决阶段 3 已知盲区）；`RegionDebugTicker` 重构复用（日志格式不变）。
- **DABR 叠加顺序约定已文档化**（阶段 6 接入落地）：本模组表面方向为基础姿态旋转，DABR 翻滚在其上叠加，不覆盖。
- **逻辑测试 `RotationLogicTest`**（8 用例 30+ 断言）：不瞬间跳变、地面→墙面平滑约 90°、墙面→天花板过渡、边缘防抖（切换锁/死区/MULTIPLE 保持）、快速离开表面保持方向、离开区域平滑恢复竖直、重置、平滑器数学。LogicTestSuite 总 126+ 断言。

### 状态
- 阶段 0/1/2/3：已完成（PRD 3.1/3.2 验收通过）。
- **阶段 4：实现完成，待 Actions 验证（编译 + runLogicTests）与用户游戏内验收**。

### 待办（用户操作）
1. commit/push 本次改动 → Actions（预期编译 OK + runLogicTests 126+ 全部通过）。
2. 游戏内验收（`/cosmonauticsroll debug on`）：
   - 平滑：站地面/墙面，观察 `旋转：current=...` 逐步过渡无跳变；
   - 防抖：墙角观察 MULTIPLE（不站立、方向保持）；
   - 恢复竖直：离开区域（降到 y<8000）观察 `旋转 << 离开` 后 current 逐步回到 (0,1,0)；
   - S 模式（玩家身体实际旋转体验）随阶段 6（DABR 接入）补验。

---

## 2026-08-10（阶段 3 游戏内验收通过）

### 完成
- **阶段 3（脚部表面检测，PRD 3.2）游戏内验收通过**：
  - 用户游戏内重测（最新 Artifact jar + 双级 Sable 检测，物理化木板 y=8000）：
    - 水平木板：`脚部检测：result=SINGLE((-0.001,1.000,-0.000)) source=sublevel`，方向竖直正确。
    - **侧立木板（侧面朝上）：`result=SINGLE(0.985,0.060,0.158) source=sublevel` → 法线跟随表面方向（近似水平），方向跟随验收通过。**
    - 脚部采样点 `block=air/void_air`、`collisionEmpty=true`（物理化方块非普通方块，靠 sublevel 路径命中，正常）；日志无一次性警告（双级路径正常）。
  - CHECKLIST.md 阶段 3 分组（3.2-1~7 + 验收）全部 `[x]`，验证记录已补。
- **已知盲区（已记录，非阻塞，随阶段 4 增强）**：Sable（物理化方块）路径当前只返回单一方向——`RegionDebugTicker` 在 `source=sublevel` 时直接取第一个命中的子世界方向，物理化墙角不产生 MULTIPLE；普通方块路径的多方向合并已有单元测试覆盖。

### 状态
- 阶段 0/1/2/3：全部完成（PRD 3.1 / 3.2 验收通过）。
- **阶段 3 收尾**，等待用户 commit 后进入阶段 4（PRD 3.3 平滑旋转）。

### 待办（用户操作）
1. commit 本次文档更新（CHECKLIST / PROGRESS / DEVELOPMENT 规则 11 / OPERATION_LOG）→ 可随阶段 4 一起提交。
2. 确认后进入阶段 4：平滑旋转（站立方向平滑过渡、防抖、离开区域恢复竖直、与 DABR 翻滚叠加顺序）。

---

## 2026-08-10（阶段 3 排查与加固）

### 完成
- **SableSubLevelDetector 升级为双级反射检测**（`src/main/java/dev/cosmonauticsroll/detect/SableSubLevelDetector.java`）：
  - 方案 A：`Sable.HELPER.getTrackingSubLevel(entity)`（Sable 碰撞时记录的当前所在子世界）主查，方向取 `subLevel.logicalPose().orientation()` 旋转 (0,1,0)。
  - 方案 B（新增兜底）：`Sable.HELPER.getAllIntersecting(level, 脚底薄片包围盒)` 世界坐标查询相交子世界，取第一个可读方向；解决「子世界存在但未被 tracking 记录」的情况。
  - 两条路径各自 try/catch，旧版 Sable 缺任一 API 时仍可用另一条（兼容 2.0.3 之前的版本）。
  - 新增一次性诊断日志：`Sable 不可用` / `部分 API 不可用` / `getTrackingSubLevel 返回 null` / `包围盒查询未命中`。
- **对照 Sable 源码（github.com/ryanhcode/sable main）逐一核实反射签名**：
  - `Sable.HELPER`、`ActiveSableCompanion.getTrackingSubLevel(Entity)`、`getAllIntersecting(Level, BoundingBox3dc)`、`SubLevel.logicalPose()`、`BoundingBox3d` 六参数构造器均存在。
  - 服务端 `getAllIntersecting` 按子世界全局包围盒暴力匹配，**世界坐标语义成立**（Sable 自身 `wakeUpObjectsAt` 即如此调用）。
  - **排除 `getContaining(Entity)`**：Sable plot 网格原点在约 ±2048 万格处，玩家正常坐标换算后落在网格外恒为 null。
  - Sable NeoForge 1.21.1 最新版确认为 **2.0.3**（Modrinth，2026-06-17 发布）。
- **DEVELOPMENT.md 阶段 3 记录同步更新**（实现说明、排查记录、当前状态）。

### 状态
- 阶段 0/1/2：已完成（PRD 3.1 验收通过）。
- 阶段 3：实现完成 + Actions 五跑通过（编译 OK + runLogicTests 96/96）；**游戏内验收未通过**——站物理化木板（y=8000）`脚部检测` 仍 NONE。
- 本次改动**未 commit、未跑 Actions**，待用户 push 后触发六跑。

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

### 完成
- **阶段 3 实现完成**：
  - `api/detect/`：Vec3d（3D 向量）、SurfaceNormal（六轴向枚举）、SurfaceQuery（函数式接口）、FootSurfaceResult（NONE/SINGLE/MULTIPLE）。
  - `detect/`：FootSamplingLayout（脚底矩形 5 点 / 单点）、FootSurfaceDetector（采样 + 下沉 0.1 + 合并法线）、LevelSurfaceQuery（BlockState.getCollisionShape → 最近面法线）、SableSubLevelDetector（反射方案 A）。
  - `region/RegionDebugTicker`：组合检测（Sable 优先 source=sublevel，方块兜底 source=block）+ 脚底采样点详情日志。
  - `logictest/FootSurfaceLogicTest`（46 断言）+ LogicTestSuite 总计 96 断言。
- **Actions 五跑验证通过**：编译成功 + runLogicTests 96/96。
  - 一跑：`Level.getBlockCollisionShape(BlockPos)` 不存在 → 改为 `BlockState.getCollisionShape(BlockGetter, BlockPos)`。
  - 二跑：83/83 通过。
  - 三跑：Sable compileOnly 传递依赖 veil 无法解析 → 删除 compileOnly，localRuntime 设 `transitive=false`。
  - 四跑：浮点严格相等断言失败 → 改为容差比较。
  - 五跑：96/96 全部通过。
- **Sable 依赖策略**：反射访问（无 compileOnly），仅 `localRuntime("dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.3") { transitive = false }`；neoforge.mods.toml 声明 sable optional `[2.0.3,)`。

### 问题发现
- 游戏内验收：站物理化木板（y=8000，`void_air`）脚部检测仍 NONE —— **待排查**（进入 2026-08-10 工作）。

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
