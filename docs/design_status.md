# NQuest 项目现状文档

> 版本 0.10.0 · Fabric 1.20.4 · 最后更新 2026-05-31

---

## 一、项目概述

NQuest（全称 Nemo's Quest Mod）是一个 Minecraft Fabric 服务端模组，为 Let's Play 服务器上使用 Minecraft Transit Railway (MTR) 模组搭建的铁路网络提供**任务寻宝 / 打卡探索**玩法。玩家通过乘坐列车、到达车站、进入指定区域或完成地图上设置的互动来逐步完成任务，获得 Quest Points (QP)，并在排行榜上竞争。

---

## 二、UX 设计现状

### 2.1 入口

| 入口方式 | 说明 |
|---------|------|
| `/nquest` 命令 | 无参数时打开 GUI（`GuiStarter`）；若有正在进行的任务则直接进入 CurrentQuestScreen，否则进入 MainMenuScreen |

### 2.2 GUI 屏幕树

所有 GUI 均为**服务端 GUI**（基于 `eu.pb4:sgui`，使用原版箱子/容器界面渲染）。

```
GuiStarter.openEntry()
├─ 有活跃任务？ → CurrentQuestScreen
│                   └─ Abort Quest → DialogGui（确认放弃）
└─ 无活跃任务？ → MainMenuScreen (9×3)
                    ├─ [槽11] Start a Quest → QuestListScreen (9×4, 分类标签)
                    │                            └─ 选择任务 → DialogGui（确认开始）
                    ├─ [槽13] Leaderboards → LeaderboardScreen (9×4, 两级标签)
                    │                          ├─ Tab: Total QP（全时段/月度）
                    │                          ├─ Tab: Total Completions（全时段/月度）
                    │                          └─ Tab: Speedruns → QuestListScreen → QuestSpeedrunScreen
                    │                                                                  └─ 点击条目 → QuestCompletionDetailScreen
                    └─ [槽15] My Profile → ProfileScreen (9×3)
                                            ├─ 总 QP
                                            ├─ 总完成数
                                            └─ Quest History → QuestHistoryScreen (9×4, 分页)
                                                                └─ 点击条目 → QuestCompletionDetailScreen
```

### 2.3 GUI 基础组件

| 组件 | 说明 |
|------|------|
| `ParentedGui` | 继承 `SimpleGui`，带"返回"按钮（`goBack()` 返回父界面） |
| `ItemListGui<T>` | 分页列表，支持异步数据加载（`CompletableFuture`）、Loading 状态（雪球）、Error 状态（屏障）、上下页翻页（纸张图标，带 500ms 冷却） |
| `TabbedItemListGui<T, P, S>` | 在 `ItemListGui` 基础上加一级/二级标签切换（显示在第 0 行），用灰色/白色玻璃区分选中状态 |
| `DialogGui` | 确认/取消对话框，3×9 布局，Cancel（红色混凝土）和 Confirm（绿色混凝土） |

### 2.4 实时反馈

| 反馈渠道 | 触发场景 | 内容 |
|----------|---------|------|
| **聊天消息** | 任务开始 | ⭐ Quest Started! ⭐ + 任务名 + 第一步提示 |
| | 步骤完成 | ✔ Step Complete: ... + ▶ Next: ... |
| | 任务完成 | ⭐ Quest Complete! ⭐ + 用时 + QP 奖励 |
| | 任务放弃 | ✘ Quest Aborted ✘ |
| | 任务失败 | ✘ Quest Failed ✘ + 失败原因 |
| **客户端 HUD** | 任务进行中 | 左上角显示当前步骤 `displayRepr`、`Step X/Y`、`Total time: H:MM:SS`、`Segment time: H:MM:SS` |
| **音效** | 步骤/任务完成 | `amethyst_block_resonate`（音量 2.0） |
| | 任务完成 | `ui_toast_challenge_complete` |
| | 放弃/失败 | `anvil_land`（音量 0.5） |

> NQuestMod 现在需要同时安装在服务端和客户端。客户端通过 `nquestmod:quest_hud` 网络包接收任务 HUD 状态；未安装客户端模组的玩家会在加入时被服务端拒绝。

### 2.5 任务列表展示

- **QuestListScreen** 按 `QuestCategory` 分标签页（一级标签），每个标签内按 `QuestTier.order` 排序
- 每个任务显示：名称、Tier 标签（黄色）、描述 lore
- 图标：Tier 有自定义 icon（通过 ResourceLocation 指定物品），否则默认 `Items.BOOK`

