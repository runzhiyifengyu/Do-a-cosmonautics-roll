# Do-a-Cosmonautics-roll 开发规则与任务

> 产品目标与功能需求见 [PRD.md](PRD.md)。
> PRD 检查清单（逐项验收状态）见 [CHECKLIST.md](CHECKLIST.md)。
> AI 实际操作记录见 [OPERATION_LOG.md](OPERATION_LOG.md)。
> 本文件记录开发规则、开发分工、开发阶段、任务清单与进度记录。

## 1. 文档用途与开发环境

本文件是 `Do-a-Cosmonautics-roll` 模组第一版开发的规则与任务依据。产品需求以 `PRD.md` 为准。

开发环境：

- Minecraft：`1.21.1`
- 加载器：NeoForge
- 目标产品：CodeAssist 项目
- 最终编译方式：上传 GitHub 后由 GitHub Actions 编译
- 本设备：只进行文件编辑，不在设备上编译或运行
- 目标模式：第一版只支持单人游戏

---

## 2. 开发规则

1. 每个开发阶段必须拆成可执行的小目标。
2. 每完成一个小目标，都必须检查：
   - Java/Kotlin 编译错误
   - 依赖和映射是否正确
   - Mixin 注入点是否有效
   - 客户端与游戏逻辑是否可能冲突
   - 旋转、碰撞、视角和动画的边界情况
3. 必须认为实现中可能存在 BUG，不能只根据代码表面判断完成。
4. 当前阶段完成后，必须先进行检查和修复。
5. 不得跳过测试，不得漏过测试。任何实现都必须经过对应测试才能视为完成：
   - 本设备上：编译检查、逻辑单元测试（可在设备 VM 上运行的纯逻辑测试）
   - 远端：GitHub Actions 构建测试
   - 实际游戏：由用户在单人游戏中按阶段检查清单逐项验证
   - 任一测试未执行或未通过，该小目标不得标记完成，阶段不得收尾。
6. 检查确认没有已知问题后，等待用户确认。
7. 只有用户确认并执行 Git commit 后，才允许进入下一阶段。
8. 如果当前阶段未确认，不得提前实现下一阶段的功能。
9. 每次阶段变更都要更新本文件中的进度记录。
10. PRD 检查：每个阶段完成时，必须对照 [PRD.md](PRD.md) 中与该阶段相关的功能需求与验收标准逐项检查（见 [CHECKLIST.md](CHECKLIST.md)）。清单中对应分组全部通过（含用户实际游戏验证）才能标记该阶段完成；未通过的项必须记录差异并等待用户确认。
11. 每次 AI 实际操作（编辑了哪些文件、做了什么改动、验证结果）都必须写入 [OPERATION_LOG.md](OPERATION_LOG.md)（按日期倒序新增条目，不遗漏任何一次操作）；PROGRESS.md 与 DEVELOPMENT.md 的阶段状态同步更新。

---

## 3. 开发分工

1. 项目检查：由用户负责。用户检查项目实际结构、旁边已有的依赖文件、版本以及外部模组接口。
2. AI 协作：AI 负责把需要检查确认的事项整理成问题清单，逐项询问用户，并记录用户提供的结果；AI 不做用户已声明负责的检查。
3. Git 操作：全部由用户执行（commit、push、tag 等），AI 不执行任何 Git 操作，AI 也不代替用户确认。

---

## 4. 多人预留原则

第一版只做单人，但架构必须为多人保留空间：

1. 核心逻辑写成纯函数：输入 `Level` / `Entity` / `BlockPos`，输出状态，不直接依赖 `Minecraft.getInstance()` / `LocalPlayer` 等纯客户端类。
2. 方向重力等移动逻辑注入两端共有的方法（如 `LivingEntity.travel()`），由服务端权威，客户端复算；纯视觉部分（镜头、模型、翻滚渲染）才使用客户端注入。
3. 预留网络协议结构：使用 NeoForge `CustomPacketPayload` 定义玩家旋转状态包（pitch/yaw/roll 或四元数）。第一版不实现发送，但结构预留，多人时按 Do a Barrel Roll 的模式加同步。
4. 铁把手、维度等外部模组判断使用 BlockState + 注册名/属性字符串，两端通用。

---

## 5. 开发任务（工程类，非产品功能）

### 5.1 项目基础配置（原 PRD 3.1）

- [x] 确认 NeoForge `1.21.1` 项目结构。（NeoForge MDK 模板，含 userdev 插件与 wrapper）
- [x] 确认 Minecraft、NeoForge、Java 版本。（MC `1.21.1`，NeoForge `21.1.235`，Java 21）
- [x] 确认旁边已有的依赖模组版本和文件。（见第 9 节）
- [x] 确认 Do a Barrel Roll 的可用类、事件和 Mixin 注入点。（见第 9 节）
- [x] 确认 Cosmonautics/Rocketnautics 的维度标识。（见第 9 节）
- [x] 确认 Aeronautics 铁把手的方块、实体或状态接口。（见第 9 节）
- [x] 设计不会破坏现有模板的最小依赖配置。（仅 NeoForge 实现依赖，无外部模组编译依赖；阶段 1 已落地）

