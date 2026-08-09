# Do-a-Cosmonautics-roll 操作日志

> 产品需求见 [PRD.md](PRD.md)，开发规则与任务见 [DEVELOPMENT.md](DEVELOPMENT.md)，检查清单见 [CHECKLIST.md](CHECKLIST.md)。
> 本文件记录 AI 在本项目中的**每次实际操作**（编辑了哪些文件、做了什么改动、验证结果），按日期倒序排列。
> 游戏内验收等由用户执行的操作，由用户在对应条目中补充结果。
> Git 操作（commit/push）全部由用户执行，AI 不执行任何 Git 操作。

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