### 2.6 当前任务界面

- **CurrentQuestScreen** 列出所有步骤
- 已完成步骤：绿色陶瓦（GREEN_TERRACOTTA）
- 当前步骤：黄色混凝土（YELLOW_CONCRETE）
- 未到步骤：灰色混凝土（GRAY_CONCRETE）
- 步骤编号：物品堆叠数 = `stepIndex + 1`
- 底部有"Abort Quest"按钮（BARRIER 图标），点击后弹出 DialogGui 确认

### 2.7 排行榜

| 类型 | 数据来源 | 条目展示 |
|------|---------|---------|
| Total QP | `RankingApiClient.getQPLeaderboard` | 玩家头像 + `#排名 玩家名` + `xxx QP` |
| Total Completions | `RankingApiClient.getCompletionsLeaderboard` | 同上 + `xxx completions` |
| Speedruns | `RankingApiClient.getSpeedrunLeaderboard`（先选任务） | 玩家头像 + 排名 + `H:MM:SS` + 可点击查看详情 |

- 支持全时段 / 月度切换（二级标签）

### 2.8 个人资料 & 历史

- **ProfileScreen**：玩家头像、总 QP、总完成数（来自 `RankingApiClient.getPlayerProfile`）、进入历史记录入口
- **QuestHistoryScreen**：通过 `RankingApiClient.getPlayerHistory` 分页列出历史完成记录，每条显示任务名、完成时间、用时、获得 QP
- **QuestCompletionDetailScreen**：逐步展示每步耗时，头部显示任务名、完成者、QP、总用时

---

## 三、技术架构现状

### 3.1 模块划分