分工：本阶段的项目检查由用户负责（项目实际结构、依赖文件、外部接口），AI 负责整理问题清单逐项询问用户并记录结果。

阶段检查：

- 项目配置文件可被 GitHub Actions 识别。
- 依赖坐标、版本和本地文件来源明确（由用户检查并提供）。
- 所有待使用的外部类和接口都已从实际依赖中确认，不能凭猜测编写。

### 5.2 Git 与 GitHub Actions（原 PRD 3.9）

- [x] 添加合适的 `.gitignore`。
- [x] 排除构建输出、缓存、IDE 临时文件和本地环境文件。（含 CodeAssist `.platform/`）
- [x] 保留源码、资源、Gradle 配置和必要的依赖声明。
- [x] 配置 GitHub Actions。
- [x] Actions 使用项目实际需要的 Java 版本。（JDK 21）
- [x] Actions 执行依赖解析、编译、测试和构建任务。（`./gradlew build`）
- [x] 构建成功后上传模组 JAR 作为 Artifact。
- [x] 不在本设备上执行编译或运行。
- [x] 不把个人路径、令牌、设备信息写入仓库。
- [x] Git 操作（commit、push、tag）全部由用户执行，AI 只编辑仓库内文件内容。

验收：

- 仓库上传后 Actions 可以自动运行。
- Actions 失败时能显示明确的编译或依赖错误。
- 成功后可以下载最终 JAR。
- Git 仓库中没有构建缓存和本地敏感配置。

---

## 6. 推荐开发阶段与进度

### 阶段 0：需求和依赖确认

分工：

- 用户：检查项目实际结构、旁边已有依赖文件、版本以及外部模组接口。
- AI：把需要确认的事项整理成问题清单，逐项询问用户，并记录用户提供的检查结果。
- Git：全部由用户执行。

AI 提问清单（用户检查并回答）：

- [x] 询问用户：项目模板和构建方式是否符合预期。（用户回答：是）
- [x] 询问用户：旁边已有依赖文件及准确版本。（用户回答：源码都在 project/ 下；AI 已核查，见第 9 节）
- [x] 询问用户：Gravity API 的实际 NeoForge `1.21.1` 版本。（用户回答：不知道；AI 核查未找到 NeoForge 1.21.1 版本，已决定不依赖，见第 9 节方案结论）
- [x] 询问用户：Do a Barrel Roll 的源码接口和 Mixin 入口。（用户回答：最新 1.21.1；AI 已核查 3.7.3+1.21-neoforge，接口见第 9 节）
- [x] 询问用户：Aeronautics 铁把手的实现方式。（用户回答：在 aeronautics 源码里；AI 已核查 HandleBlock 等，见第 9 节）
- [x] 询问用户：Rocketnautics 维度注册名。（用户回答：自己查；AI 已核查 `rocketnautics:deep_space`，与 PRD 一致）

出口条件：

- AI 已逐项询问，用户已检查并逐项回答。
- 所有外部 API 均来自用户确认的实际文件或可靠源码。
- 没有依赖版本猜测。
- 用户确认并 commit 后进入阶段 1。
- 未确认前不得进入阶段 1。

当前状态：已完成。用户已确认方案并指示开始开发，进入阶段 1。

### 阶段 1：项目骨架和 GitHub Actions

目标：

- [x] 完成模组元数据。（mod_id=`cosmonautics_roll`、mod_name=`Cosmonautics Roll`、group=`dev.cosmonauticsroll`、version=`0.1.0`，neoforge.mods.toml 描述已更新）
- [x] 完成最小依赖配置。（仅 `net.neoforged:neoforge:21.1.235`，无外部模组编译依赖，符合阶段 0 结论）
- [x] 添加 GitHub Actions。（build.yml：JDK 21 + `./gradlew build` + 上传 mod JAR Artifact；2026-08-07 首跑失败 `./gradlew: Permission denied`，已修复：workflow 中先 `chmod +x gradlew` 再执行，并在 .gitattributes 固定 gradlew 为 LF 行尾）
- [x] 添加 `.gitignore`。（模板已有，补充 `.platform/` 排除 CodeAssist 本地状态）
- [x] 创建基础包结构和后续扩展接口。（`dev.cosmonauticsroll` 主类/客户端类；`api.region.RegionRule` 纯函数接口；`api.net.RollStatePayload` 预留包结构，第一版不注册不发送；2026-08-07 二跑编译失败：`CustomPacketPayload` 包名错误，已修正为 Minecraft 原生 `net.minecraft.network.protocol.common.custom.CustomPacketPayload`）
- [x] 加入 DEBUG 调试基础设施（Debug 验收模式的手段）：`debug.Debug` 调试开关 + 调试日志（`[Debug]` 前缀，仅开启时输出）；`debug.DebugCommand` 游戏内命令 `/cosmonauticsroll debug on|off|status`（0 级权限，单人可直接用）；开发运行环境 build.gradle 默认注入 `-Dcosmonauticsroll.debug=true`，生产默认关闭。

