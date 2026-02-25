# Halo: Combat Evolved TCG
# 游戏引擎与状态管理设计（V1.0）

> 目标：补齐“可开局、可推进完整四阶段回合、可管理玩家与战场、可检查胜负、可网络同步”的核心运行骨架。

---

## 1. 覆盖范围

本次实现包含以下模块：

- `GameEngine`：多模式对局装配与初始化入口。
- `GameStateManager`：全局状态管理器（回合/轮次/阶段/玩家/战场/胜负）。
- `TurnExecutor`：回合阶段推进器。
- `PlayerState`：玩家状态管理（基地生命、牌区、资源、控制统计）。
- `BattlefieldState`：三路战场状态（Lane 与前后排容量）。
- `NetworkGameServer`：网络会话服务器与状态广播。
- `RemotePlayerRegistry`：远程玩家会话绑定与在线管理。

---

## 2. 核心类职责

## 2.1 GameEngine

包路径：`com.haloce.tcg.game.GameEngine`

职责：
- 接收 `PlayerSetup` 或 `PlayerSeat` 列表。
- 调用 `DeckValidator` 校验卡组合法性。
- 将 `DeckDef` 转换为运行时 `CardInstance`，并进行洗牌。
- 创建 `GameStateManager` 并执行 `startGame()`。

当前约束：
- 支持 `DUEL_1V1`、`FFA`、`TEAM_2V2`。
- `TEAM_2V2` 强制 4 人、2 队、每队 2 人。
- 默认基地生命 `30`。
- 洗牌使用固定种子随机数，便于复现与调试。

## 2.2 GameStateManager

包路径：`com.haloce.tcg.game.GameStateManager`

职责：
- 管理对局生命周期：`NOT_STARTED -> RUNNING -> FINISHED`。
- 管理全局计数：
  - `globalTurnIndex`
  - `roundIndex`
  - `activePlayerCursor`
- 管理当前阶段：`DRAW_RECHARGE / DEPLOYMENT / SKIRMISH / ENDSTEP`。
- 管理玩家顺序与活动玩家。
- 驱动完整回合流程（抽牌充能 → 部署 → 交战 → 结束）。
- 提供核心动作接口：
  - `deployUnitFromHand(...)`
  - `convertToBattery(...)`
  - `declareAttack(...)`
  - `attackBase(...)`
  - `advancePhase()`
  - `damageBase(...)`
  - `endTurn()`
- 通过 `EventBus` 发布关键事件，保证规则链可扩展。

胜利检查：
- **歼灭胜（DUEL/FFA）**：最后存活玩家获胜。
- **歼灭胜（2v2）**：最后存活队伍获胜。
- **全面控制胜（DUEL/FFA）**：当前玩家控制 3 路并连续 2 次达成。
- **全面控制胜（2v2）**：当前队伍控制 3 路并连续 2 次达成。

## 2.3 TurnExecutor

包路径：`com.haloce.tcg.game.TurnExecutor`

职责：
- 封装阶段推进入口 `advancePhase()`。
- 与 `GameStateManager` 配合驱动阶段切换：
  - `DEPLOYMENT -> SKIRMISH`
  - `SKIRMISH -> ENDSTEP`
  - `ENDSTEP -> 下一玩家回合`

## 2.4 PlayerState

包路径：`com.haloce.tcg.game.PlayerState`

管理数据：
- 身份与生存：
  - `playerId`
  - `baseHealth`
- 牌区：
  - `library`（牌库）
  - `hand`（手牌）
  - `discardPile`（弃牌堆）
- 资源：
  - `supplyCap`
  - `currentSupply`
  - `battery`
  - `batteryConvertedThisTurn`
- 占领统计：
  - `controlledLaneCount`
  - `fullControlStreak`

核心行为：
- 抽牌：`draw(int count)`
- 费用支付：`spendResources(int supplyCost, int batteryCost)`
- Battery 转化（每回合一次）：`convertHandCardToBattery(...)`
- 基地受伤：`applyBaseDamage(int amount)`
- 记录 Lane 控制状态：`setLaneControl(int laneCount)`

## 2.5 BattlefieldState / LaneState / LaneBoardState

包路径：`com.haloce.tcg.game`