```
cn.zbx1425.nquestmod
├── NQuestMod.java          # 模组入口，生命周期管理
├── Commands.java           # Brigadier 命令注册（/nquest 及子命令）
├── QuestNotifications.java # IQuestCallbacks 实现，聊天/HUD 同步/音效通知
├── NQuestModClient.java    # 客户端入口，HUD 渲染与客户端配置
├── QuestHudNetworking.java # 服务端到客户端的任务 HUD 状态包
├── QuestEventLogger.java   # 任务事件审计日志：按天写 Start/Progress/Fail/Finish/Abort
├── ServerConfig.java       # 服务端配置：Web API、同步、事件日志目录/时区/保留天数
├── CommandSigner.java      # HMAC-MD5 时间戳签名（保护远程 set 命令）
│
├── data/                   # 核心数据与逻辑
│   ├── QuestDispatcher.java      # 任务调度核心：开始/停止/推进/失败判定
│   ├── QuestPersistence.java     # JSON 持久化（任务定义、分类、签名密钥）
│   ├── QuestProgressPersistence.java # 活跃任务进度 JSON 持久化（quest_sessions）
│   ├── QuestSyncClient.java      # 远端任务/分类同步，主线程应用新 bundle
│   ├── NQuestGson.java           # 统一 Gson 配置（注册 CriteriaRegistry 工厂）
│   ├── QuestException.java       # 错误类型枚举 + Brigadier 异常转换
│   ├── IQuestCallbacks.java      # 任务生命周期回调接口
│   ├── RuntimeTypeAdapterFactory  # Gson 多态序列化
│   ├── Vec3d.java                # 3D 坐标 + AABB 判定
│   │
│   ├── quest/              # 任务数据模型
│   │   ├── Quest.java            # 任务定义：id, name, description, category, tier, questPoints, steps, defaultCriteria
│   │   ├── Step.java             # 步骤 = criteria + failureCriteria（纯数据容器 + evaluate/expand 辅助方法）
│   │   ├── StepState.java        # 步骤状态容器：Map<String, JsonObject>，按树路径寻址
│   │   ├── QuestProgress.java    # 玩家正在进行的任务运行时状态（含 questSnapshot + StepState）
│   │   ├── PlayerProfile.java    # 玩家档案：UUID, 总QP, 总完成数, activeQuests
│   │   ├── QuestCategory.java    # 分类：name, description, icon, order, tiers
│   │   ├── QuestTier.java        # 分级：name, icon, order
│   │   └── QuestCompletionData   # 完成记录快照
│   │
│   ├── criteria/           # 条件系统
│   │   ├── Criterion.java        # 接口：evaluate / evaluateFailureTypes / getDisplayRepr / expand / propagateManualTrigger
│   │   ├── CriterionContext.java # 求值上下文 record：封装 StepState + 当前树路径，child() 派生子节点
│   │   ├── CriteriaRegistry.java # 注册所有 Criterion 子类到 Gson RuntimeTypeAdapterFactory
│   │   ├── 逻辑组合: AndCriterion, OrCriterion, NotCriterion
│   │   ├── 状态保持: LatchingCriterion（一旦满足不可逆）, RisingEdgeAndConditionCriterion（上升沿+条件）
│   │   ├── 触发器: ManualTriggerCriterion, ConstantCriterion
│   │   ├── 包装器: Descriptor（自定义显示文本）
│   │   ├── 位置: InBoundsCriterion（AABB 区域）
│   │   ├── 运动: OverSpeedCriterion, TeleportDetectCriterion
│   │   │
│   │   └── mtr/            # MTR 专用条件
│   │       ├── MtrNameUtil.java          # 站名/线名匹配（支持 name: 和 id: 前缀）
│   │       ├── VisitStationCriterion / InStationAreaCriterion / StationStopCriterion
│   │       ├── RideLineCriterion / RideFromStationCriterion / RideToStationCriterion
│   │       └── RideLineFromStationCriterion / RideLineToStationCriterion
│   │
│   └── ranking/            # 排行 API 与待提交完成记录 WAL
│       ├── RankingApiClient.java   # Web API：排行、历史、资格检查、完成提交
│       ├── PendingCompletions.java # 本地 JSONL WAL，失败提交可重放
│       ├── PlayerQPEntry.java      # QP 排行条目
│       └── PlayerCompletionsEntry  # 完成数排行条目
│
├── interop/                # MTR 桥接层
│   ├── TscStatus.java            # 共享状态：玩家位置 → MTR 车站/线路映射
│   └── GenerationStatus.java     # 传送检测：generation 计数 + 玩家 warp 标记
│
├── sgui/                   # 服务端 GUI（全部 11 个屏幕类）
│   ├── GuiStarter, MainMenuScreen, QuestListScreen, CurrentQuestScreen
│   ├── LeaderboardScreen, QuestSpeedrunScreen, QuestCompletionDetailScreen
│   ├── ProfileScreen, QuestHistoryScreen
│   ├── ParentedGui, ItemListGui, TabbedItemListGui, DialogGui
│
└── mixin/                  # Mixin 注入与 accessor
    ├── SimulatorMixin.java       # 注入 MTR Simulator.tick()，同步站点/线路数据到 TscStatus
    ├── ServerPlayerMixin.java    # 注入 teleportTo()，标记传送事件到 GenerationStatus
    ├── ScoreboardCommandMixin.java # 注入 setScore()，兼容旧版 scoreboard 触发
    └── SidingAccessor / VehicleAccessor / VehicleSchemaAccessor # 读取 MTR 内部车辆/线路状态
```

### 3.2 数据流：任务进度推进

```
每秒触发一次 (ServerTickEvents, tickCount % 20 == 15)
    │
    ▼
QuestDispatcher.updatePlayers()
    │  遍历 playerProfiles → 每个有 activeQuests 的玩家
    │  使用 progress.questSnapshot（开始任务时冻结的原始未展开 Quest 定义）
    ▼
tryAdvance(profile, progress, player, triggerId=null)
    │
    ├─ 1. 惰性展开：如果 expandedCurrentStep == null（首次 / 断线重连后 / 步骤推进后），
    │     从 questSnapshot.steps[currentStepIndex].expand() 展开语法糖
    │     缓存到 transient expandedCurrentStep / expandedDefaultCriteria
    │
    ├─ 2. 构建 CriterionContext（state=StepState, path=""），将状态与定义解耦
    │
    ├─ 3. 检查失败条件 → step.evaluateFailure(player, failureCtx, defaultCriteria, defaultFailCtx)
    │     → 若失败：移除活跃任务，保存进度，回调 onQuestFailed
    │     → QuestNotifications 发送聊天/HUD 清理/音效，并写入 Fail 事件日志
    │
    └─ 4. 检查完成条件 → step.evaluate(player, criteriaCtx)
          → 若满足：advanceQuestStep()
              ├─ 结算当前步骤耗时并 currentStepIndex++
              ├─ 若还有下一步 → 重置 StepState，保存进度，回调 onStepCompleted
              │                  → 写入 Progress X/Y（HUD 当前 Step X/Y）
              └─ 若所有步骤完成 → 移除活跃任务，生成 QuestCompletionData，
                                   更新内存统计，写入 PendingCompletions WAL，
                                   保存进度，回调 onQuestCompleted → 写入 Finish，
                                   后台提交 RankingApiClient（Idempotency-Key = clientCompletionId）
```