检查：

- [x] 检查配置格式。（gradle.properties / neoforge.mods.toml / build.gradle 语法与占位符一致）
- [x] 检查依赖声明。（无本地路径依赖，仅 NeoForge 实现依赖；移除示例包）
- [x] 检查 Actions YAML。（JDK 21 与项目一致；构建后上传 `build/libs/*.jar`）
- [x] 检查是否包含本地路径或敏感信息。（无个人路径/令牌；`.platform/` 已忽略）

出口条件：

- 用户上传 GitHub 后可以自行触发构建。（已验证：Actions 构建通过，产出模组 JAR Artifact）
- 用户确认并 commit。（已完成：2026-08-07 用户确认并 commit）
- 未确认前不得进入阶段 2。

当前状态：阶段 1 完成（构建验证通过，用户确认并 commit）。用户指示暂不进入阶段 2，等待用户指示后再开始。

### 阶段 2：适用区域和状态机

目标：

- [x] 实现主世界中心 Y 坐标判断。（`region.OverworldAltitudeRule`：`minecraft:overworld` + `centerY >= 8000.0`）
- [x] 实现 `rocketnautics:deep_space` 判断。（`region.DeepSpaceDimensionRule`：维度名匹配，不依赖 Y）
- [x] 实现进入、保持、离开状态。（`region.RegionStateMachine`：INACTIVE/ACTIVE + entered/left 转换事件）
- [x] 实现离开方块约 0.3 秒的计时。（`region.SurfaceContactTimer`：6 tick 宽限期，超过进入 FREE）
- [x] 实现状态清理。（`RegionStateMachine.reset()`，供死亡/重生/传送/维度切换调用，不留残留）
- [x] 实现游戏内调试观察挂接。（`region.RegionDebugTicker`：服务端每 tick 驱动状态机，进入/离开/心跳日志（心跳 5 秒一次），维度切换与死亡重生自动 reset，仅 Debug 开启时输出，用于 PRD 3.1 游戏内验收）

检查：

- [x] 检查边界值 `8000`。（逻辑测试覆盖 8000.0/7999.999/高负值）
- [x] 检查维度切换。（逻辑测试覆盖 主世界↔deep_space↔末地）
- [x] 检查死亡、重生和传送。（`reset()` 已实现并测试；游戏内事件挂钩已接入 `RegionDebugTicker`（`PlayerEvent.PlayerChangedDimensionEvent` / `PlayerEvent.Clone` / `PlayerEvent.PlayerLoggedOutEvent`），用户实际游戏验证通过）
- [x] 检查跨区时状态是否残留。（逻辑测试覆盖跨 Y=8000 边界与 reset 清理）

测试说明：

- 设备内存不足，不在设备上编译/运行。逻辑测试已写入 `src/logictest/java/dev/cosmonauticsroll/test/RegionLogicTest.java`（纯逻辑 main，独立 sourceSet，不占用 Gradle test 任务），由 GitHub Actions 的 `runLogicTests` 任务（`./gradlew build runLogicTests`）执行；实际游戏由用户按阶段检查清单验证。
- 2026-08-07 首跑：`runLogicTests` 50/50 全部通过；但 Gradle `test` 任务因 src/test 下无 JUnit 测试而失败，已将逻辑测试移到独立 sourceSet `src/logictest` 修复，`test` 任务恢复 NO-SOURCE。
- 2026-08-09 游戏内验收：用户按 PRD 3.1 清单在测试机验证（`/cosmonauticsroll debug on` + `[Debug]` 日志观察），8 项全部通过（见第 10.1 节验证记录）。逻辑测试 50 个断言分布：主世界规则 8、深空规则 5、组合规则 4、状态机 15、跨边界 4、维度切换 5、0.3s 计时器 9。

出口条件：

- 状态机逻辑清晰且没有已知边界 BUG。（逻辑测试 50/50 通过：GitHub Actions `runLogicTests`）
- 用户确认并 commit。（游戏内验收已通过：用户 2026-08-09 在测试机按 PRD 3.1 清单逐项验证全部通过；commit 待用户执行）
- 未确认前不得进入阶段 3。

当前状态：阶段 2 完成（逻辑测试 50/50 通过、Actions 构建通过、用户游戏内验收全部通过）；等待用户 commit（阶段2收尾）后进入阶段 3。