结构：
- `Lane`：`ALPHA / BRAVO / CHARLIE`
- `GameRow`：`FRONTLINE / BACKLINE`
- 每条 Lane 按玩家切分战场面板：
  - 前排上限 2
  - 后排上限 2

能力：
- 部署单位：`deploy(playerId, lane, row, card)`
- 移除单位：`removeUnit(instanceId)`
- 单位定位：`locateUnit(instanceId)`
- 统计：按 Lane + 玩家集合统计总单位/前排数量

控制判定（V1.1 实现）：
- 你方（或你方队伍）该 Lane 单位总数 > 对方（或敌方队伍）单位总数；
- 且对方（或敌方队伍）前排为空。

---

## 3. 回合执行流程（当前实现）

- 开局：双方各抽 `5`。
- 每回合开始（`DRAW_RECHARGE` 自动结算）：
  - `supplyCap +1`（上限 10）
  - `currentSupply = supplyCap`
  - 重置 Battery 转化标记
- 先手首回合不抽牌。
- 部署阶段（`DEPLOYMENT`）：
  - 可打出单位到指定 Lane/Row
  - 可执行每回合一次手牌转 Battery
- 交战阶段（`SKIRMISH`）：
  - `declareAttack(attacker, defender)` 触发攻击声明与伤害结算
  - `attackBase(attacker, targetPlayer)` 在无拦截单位时攻击基地
- 结束阶段（`ENDSTEP`）：
  - 结算 lane 控制与胜利条件
  - 轮转到下一存活玩家
  - 跨越完整一轮后 `roundIndex +1`

---

## 4. 事件联动接入点

`GameStateManager` 当前已发布以下关键事件（部分）：

- 生命周期：`GAME_STARTED`、`ROUND_STARTED`、`TURN_STARTED`、`TURN_ENDED`、`ROUND_ENDED`、`GAME_ENDED`
- 资源：`CARD_DRAWN`、`SUPPLY_CAP_INCREASED`、`SUPPLY_REFILLED`、`BATTERY_GENERATED`
- 阶段：`PHASE_DRAW_RECHARGE_*`、`PHASE_DEPLOYMENT_*`、`PHASE_SKIRMISH_*`、`PHASE_ENDSTEP_*`
- 部署与战斗：`UNIT_DEPLOYED`、`ATTACK_DECLARED`、`TARGET_LOCKED`、`DAMAGE_*`、`KILL_OCCURRED`
- 胜负：`LANE_CONTROL_UPDATED`、`BASE_DAMAGED`、`WIN_CONDITION_MET`

这保证后续可以通过监听器扩展：
- 抽牌替代效果
- 费用减免
- 进场触发
- 回合结束清理等规则

---

## 5. 网络与多人同步（新增）

已实现 TCP + JSON Line 协议，便于前后端分离：

- 服务端：`com.haloce.tcg.net.NetworkGameServer`
- 客户端：`com.haloce.tcg.net.NetworkGameClient`
- 远程玩家管理：`RemotePlayerRegistry`
- 状态同步：`GameSnapshotFactory`（按命令响应 + 广播）

协议命令：
- `JOIN { playerId }`
- `STATE`
- `ADVANCE_PHASE`
- `DEPLOY { cardInstanceId, lane, row }`
- `CONVERT_BATTERY { cardInstanceId }`
- `ATTACK { attackerInstanceId, defenderInstanceId }`
- `ATTACK_BASE { attackerInstanceId, targetPlayerId }`
- `END_TURN`

## 6. 与现有工程的集成

- `App` 已接入 `GameEngine`，可直接完成：
  - 卡池加载
  - 卡组校验
  - 创建对局并进入第一回合
  - 通过 `--server` 启动网络服务

---

## 7. 后续建议（下一阶段）

- 引入鉴权/令牌机制，限制会话冒充和越权命令。
- 增加网络断线重连与状态回放（事件序列恢复）。
- 引入 matchmaking / room 维度（当前为单局单服务端实例）。
- 增加更细粒度的动作合法性与回放日志存档。
- 补充单元测试：
  - 阶段推进与非法阶段动作
  - 多人轮转与淘汰跳过
  - 2v2 队伍胜利条件
  - 资源变化
  - Lane 容量、部署和攻击目标规则