### 3.3 数据流：MTR 状态同步

```
每秒第 5 tick (tickCount % 20 == 5):
    TscStatus.requestUpdate(server)
    → 记录所有在线玩家的 BlockPos 到 CLIENT_POSITIONS

MTR Simulator.tick() 结尾 (SimulatorMixin):
    → 检查 nonce 是否更新
    → 解析 STATION_NAME_REQUESTS（延迟加载站名）
    → 如果有任务进行中(isAnyQuestGoingOn)：
        ├─ 对每个 client：找出玩家所在的所有 Station area → CLIENTS[uuid]
        └─ 对每个 siding 上的乘客：附加其所乘线路信息 → CLIENTS[uuid]

MTR Criterion 在 evaluate(player, ctx) 中读取:
    TscStatus.getClientState(player)
    → 获取 stations: Collection<NameIdData>, line: NameIdData
```

### 3.4 数据流：手动触发

```
/nquest quest trigger <participant> <trigger_id>  (权限等级 2)
    │
    ▼
QuestDispatcher.triggerManualCriterion(uuid, triggerId, player)
    │  遍历该玩家的 activeQuests
    ▼
tryAdvance(... triggerId)
    ├─ propagateManualTrigger(triggerId, ctx)
    │     递归传播到所有嵌套 Criterion（通过 CriterionContext 写入 StepState）
    │     ManualTriggerCriterion: 如果 id 匹配则 ctx.setBoolean("triggered", true)
    └─ 然后正常走 evaluate / evaluateFailure 检查

兼容路径（ScoreboardCommandMixin）:
    setScore 目标为 "mtrq_quest_complete" 时
    → 自动触发 "legacy_trigger_<score>"
```

### 3.5 持久化

| 类型 | 存储位置 | 格式 | 时机 |
|------|---------|------|------|
| 任务定义 | `<world>/nquest/quests/<id>.json` | JSON (NQuestGson + RuntimeTypeAdapterFactory) | set 命令时立即保存；启动时加载全部 |
| 分类定义 | `<world>/nquest/categories.json` | JSON | set 命令时保存；服务器关闭时保存；启动时加载 |
| 服务端配置 | `<server>/config/nquest.json` | JSON（含 `commandSigningKey`、Web API、事件日志配置） | 启动时加载；服务器关闭时保存 |
| 活跃任务进度 | `<world>/nquest/quest_sessions/<uuid>.json` | JSON（`QuestProgress.questSnapshot` + StepState + 计时/线路记录） | 开始、推进、失败/完成/放弃、退出、关闭时保存；空任务时删除 |
| 待提交完成记录 | `<world>/nquest/pending_completions.jsonl` | 每行一个 `QuestCompletionData` JSON；`clientCompletionId` 去重 | API 开启时任务完成先入 WAL；提交成功后移除；启动/提交后重放 |
| 历史/排行记录 | 远端 `webBackendUrl` | JSON API | `RankingApiClient` 查询排行、玩家历史、资格检查并提交完成记录 |
| 任务事件日志 | `<server>/logs/nquest/quest_events-YYYY-MM-DD.log`（可通过 `eventLogDir` 配置） | UTF-8 文本，每行 `Event player unixTimestamp zoneId questId [detail]` | 任务开始、进度、失败、完成、放弃时追加；默认保留 7 天 |

所有 Gson 序列化/反序列化统一使用 `NQuestGson.INSTANCE`（注册了 `CriteriaRegistry` 工厂），确保 `QuestProgress.questSnapshot` 中的多态 Criterion 树可正确序列化。

任务事件日志配置项：`eventLogDir` 默认 `logs/nquest`（相对服务器根目录，绝对路径也可用），`eventLogTimezone` 默认 `UTC`，`eventLogRetentionDays` 默认 `7`（`0` 表示不自动删除）。日志文件日期按配置时区计算，行内时间戳始终是 Unix epoch seconds；旧版 `<world>/nquest/quest_events.log` 不再读取或迁移。