### 阶段 3：脚部表面检测和基础方向

目标：

- [ ] 实现跟随身体方向的脚部检测。（`detect.FootSamplingLayout`（局部坐标采样点，随 bodyUp/bodyForward 旋转）+ `detect.FootSurfaceDetector`（核心），纯逻辑）
- [ ] 获取接触表面法线。（`api.detect.SurfaceQuery` 接口 + `detect.LevelSurfaceQuery` MC 适配层（`BlockState.getCollisionShape` → 最近面法线，轴向近似））
- [ ] 识别单一表面。（`FootSurfaceResult.SINGLE`，合并同方向）
- [ ] 拒绝多个不同方向表面。（`FootSurfaceResult.MULTIPLE`）
- [ ] 建立统一方向表示。（`api.detect.SurfaceNormal` 六轴向枚举 + `FootSurfaceResult` 连续方向 `Vec3d` 法线）
- [x] 支持物理化方块（Sable 子世界）表面方向。（`detect.SableSubLevelDetector`：**双级反射检测**——方案 A `Sable.HELPER.getTrackingSubLevel(entity)` 主查（Sable 碰撞时记录的当前所在子世界），方案 B `Sable.HELPER.getAllIntersecting(level, 脚底薄片包围盒)` 兜底（世界坐标查询相交子世界，取第一个可读方向）；两级均经 `subLevel.logicalPose().orientation()` 旋转 (0,1,0) 得站立方向；Sable 未装/API 变化时反射失败降级为纯方块检测，不崩溃；**依赖策略：SableSubLevelDetector 用反射（Class.forName），编译期不需要 Sable 类在 classpath，故不加 compileOnly；仅 localRuntime（transitive=false，只拿 Sable 本体 jar，避免 veil/create/ponder/flywheel 传递依赖地狱）用于开发环境**；neoforge.mods.toml 声明 sable optional 依赖 `[2.0.3,)`）

检查：

- [ ] 检查地面、墙面、天花板。（逻辑测试覆盖：UP/EAST/DOWN 站立）
- [ ] 检查墙角和边缘。（逻辑测试覆盖：倾斜姿态横跨地面+墙 → MULTIPLE；边缘部分悬空仍 SINGLE）
- [ ] 检查身体旋转后的脚部位置。（逻辑测试覆盖：单点布局，身体转向墙 → EAST，身体竖直 → NONE）
- [ ] 检查只碰到身体其他部位的情况。（逻辑测试覆盖：采样点全部位于脚底矩形内，无头/躯干点）

测试说明：

- 纯逻辑测试 `FootSurfaceLogicTest`（46 个断言，11 个用例：原 33 断言 9 用例 + Vec3d 数学 9 断言 + 连续方向 4 断言），与阶段 2 的 `RegionLogicTest`（50 个断言）由统一入口 `LogicTestSuite` 运行（`runLogicTests`，共 96 个断言）。
- 2026-08-09 Actions 首跑：`compileJava` 失败——`Level.getBlockCollisionShape(BlockPos)` 在 1.21.1 不存在，已改为 `level.getBlockState(pos).getCollisionShape(level, pos)`（`BlockState.getCollisionShape(BlockGetter, BlockPos)` 稳定 API）。
- 2026-08-09 Actions 二跑：**编译通过 + `runLogicTests` 83/83 全部 [PASS]**（BUILD SUCCESSFUL，当时测试为 33+50）。
- 2026-08-09 Actions 三跑失败：Sable `compileOnly` 的传递依赖 `foundry.veil:veil-neoforge-1.21.1:4.1.4` 无法解析（仓库未配 + 依赖地狱）。**修复：利用 SableSubLevelDetector 纯反射特性，删除 compileOnly，localRuntime 设 `transitive=false`（只拿 Sable 本体 jar）**——编译期不再解析任何 Sable 传递依赖；开发环境缺 veil 时反射降级（预期）；用户测试机完整 Cosmonautics 环境反射成功。
- 2026-08-09 Actions 四跑：编译通过；runLogicTests 1 失败——`[FAIL] 任意方向归一化`：`r.normal().length() == 1.0` 严格比较，浮点精度（0.9999999999999999）。已改为容差比较 `Math.abs(length - 1.0) < 1e-9`。
- 2026-08-09 Actions 五跑：**编译通过 + runLogicTests 96/96 全部 [PASS]**（BUILD SUCCESSFUL）。
- 2026-08-09 游戏内验收发现：物理化方块（Sable 子世界）在 y=8000 处是 `void_air`（静态方块世界为空），`LevelSurfaceQuery` 查不到 → NONE。经用户确认方案 A：加 Sable 可选依赖 + `SableSubLevelDetector` 反射检测子世界方向（已实现，见上）。
- 2026-08-09 游戏内再测（补丁6 jar）：站物理化木板仍 NONE（待用户提供 `脚部检测` 日志行确认 result/source 与有无 `Sable 不可用` 警告；**强烈怀疑测试机装了旧 jar**）。
- 2026-08-10 排查与加固（未 commit，待 Actions 编译 + 用户重测）：
  - 对照 Sable main 分支源码逐一核实反射签名：`Sable.HELPER`（public static final ActiveSableCompanion）、`ActiveSableCompanion.getTrackingSubLevel(Entity)`、`getAllIntersecting(Level, BoundingBox3dc)`、`SubLevel.logicalPose()`、`BoundingBox3d` 六参数构造器全部存在；Modrinth 确认 NeoForge 1.21.1 最新 Sable 为 2.0.3（2026-06-17 发布）。
  - **排除 `getContaining(entity)` 兜底**：Sable plot 网格原点在世界坐标约 ±2048 万格（DEFAULT_ORIGIN=10000 plot、logPlotSize=7），玩家在世界原点附近的 chunk 坐标换算后落在 plot 网格外恒为 null。
  - **新增方案 B 兜底**：`getAllIntersecting` 服务端按子世界全局包围盒暴力匹配（世界坐标语义成立，Sable 自身 `wakeUpObjectsAt` 即如此调用），用脚底薄片包围盒（脚底 ±0.1、水平 ±0.05）查询，解决「有子世界但未被 tracking 记录」的情况；并加一次性阶段日志（Sable 不可用 / 部分 API 缺失 / tracking 返回 null / 包围盒未命中），下一次游戏内验收可直接定位失败环节。
