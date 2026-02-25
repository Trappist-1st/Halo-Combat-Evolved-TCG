# Halo: Combat Evolved TCG
# 规则实现与网络支持现状评估（V1.0）

> 评估时间：2026-02-25
> 范围：当前 `game / combat / net` 代码实现状态

---

## 1. 结论概览

- 回合主循环已具备完整四阶段骨架（抽牌充能 / 部署 / 交战 / 结束），并能在 1v1 / FFA / 2v2 下轮转。
- 网络层已升级为房间制 TCP + JSON 协议，支持建房、列房、入房、房间内状态广播。
- 你关心的规则里，部署疲劳与 Noob Combo 已落地；控制占领与胜利条件主线可用；状态标记体系已有基础但仍可扩展。

---

## 2. 你提出的 5 条规则逐项评估

## 2.1 部署疲劳机制

状态：**已实现（基础版）**

现状：
- 单位部署时记录 `summonedTurnIndex`。
- 在 `declareAttack(...)` / `attackBase(...)` 中校验：
  - 若 `summonedTurnIndex == globalTurnIndex` 且无 `DROP_POD`，禁止攻击。

备注：
- 已支持 `DROP_POD` 例外。

建议：
- 为单位实例增加 `summonedTurnIndex` 或 `hasSummoningSickness`。
- 在 `declareAttack(...)` 前增加校验：
  - 若单位是本回合部署且无 `DROP_POD`，则禁止攻击。

---

## 2.2 协同规则（Noob Combo）

状态：**已实现（基础版）**

规则目标：
- 同目标同回合先受 `PLASMA` 并获得 `PLASMA_TAG`，再受 `BALLISTIC` 时本次伤害 ×2（每目标每回合最多 1 次）。

现状：
- 已新增 `UnitStatusStore`，记录 `plasmaTaggedTurnIndex` 与 `noobComboTriggeredTurnIndex`。
- `declareAttack(...)` 中对 `BALLISTIC` 攻击判定 Noob Combo，并通过 `DamageContext.finalDamageMultiplier=2` 实现“最终伤害×2”。
- `PLASMA` 命中后写入标记并发布 `PLASMA_TAG_APPLIED`。

边界待完善：
- 目前 combo 的“每目标每回合一次”已满足，但缺少可视化日志聚合与回放注释。

---

## 2.3 控制与占领完整逻辑

状态：**部分实现（可用但不完整）**

已实现：
- `controlledLaneCountForPlayer(...)` 与 `controlledLaneCountForTeam(...)` 已按“己方单位数 > 敌方单位数 且 敌方前排为空”判断。
- 2v2 下可按队伍聚合计数。

待完善：
- 触发时机目前偏“回合末/关键动作后即计算”，与所有卡牌持续效果、临时控制效果的联动规则尚未统一。
- 未实现“控制变更事件细粒度差异（哪条 lane 从谁转移到谁）”。

建议：
- 增加每条 Lane 的“上一次控制方”缓存，发布更细粒度控制变化事件。
- 将控制计算统一挂到 EndStep 的固定窗口，避免多处重复触发造成歧义。

---

## 2.4 胜利条件具体实现

状态：**已实现主线 + 优先级已固化**

已实现：
- 歼灭胜：
  - 1v1 / FFA：最后存活玩家获胜。
  - 2v2：最后存活队伍获胜。
- 全面控制胜：
  - 个人模式与 2v2 均有“控制 3 路并连续 2 次”判定。
- **胜利优先级（已固化）**：见 `VictoryReason` 与 `GameStateManager.evaluateWinConditions`。
  - **优先级 1**：歼灭（最后存活玩家/队伍）。
  - **优先级 2**：全面控制（3 路连续 2 次）。
  - 同一时刻多条件满足时只按上述顺序取第一条；事件中 `reason` 为枚举名（如 `LAST_PLAYER_STANDING`、`FULL_CONTROL_STREAK`）。无人存活时 `reason = NO_ALIVE_PLAYERS`，无胜者。

边界待完善：
- 多人同回合联动（异步事件）下的“并发达成”裁决规则未显式定义（当前按单次 evaluateWinConditions 内顺序判定）。

---

## 2.5 状态标记管理（如 damagedLastOpponentTurn）

状态：**部分实现（基础标记已落地）**

现状：
- 新增 `UnitStatus` / `UnitStatusStore`，已支持以下标记：
  - `summonedTurnIndex`
  - `plasmaTaggedTurnIndex`
  - `noobComboTriggeredTurnIndex`
  - `attackedTurnIndex`
  - `damagedTurnIndex` + `damagedByPlayerId`
  - `cannotAttackUntilTurn`
  - `cannotMoveUntilTurn`
  - `hasCamoThisTurn`

影响：
- 无法稳定支持部署疲劳、Noob Combo、EMP/CAMO 等关键机制。

待完善：
- 目前仅部分标记参与规则判定（部署疲劳、Noob Combo、攻击次数、Camo 开场刷新）。
- 仍需扩展到 `EMP / cannotMove` 等完整规则闭环。

---

## 3. 网络支持现状评估

状态：**基础可用（房间制 MVP）**

已实现：
- 协议：TCP + JSON Line。
- 服务端：`NetworkGameServer`（房间路由）。
- 客户端 SDK：`NetworkGameClient`。
- 房间管理：`RoomManager`、`GameRoom`、`RoomSummary`。
- 会话绑定：`JOIN_ROOM { roomId, playerId }`（`JOIN` 兼容首房间）。
- 状态同步：命令成功后在房间内 `STATE_BROADCAST`，并支持 `STATE` 拉取。
- 已支持的动作命令：
  - `ADVANCE_PHASE`
  - `END_TURN`
  - `DEPLOY`
  - `CONVERT_BATTERY`
  - `ATTACK`
  - `ATTACK_BASE`

当前缺口：
- 无鉴权（仅凭 `JOIN playerId`）。
- 房间已支持，但暂不支持房间销毁/持久化。
- 无断线重连状态恢复（含事件序列续传）。
- 无命令幂等与重放保护。
- 广播为全量快照，缺少增量事件流。

建议优先级：
1. 鉴权 + 会话令牌
2. 房间管理（多局并行）
3. 断线重连 + 序列号
4. 增量同步（事件流 + 快照兜底）

---

## 4. 已有实现落点（便于你快速定位）

- 回合阶段与动作入口：`src/main/java/com/haloce/tcg/game/GameStateManager.java`
- 多模式建局（1v1/FFA/2v2）：`src/main/java/com/haloce/tcg/game/GameEngine.java`
- 伤害流水线：`src/main/java/com/haloce/tcg/combat/DamageResolver.java`
- 网络服务端：`src/main/java/com/haloce/tcg/net/NetworkGameServer.java`
- 状态快照：`src/main/java/com/haloce/tcg/net/GameSnapshotFactory.java`

---

## 5. 建议下一步（可直接进入开发）

- 第一批（规则闭环）：
  - 部署疲劳（含 `DROP_POD` 例外）
  - Noob Combo
  - 统一 Unit 状态标记存储与生命周期
- 第二批（多人网络可用性）：
  - 房间系统 + 鉴权
  - 断线重连 + 状态恢复
- 第三批（可观测性）：
  - 事件日志持久化
  - 回放工具（按事件序回放）