任务事件日志语义：
- `Start player unixTimestamp zoneId questId`：仅任务开始时写入，不立即写 `Progress 1/Y`
- `Progress player unixTimestamp zoneId questId X/Y`：步骤完成后、HUD 当前步骤变为 `Step X/Y` 时写入；最终步骤不写 `Progress Y/Y`
- `Fail player unixTimestamp zoneId questId LeafCriterionType[+LeafCriterionType]`：失败时写入匹配的失败条件叶子类型，按树顺序用 `+` 连接
- `Finish player unixTimestamp zoneId questId`：任务完成时写入
- `Abort player unixTimestamp zoneId questId`：显式停止、任务删除、reload/sync 删除等非失败/非完成结束时写入

### 3.6 命令体系

| 命令 | 权限 | 功能 |
|------|------|------|
| `/nquest` | 0 | 打开 GUI |
| `/nquest quest start <participant> <quest_id>` | 2 | 为指定玩家开始任务 |
| `/nquest quest stop [participant]` | 2 | 放弃任务（无参为自己，指定玩家需权限 2） |
| `/nquest quest trigger <participant> <trigger_id>` | 2 | 手动触发条件 |
| `/nquest config quests set <sign> <json>` | 2 | 设置任务定义（需签名） |
| `/nquest config quests get <quest_id>` | 3 | 获取任务 JSON |
| `/nquest config quests remove <quest_id>` | 3 | 删除任务定义；若有玩家正在进行该任务，会按 Abort 结束 |
| `/nquest config categories set <sign> <json>` | 2 | 设置分类定义（需签名） |
| `/nquest config categories get` | 3 | 获取分类 JSON |
| `/nquest config sign` | 3 | 生成 5 分钟有效的 HMAC-MD5 时间戳签名 |
| `/nquest config reload` | 3 | 重载服务端配置 |
| `/nquest debugMode [participant]` | 2 | 切换调试模式（无参为自己，指定玩家需权限 2） |

签名机制：`config quests set` 和 `config categories set` 需要通过 `/nquest config sign` 获取签名，防止未授权修改。

### 3.7 条件系统详解

条件系统是任务的核心，采用**组合模式 + 多态序列化 + 定义/状态分离**。

**架构：定义层与状态层完全分离**

- **Criterion**：纯不可变定义，从 JSON 反序列化，描述"检查什么"。Criterion 对象上不存储任何可变状态。
- **StepState**：`Map<String, JsonObject>` 状态容器，每个 Criterion 节点通过其**树路径**（如 `""`, `"0"`, `"0/b"`, `"1/t"`）读写状态。可通过 Gson 完整序列化/反序列化。
- **CriterionContext**：轻量 record，封装 StepState + 当前路径，`child()` 方法按树结构层层派生。Criterion 的 `evaluate()` 和 `propagateManualTrigger()` 通过它访问状态。

**接口 `Criterion`：**
- `evaluate(ServerPlayer, CriterionContext)` → 是否满足（替代原 `isFulfilled`）
- `evaluateFailureTypes(ServerPlayer, CriterionContext, List<String>)` → 失败判定并收集匹配叶子类型，用于事件日志
- `collectLeafTypes(List<String>)` → 收集条件树叶子类型名，组合条件用于失败原因展开
- `getDisplayRepr()` → 用于 GUI/聊天的显示文本
- `expand()` → 展开语法糖（如 RideLineToStation → Descriptor(RisingEdge(VisitStation, RideLine))），默认返回自身
- `propagateManualTrigger(String, CriterionContext)` → 传播手动触发信号

**已移除的方法：**
- `createStatefulInstance()` — 不再需要，状态在外部 StepState 中
- `isFulfilled(ServerPlayer)` — 替换为 `evaluate(ServerPlayer, CriterionContext)`

**树路径寻址规则：**
- 根节点：`""`
- And/Or 的第 i 个子节点：`"0"`, `"1"`, `"2"`, ...
- Latching/Not/Descriptor 的 base：`"b"`
- RisingEdge 的 trigger：`"t"`，condition：`"c"`

**已实现的条件类型：**