- 设备端 IDE 诊断对本次会话新建的纯逻辑文件跨类引用报 `Cannot resolve symbol`（CodeAssist 会话内新文件索引未更新，`find_symbol` 可定位、logictest 引用正常、代码经人工逐行核对）；以 GitHub Actions 编译为准。
- 楼梯/斜面 45° 法线暂按轴向近似（阶段 5 楼梯专用逻辑处理）；`LevelSurfaceQuery` 对斜面可能返回 MULTIPLE 而保守不站立。
- 游戏内验收手段：`RegionDebugTicker` 已扩展——适用区域内玩家每 1 秒输出一次 `脚部检测` 日志（result / source(sublevel|block) / footCenter），覆盖地面 SINGLE(UP)、墙面 SINGLE(水平法线)、墙角 MULTIPLE、悬空 NONE、物理化方块 SINGLE(子世界方向) 的观察（PRD 3.2 验收 S+D）。

出口条件：

- 单表面、无表面、多表面三类结果可区分。（逻辑测试 46/46 + Actions 编译通过，待用户游戏内验收确认，含物理化方块）
- 用户确认并 commit。（待执行）
- 未确认前不得进入阶段 4。

当前状态：**阶段 3 完成（验收通过）**。实现完成 + Actions 五跑验证通过（编译成功、runLogicTests 96/96）+ 用户游戏内验收通过（2026-08-10，最新 Artifact jar）：水平物理化木板 `SINGLE((-0.001,1.000,-0.000)) source=sublevel`；**侧立木板（侧面朝上）`SINGLE(0.985,0.060,0.158) source=sublevel` → 法线跟随表面方向（近似水平），方向跟随验收通过**；脚部采样 `block=air/void_air`、`collisionEmpty=true` 符合预期；无一次性警告（双级 Sable 路径正常）。CHECKLIST 阶段 3 分组全部 `[x]`。**已知盲区（随阶段 4 增强）**：Sable 路径当前只返回第一个命中的子世界方向（物理化墙角不产生 MULTIPLE），普通方块路径多方向合并已有单元测试覆盖。用户 commit 后进入阶段 4。

### 阶段 4：平滑站立和表面行走

目标：

- [ ] 实现地面、墙面、天花板之间的平滑旋转。
- [ ] 实现表面方向稳定和防抖。
- [ ] 保持其他模组的重力判断。
- [ ] 处理脱离表面后的旋转状态。

检查：

- [ ] 检查快速移动。
- [ ] 检查表面边缘。
- [ ] 检查狭窄空间。
- [ ] 检查方向切换。
- [ ] 检查退出适用区域。

出口条件：

- 旋转连续。
- 不会因边缘检测反复抖动。
- 用户确认并 commit。
- 未确认前不得进入阶段 5。

当前状态：未开始

### 阶段 5：楼梯支持

目标：

- [ ] 识别所有楼梯行为方块。
- [ ] 根据楼梯碰撞形状计算旋转进度。
- [ ] 支持上下楼。
- [ ] 支持从平面进入墙面。

检查：

- [ ] 检查原版楼梯。
- [ ] 检查模组楼梯。
- [ ] 检查不同朝向。
- [ ] 检查楼梯边缘。
- [ ] 确认非楼梯方块不会误判。