| 类型 | 说明 | 使用 StepState |
|------|------|--------|
| `ConstantCriterion` | 常量 true/false | 否 |
| `ManualTriggerCriterion` | 匹配 triggerId 后 ctx.setBoolean("triggered", true) | 是 |
| `InBoundsCriterion` | 玩家在 AABB 内 | 否 |
| `OverSpeedCriterion` | 玩家速度超过阈值（通过 ctx 保存 lastX/Y/Z 和 lastTick） | 是 |
| `TeleportDetectCriterion` | 玩家发生传送 | 否（读 GenerationStatus） |
| `VisitStationCriterion` | 玩家在匹配的 MTR 车站区域内 | 否 |
| `InStationAreaCriterion` | 玩家在指定 MTR 车站区域内 | 否 |
| `StationStopCriterion` | 玩家所在列车停靠在匹配车站 | 否 |
| `RideLineCriterion` | 玩家正在乘坐匹配的 MTR 线路 | 否 |
| `RideFromStationCriterion` | **语法糖**：从指定车站乘车离开 | — |
| `RideToStationCriterion` | **语法糖**：expand() → Descriptor(RisingEdge(VisitStation, RideLine(""))) | — |
| `RideLineFromStationCriterion` | **语法糖**：乘坐指定线路从指定车站离开 | — |
| `RideLineToStationCriterion` | **语法糖**：expand() → Descriptor(RisingEdge(VisitStation, RideLine)) | — |
| `AndCriterion` | 全部子条件满足 | 取决于子条件 |
| `OrCriterion` | 任一子条件满足 | 取决于子条件 |
| `NotCriterion` | 取反 | 取决于子条件 |
| `LatchingCriterion` | 一旦满足则锁定（ctx.setBoolean("fulfilled", true)） | 是 |
| `RisingEdgeAndConditionCriterion` | trigger 从 false→true 的瞬间 condition 也为 true | 是 |
| `SequenceCriterion` | 子条件按顺序逐个满足（ctx.setInt("step", ...)） | 是 |
| `Descriptor` | 为条件添加自定义描述文本 | 取决于子条件 |

### 3.8 任务模型

**Quest 结构：**
- `id` (String) — 唯一标识
- `name`, `description` — 显示信息
- `category`, `tier` — 分类/等级（关联 QuestCategory/QuestTier）
- `questPoints` (int) — 奖励 QP
- `defaultCriteria` (Step, 可选) — 全局失败条件，适用于所有步骤
- `steps` (List<Step>) — 步骤序列，按顺序完成

**Step 结构：**（纯数据容器，不再 implements Criterion）
- `criteria` (Criterion) — 完成条件
- `failureCriteria` (Criterion, 可选) — 步骤级失败条件（优先于 defaultCriteria 的 failureCriteria）
- `evaluate(player, ctx)` — 求值辅助方法
- `evaluateFailure(player, failCtx, defaultCriteria, defaultFailCtx)` — 失败判定辅助方法
- `expand()` — 递归展开所有子条件的语法糖，返回新 Step

**QuestProgress 结构：**
- `questId` — 任务 ID
- `attemptId` — 本次尝试 UUID，用作完成提交的幂等来源
- `questSnapshot` (Quest) — 开始任务时冻结的**原始未展开** Quest 定义
- `currentStepIndex` — 当前步骤索引
- `questStartTime`, `currentStepSessionStartTime`, `previousSessionsStepDurationsMillis` — 总耗时与分段计时，断线期间不计入当前步骤
- `stepLinesRidden` — 每步乘坐过的线路名，用于完成详情
- `criteriaState`, `failureCriteriaState`, `defaultFailureCriteriaState` (StepState) — 当前步骤的可变状态
- `expandedCurrentStep`, `expandedDefaultCriteria` (transient) — 运行时从 questSnapshot 展开的缓存

**限制：** 当前一个玩家同一时间只能进行一个任务（`QUEST_ONLY_ONE_AT_A_TIME`）。

### 3.9 依赖关系

| 依赖 | 版本 | 用途 |
|------|------|------|
| Fabric Loader | 0.17.2 | 模组加载器 |
| Fabric API | 0.97.3+1.20.4 | 事件/命令/网络 API |
| Minecraft Transit Railway (MTR) | 4.0.0 | 列车/车站/线路系统 |
| SGUI (Polymer) | 1.4.2+1.20.4 | 服务端 GUI 框架 |
| Gson | Minecraft 内置 | JSON 序列化 |
| Java HttpClient | JDK 17 | Web API 同步、排行、历史、完成提交 |

### 3.10 Mixin 注入点