出口条件：

- 楼梯行走没有明显跳变或卡顿。
- 用户确认并 commit。
- 未确认前不得进入阶段 6。

当前状态：未开始

### 阶段 6：Do a Barrel Roll 兼容

目标：

- [ ] 接入正确的 Do a Barrel Roll 旋转入口。
- [ ] 保留原有翻滚。
- [ ] 叠加表面旋转。
- [ ] 处理缺少依赖或不兼容版本。

检查：

- [ ] 检查 Mixin 注入点。
- [ ] 检查旋转叠加顺序。
- [ ] 检查重复修改。
- [ ] 检查启动和运行时错误。

出口条件：

- Do a Barrel Roll 的已有功能未被破坏。
- 用户确认并 commit。
- 未确认前不得进入阶段 7。

当前状态：未开始

### 阶段 7：碰撞、视角和防穿模

目标：

- [ ] 自研方向重力（Mixin `LivingEntity.travel()`，重力方向旋转到表面法线反方向，两端生效）。
- [ ] 处理旋转后的碰撞方向。
- [ ] 处理旋转空间检查。
- [ ] 处理第一人称视角。
- [ ] 处理第三人称模型。
- [ ] 处理观察视角。
- [ ] 空间不足时限制或暂缓旋转。

检查：

- [ ] 检查墙角。
- [ ] 检查狭窄通道。
- [ ] 检查天花板。
- [ ] 检查旋转过程中被方块包围的情况。
- [ ] 检查玩家卡死、弹飞和穿模。

出口条件：

- 没有已知严重穿模、卡死或异常位移问题。
- 用户确认并 commit。
- 未确认前不得进入阶段 8。

当前状态：未开始

### 阶段 8：Aeronautics 铁把手

目标：

- [ ] 识别铁把手。
- [ ] 获取铁把手长轴。
- [ ] 读取玩家与铁把手的相对位置。
- [ ] 平行对齐玩家身体。
- [ ] 自动选择头脚方向。
- [ ] 保留铁把手原有移动逻辑。

检查：

- [ ] 检查右键触发。
- [ ] 检查拉近过程。
- [ ] 检查方向冲突。
- [ ] 检查状态结束。
- [ ] 检查与表面站立同时发生的情况。

出口条件：

- 铁把手原有行为没有被改变。
- 用户确认并 commit。
- 未确认前不得进入阶段 9。

当前状态：未开始

### 阶段 9：最终检查和发布配置

目标：

- [ ] 检查全部源码诊断。
- [ ] 检查 Mixin 配置。
- [ ] 检查模组元数据。
- [ ] 检查构建产物配置。
- [ ] 检查 GitHub Actions。
- [ ] 检查 README 和版本说明。
- [ ] 检查仓库中没有本地缓存或敏感文件。

检查：

- [ ] 由 GitHub Actions 完成编译。
- [ ] 检查 Actions 日志。
- [ ] 检查最终 JAR 内容。
- [ ] 记录已知限制和未完成项目。

出口条件：

- GitHub Actions 构建成功。
- 用户检查结果并确认。
- 用户执行最终 commit/tag 后，第一版开发结束。

当前状态：未开始

---

## 7. 构建验收

- [ ] GitHub Actions 可以自动执行。
- [ ] GitHub Actions 使用正确 Java 版本。
- [ ] GitHub Actions 成功完成构建。
- [ ] 构建产物包含最终模组 JAR。
- [ ] 无内嵌依赖，方向重力自研（不依赖外部 Gravity API）。
- [ ] Git 仓库不包含构建缓存和本地敏感配置。
- [ ] 不在本设备上执行编译或运行。

---

## 8. 已知风险

以下内容在实现前必须通过实际依赖源码或构建结果确认（由用户检查项目后提供，AI 逐项询问并记录）：

- Do a Barrel Roll 的实际旋转数据结构和 Mixin 注入点。（已核查：见第 9 节，API 为 RollEvents/RotationInstant）
- Gravity API 是否确实提供 NeoForge `1.21.1` 版本。（已核查：未找到；Modrinth 同名项目仅 Fabric/Quilt 且最高 1.20.1，决定：不依赖外部 Gravity API，方向重力自研）
- Gravity API 是否允许当前方式 Jar-in-Jar。（已决定：不使用，无内嵌依赖）
- Aeronautics 铁把手的实际实现类和运动流程。（已核查：HandleBlock / HandleBlockEntity / ClientHandleHandler，见第 9 节；决定：用注册名 + BlockState 属性判断，不引用类、不 Mixin 第三方类）
- Rocketnautics 维度注册是否确实为 `rocketnautics:deep_space`。（已核查：是，见第 9 节）
- 多人预留：第一版单人，但核心逻辑使用纯函数 + 两端共有 API，预留 CustomPacketPayload 旋转状态包结构。
- Minecraft `1.21.1` 玩家碰撞箱是否能以所需方式支持旋转。
- 客户端模型旋转与实际碰撞系统之间是否需要额外兼容处理。
- 第一人称镜头旋转与原版渲染流程的兼容性。
- 单人游戏中客户端逻辑与内部服务端逻辑之间的状态一致性。