| Mixin / Accessor | 目标 | 注入点 | 功能 |
|------------------|------|--------|------|
| `SimulatorMixin` | `org.mtr.core.simulation.Simulator` | `tick()` RETURN | 将 MTR 模拟器中的站点/线路/乘客数据同步到 `TscStatus` |
| `ServerPlayerMixin` | `net.minecraft.server.level.ServerPlayer` | `teleportTo` 两个重载 | 标记传送事件到 `GenerationStatus`（用于 `TeleportDetectCriterion`） |
| `ScoreboardCommandMixin` | `net.minecraft.server.commands.ScoreboardCommand` | `setScore` | 兼容旧版：`mtrq_quest_complete` 计分板目标触发 `legacy_trigger_<score>` |
| `SidingAccessor` / `VehicleAccessor` / `VehicleSchemaAccessor` | MTR 内部类型 | Accessor | 暴露列车、车辆 schema、siding 信息给 `TscStatus` |

---

## 四、JSON Schema 与实际实现的差异

`docs/json_schema.txt` 中定义的 schema 与代码实际支持的模型存在差异，应以代码为准。

---

## 五、外部 API

项目引用了 MTR 系统地图 API（`docs/system_map_api.txt`），用于获取车站和线路数据：

- **端点：** `GET https://letsplay.minecrafttransitrailway.com/system-map/mtr/api/map/stations-and-routes?dimension=0`
- **用途：** 在代码中未直接调用此 API；模组通过 Mixin 直接从 MTR 的 `Simulator` 实例读取数据。此 API 可用于外部工具（如任务编辑器）获取站点/线路列表。

---

## 六、当前设计特点 & 待关注事项

### 设计亮点
1. **条件系统高度可组合** — 通过 And/Or/Not/Latching/RisingEdge 等逻辑条件的嵌套组合，能表达复杂的游戏逻辑
2. **定义/状态完全分离** — Criterion 为纯不可变定义，运行时状态存储在外部 StepState 容器中（通过 CriterionContext 按树路径寻址），支持断线重连后无缝继续
3. **任务快照机制** — 玩家开始任务时冻结 Quest 定义副本（questSnapshot），后续修改不影响进行中的任务
4. **语法糖展开** — RideLineToStation 等复合条件在运行时惰性展开为基础条件树，JSON 层保持简洁的高层定义
5. **解耦设计** — 核心逻辑（`QuestDispatcher`）与 Minecraft 特定逻辑（通知、GUI）分离，通过 `IQuestCallbacks` 接口连接
6. **MTR 集成非侵入** — 通过 Mixin + 共享状态（`TscStatus`）实现，不修改 MTR 代码
7. **签名保护** — 任务/分类的远程写入需要 HMAC-MD5 签名，防止滥用
8. **统一 Gson 配置** — `NQuestGson` 统一注册 CriteriaRegistry 工厂，QuestPersistence、QuestProgressPersistence、RankingApiClient、PendingCompletions 共享同一 Gson 实例
9. **可审计事件日志** — 任务生命周期事件按天写入服务端文本日志，支持时区、保留天数和失败原因类型展开

### 待关注事项
1. **JSON Schema 过时** — `docs/json_schema.txt` 未反映代码中的全部条件类型和字段，需要更新
2. **单任务限制** — 玩家同时只能进行一个任务，`activeQuests` 虽为 Map 但实际被限制为单条目
3. **QuestHistoryScreen 总数估算** — 历史记录无法获取精确总数，使用 `99999` 作为占位值
4. **TscStatus 同步时序** — 更新请求（tick 5）和任务推进（tick 15）之间有 0.5 秒间隔，依赖 MTR Simulator 在此期间完成 tick
5. **远端 API 降级** — 资格检查失败时默认允许开始任务；完成提交失败时依赖 `pending_completions.jsonl` 后续重放
6. **QuestCompletionDetailScreen 初始化顺序** — `rowContentStarts = 1` 在 `init()` 之后才设置
7. **所有 GUI 文本为英文硬编码** — 无 i18n 支持
8. **preTouchDescriptions()** — 通过临时 expand() 预加载 MTR 站名的异步解析
9. **questSnapshot 存储开销** — quest_sessions JSON 中现在包含完整 Quest 定义副本；活跃任务数较少（当前限制为 1）所以影响可控


---


## Quest 可见性状态

在 `Quest` 模型上有一个 `status` 枚举字段：