如果实际 API 与需求不一致，必须先记录差异、说明影响并等待用户确认，不得凭猜测继续实现。

---

## 9. 当前进度

当前阶段：

```text
阶段 3：脚部表面检测和基础方向（实现完成，待 Actions 验证与游戏内验收）
```

当前状态：

```text
阶段 3 实现完成 + Actions 五跑验证通过（编译成功、runLogicTests 96/96）。
脚底采样点布局（跟随身体方向旋转）、六轴向统一方向表示（SurfaceNormal）、
连续方向法线（FootSurfaceResult + Vec3d，支持任意 3D 方向）、
表面查询接口 + MC 适配层（LevelSurfaceQuery）、检测器（NONE/SINGLE/MULTIPLE）、
Sable 子世界检测（SableSubLevelDetector，方案 A：纯反射 + localRuntime(transitive=false)）。
RegionDebugTicker 已扩展：脚部检测日志（source=sublevel|block）。

⚠️ 待排查（2026-08-09 晚，用户测试机游戏内）：Sable 子世界检测未生效，
站物理化木板仍 result=NONE（与修复前"一样"）。
下一步排查（用户睡醒后）：
1. 用户贴 [Debug] 日志：脚部检测那行的 result= / source=；
   是否有 "Sable 不可用，降级为纯方块表面检测" 或 "Sable 子世界方向检测异常"；
   logFootBlockDetail 的 block= 是否 air/void_air。
2. 确认测试机 jar 是 Actions 五跑（96/96）后的最新 Artifact。
3. 若反射失败：对照 sable 源码核对 SableSubLevelDetector 的
   getTrackingSubLevel 参数类型 / logicalPose 归属类 / orientation 接口方法。
阶段 3 未完成：等待排查 Sable 检测 + 游戏内验收 + commit 后进入阶段 4。
```

已确认：

- [x] Minecraft `1.21.1`
- [x] NeoForge
- [x] 主世界使用碰撞箱中心 Y 坐标判断
- [x] 主世界阈值为 `Y >= 8000`
- [x] 宇宙维度为 `rocketnautics:deep_space`
- [x] 只支持单人模式
- [x] 保留 Do a Barrel Roll 原有翻滚
- [x] 只检测玩家脚部
- [x] 单一表面才站立
- [x] 楼梯包括模组楼梯
- [x] 铁把手只处理旋转，不改变原有拉近逻辑
- [x] 铁把手自动选择头脚方向
- [x] 旋转采用平滑过程
- [x] 第一人称和第三人称都需要处理
- [x] 空间不足时限制或暂缓旋转
- [x] 第一版只在指定区域启用
- [x] GitHub Actions 由项目配置，设备不编译

阶段 0 方案结论（已与用户讨论确定）：

1. **Gravity API 不依赖**：NeoForge `1.21.1` 无可用外部 Gravity API（Modrinth 同名项目仅 Fabric/Quilt 且最高 1.20.1）。改为自研"方向重力"：注入两端共有的 `LivingEntity.travel()`，把重力方向旋转到表面法线反方向，实现墙面/天花板行走；重力大小仍由 Cosmonautics/Sable 决定，本模组只改方向。
2. **方向重力两端生效**：Mixin 注入 `LivingEntity.travel()`（服务端 + 客户端都有），不注入纯客户端类；否则单机内置服务器不认，玩家会被弹回。纯客户端只做视觉（第一人称镜头、第三人称模型、翻滚渲染）。
3. **墙面行走机制**：站在墙 A → 重力方向 = 墙 A 法线反方向 → 脚部检测跟着身体方向旋转 → 探到墙 B 单一表面 → 平滑过渡到墙 B；转角（多方向表面）保持当前方向不切换，防抖。
4. **多人预留**：核心逻辑纯函数（输入 Level/Entity/BlockPos，输出状态）；预留 `CustomPacketPayload` 旋转状态包结构（第一版不实现发送）；外部模组判断用 BlockState + 字符串，两端通用。见第 4 节"多人预留原则"。
5. **依赖最小化（几乎零编译依赖）**：Do a Barrel Roll 用官方 API（`RollEvents`/`RotationInstant`）；Cosmonautics 只用 `rocketnautics:deep_space` 字符串判断，不引用类；铁把手用 `simulated:iron_handle` + `FACING` + `axis_along_first` 属性 + 复刻 20 行 getAxis 算法，不引用类、不 Mixin 第三方类。因此不需要 maven.ryanhcode.dev 作为编译依赖。

旁边源码位置（用户提供：都在 project/ 下）：

- [x] `do-a-barrel-roll`：Do a Barrel Roll `3.7.3`（stonecutter 工程），本地已有 NeoForge 编译产物 `do_a_barrel_roll-neoforge-3.7.3+1.21.jar` 及 `-sources.jar`；Modrinth 版本号为 `3.7.3+1.21-neoforge`
- [x] `Create-Cosmonautics-main`：Cosmonautics（Rocketnautics）`1.4.0.rc1`，源码，无本地编译产物
- [x] `Simulated-Project`：Create Aeronautics `1.2.1`（aeronautics + simulated + offroad 子项目），源码，无本地编译产物
- [x] `LowGravity`：现有原型模组（FlightController / RollController / OBBCollision / 4 个 Mixin）
- [x] Gravity API：无外部库（已决定不依赖）

AI 从源码确认的接口（记录，供后续阶段使用）：

- [x] Rocketnautics 维度：`RocketNautics.MODID = "rocketnautics"`，`RocketDimensions.DEEP_SPACE = rocketnautics:deep_space`（与 PRD 一致）；另有 `rocketnautics:moon`
- [x] Do a Barrel Roll API 入口：`nl.enjarai.doabarrelroll.api.rotation.RotationInstant`、`nl.enjarai.doabarrelroll.api.event.RollEvents`（SHOULD_ROLL_CHECK / EARLY_CAMERA_MODIFIERS / LATE_CAMERA_MODIFIERS）、`nl.enjarai.doabarrelroll.api.event.RollContext`；其 Mixin 注入点在 client/roll（Camera、Mouse、EntityRenderer 等，属 DABR 内部，我们通过 API 叠加旋转）
- [x] Aeronautics 铁把手：`dev.simulated_team.simulated.content.blocks.handle.HandleBlock`（Variant.IRON 为铁把手；方向属性 FACING + AXIS_ALONG_FIRST_COORDINATE）、`HandleBlockEntity`（getGrabCenter、MAX_HANDLE_RANGE=5.0）、`ClientHandleHandler`（右键 startHold 后每 tick 拉近玩家）、`ServerHandleHoldingHandler`、`SimTags.Blocks.HANDLES`
- [x] 依赖仓库（仅参考，非编译依赖）：`maven.ryanhcode.dev/releases` 上有 `dev.eriksonn.aeronautics:aeronautics-neoforge-1.21.1:1.2.1`、`dev.simulated_team.simulated:simulated-neoforge-1.21.1:1.2.1`（含 sources jar）、`dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.3`、`dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0` 等

阶段 0 结束条件：

1. AI 逐项询问上述待确认内容。
2. 用户检查项目并逐项回答。
3. AI 记录用户提供的实际版本和接口。
4. 检查没有无法满足的依赖或技术限制。
5. 向用户报告整理后的结果，等待用户确认。
6. 用户确认并 commit 后，才进入阶段 1。

---

## 10. PRD 检查清单

> PRD 检查清单（各阶段逐项验收状态与验证记录）已独立为 [CHECKLIST.md](CHECKLIST.md)，作为**唯一维护位置**，本文件不再维护检查表格。
> 验收模式（`S` 标准 / `D` Debug / `S+D` 两种）与 Debug 调试功能说明见 CHECKLIST.md 头部。
> 使用规则不变：每个阶段收尾时对照 [PRD.md](PRD.md) 逐项检查；全部通过（含用户实际游戏验证）才能标记阶段完成，未通过的项记录差异并等待用户确认。

### 10.1 阶段 2 → PRD 3.1 适用区域判断

> 检查项与验证记录见 [CHECKLIST.md](CHECKLIST.md) 第 1 节（状态：[x] 全部通过，2026-08-09 用户游戏内验证 + Actions 逻辑测试 50/50）。

### 10.2-10.6 阶段 3-7 → PRD 3.2-3.6

> 检查项与状态见 [CHECKLIST.md](CHECKLIST.md) 第 2-6 节（阶段 3 逻辑测试 46 断言已通过，游戏内验收进行中；阶段 4-7 未开始）。

### 10.7 阶段 8 → PRD 3.7 铁把手方向对齐

> 检查项与状态见 [CHECKLIST.md](CHECKLIST.md) 第 7 节（未开始）。

### 10.8 第一版不做的内容（PRD 第 4 节约束）

> 检查项与状态见 [CHECKLIST.md](CHECKLIST.md) 第 8 节（全部 [-] 不实现，阶段 9 复查确认）。

### 10.9 全局验收（PRD 第 5 节 + 本文件第 7 节，阶段 9 复查）

> 检查项与状态见 [CHECKLIST.md](CHECKLIST.md) 第 9 节（阶段 1 已验证项 [x]，其余待阶段 9）。