```java
public enum QuestStatus { PRIVATE, STAGING, PUBLIC }
```

- **[Quest.java](src/main/java/cn/zbx1425/nquestmod/data/quest/Quest.java)** — `getEffectiveStatus()` 在旧文件缺少 `status` 时返回 `PUBLIC`，保持向后兼容
- **可见性规则** — `PUBLIC` 对所有玩家可见；`STAGING` 仅调试模式可见；`PRIVATE` 需要调试模式且玩家 UUID 在 `creators` 中
- **[QuestDispatcher.java](src/main/java/cn/zbx1425/nquestmod/data/QuestDispatcher.java)** 的 `startQuest()` — 若玩家不可见该 Quest，则抛出 `QuestException.Type.QUEST_NOT_PUBLISHED`
- **sgui/QuestListScreen** — 普通玩家只看到 `PUBLIC` Quest；调试/作者视角可看到更多状态并以特殊样式标注

---

## Quest 作者调试模式

引入一个 **per-player 调试标志**，通过命令开关控制。
- 在 `QuestDispatcher` 中维护一个 `Set<UUID> debugPlayers`（仅内存，不持久化——调试状态不应跨重启保留）
- `/nquest debugMode [participant]`（权限等级 2）— 切换调试模式开关
- 执行后向玩家发送聊天消息确认当前状态

---

## Criterion 定义/状态分离重构 (v0.5.0)

将 Criterion 从"定义+状态一体"改为"纯定义 + 外部状态容器"模式，支持断线重连后无缝继续任务。

### 核心变更

1. **新增类：**
   - `StepState` — `Map<String, JsonObject>` 状态容器，按树路径寻址
   - `CriterionContext` — record(StepState, path)，求值时层层派生 `child()`
   - `NQuestGson` — 统一 Gson 配置，注册 CriteriaRegistry 工厂

2. **Criterion 接口变更：**
   - `isFulfilled(ServerPlayer)` → `evaluate(ServerPlayer, CriterionContext)`
   - `propagateManualTrigger(String)` → `propagateManualTrigger(String, CriterionContext)`
   - 新增 `default Criterion expand()` — 展开语法糖
   - 移除 `createStatefulInstance()` — 不再需要深拷贝

3. **有状态 Criterion 重构：**
   - 移除所有 `transient` 状态字段和拷贝构造函数
   - 状态通过 `CriterionContext` 读写到外部 `StepState`

4. **语法糖 Criterion：**
   - `RideLineToStationCriterion` / `RideToStationCriterion` 的 `evaluate()` 抛 UnsupportedOperationException
   - 核心逻辑移至 `expand()` 返回展开后的 Criterion 树

5. **Step 重构：**
   - 移除 `implements Criterion`，改为纯数据容器 + `evaluate()`/`evaluateFailure()`/`expand()` 辅助方法

6. **QuestProgress 重构：**
   - 新增 `questSnapshot`（原始未展开 Quest 定义，开始任务时冻结）
   - 新增 `criteriaState` / `failureCriteriaState` / `defaultFailureCriteriaState`（StepState）
   - 新增 `transient expandedCurrentStep` / `expandedDefaultCriteria`（运行时缓存）
   - 移除旧的 `transient currentStepStateful` / `defaultCriteriaStateful`

7. **QuestDispatcher 重构：**
   - `startQuest()` 冻结 questSnapshot 和初始化 StepState
   - `tryAdvance()` 惰性展开 + CriterionContext 求值
   - `advanceQuestStep()` 步骤完成时重置 StepState 并清除展开缓存
   - `updatePlayers()` / `triggerManualCriterion()` 使用 `progress.questSnapshot` 而非全局 `quests` map

8. **持久化适配：**
   - `QuestPersistence`、`QuestProgressPersistence`、`RankingApiClient` 和 `PendingCompletions` 统一使用 `NQuestGson.INSTANCE`
   - quest_sessions JSON 中的 QuestProgress 可完整序列化（questSnapshot + StepState），断线重连后恢复

### 断线重连流程

```
玩家断线 → Gson 序列化 QuestProgress（questSnapshot 原始形式 + StepState 路径状态）→ quest_sessions JSON
玩家重连 → 反序列化 → expandedCurrentStep = null（transient）
下一秒 tryAdvance → expand() 重建展开树 → StepState 中的路径仍有效 → 无缝继续
```
