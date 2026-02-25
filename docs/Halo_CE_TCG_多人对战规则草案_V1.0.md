# Halo: Combat Evolved TCG 多人对战规则草案（V1.0）

> 设计目标：在不推翻现有2人核心机制（3 Lane、护盾、Noob Combo、资源曲线）的前提下，扩展到 3-4 人对战。

## 1. 模式与人数

- **自由混战（FFA）**：3-4人，各自为战。
- **双人团队战（2v2）**：4人，固定队友。
- 推荐首发：先实现 FFA（规则最直接），再扩展 2v2。

## 2. 基础参数（多人版）

- 初始基地生命：
  - 3人局：`24`
  - 4人局：`22`
- 起手：`5` 张。
- 套牌：沿用40张构筑规则。
- 资源：沿用 `Supply 1~10` 与 `Battery`。
- 先手平衡：第一位玩家首回合不抽牌，最后一位玩家首回合额外抽1张（补偿）。

## 3. 座次与攻击范围

玩家按顺时针入座，定义：
- `左邻` 与 `右邻` 为“相邻玩家”。
- 默认攻击范围：你的单位只能攻击**相邻玩家**所在战场。
- 战术牌默认目标也仅限相邻玩家（除非标注“全体敌人”）。

这样能避免4人局中“全桌同时集火一人”导致体验崩坏。

## 4. 战场结构（推荐：共享Lane）

- 全桌共享 `Alpha / Bravo / Charlie` 三条 Lane。
- 每名玩家在每条 Lane 仍有自己的前后排槽位（上限不变）。
- 一个 Lane 中可同时出现多个玩家单位。

### 拦截规则（多人版）

当你攻击某玩家时：
- 只检查**该目标玩家**在该 Lane 的前排是否存在。
- 若存在，必须先打其前排。
- 其他玩家单位不会替目标“代挡”。

## 5. 回合顺序

- 采用固定顺时针回合：P1 → P2 → P3 → P4。
- 每位玩家完整执行自己的4阶段（抽牌与充能→部署→交战→结束）。

### 状态持续时间定义（多人关键）

- “直到你的下回合开始”：以拥有者回合计。
- “直到目标玩家下回合结束”：以目标玩家回合计。
- `EMP` 等控制效果建议记录为绝对回合号（如 `cannotAttackUntilTurn = X`）。

## 6. 胜利条件（多人版）

### FFA
- 玩家基地生命归零即出局。
- 场上最后存活玩家获胜。

### 2v2
- 任一队两名玩家均出局则该队失败。
- 可选快速胜利：任一队在同一轮结束时控制3条Lane，连续2轮则直接获胜。

## 7. Lane 控制重定义（多人）

在你的回合结束时，某 Lane 满足以下条件你即“控制该 Lane”：
- 你的单位数严格大于任一对手在该 Lane 的单位数；且
- 你在该 Lane 前排至少有1个单位。

> 说明：多人场景不再使用“对手前排为空”作为必要条件，否则几乎无法触发控制胜。

## 8. 关键机制在多人中的修订

### Noob Combo
- 只对同一目标生效；每目标每回合最多触发1次（与2人版一致）。

### Hijack
- 只能夺取你当前正在交战目标玩家的载具。
- 不能越过距离去夺取非相邻玩家载具。

### Friendly Fire
- “该Lane所有单位”指该Lane内所有玩家单位（含你、盟友、第三方）。

### Infect
- 击杀任意敌方玩家单位都可触发；衍生物放置在你自己的槽位。

## 9. 外交与节奏限制（推荐）

为降低“王者制造（Kingmaking）”与过度围殴：

- **同一回合基地直伤上限**：你对同一玩家基地造成的直接伤害最多 `8`。
- **战术连发上限**：每回合最多打出 `2` 张战术牌。
- **清场保护窗**：全场清场类战术（如 Glassing Beam）每位玩家每2回合最多1次。

## 10. 2v2 团队专项规则

- 队友不共享手牌与资源。
- 队友可互相选择为“友方目标”（治疗、增益可作用队友单位）。
- 伤害与击杀归属按效果来源玩家计算。
- 回合顺序建议交错：A1 → B1 → A2 → B2，减少单队连动爆发。

## 11. UI/引擎实现建议（Java）

### 目标过滤新增维度

- `TargetRelation`: `SELF | ALLY | ENEMY | ANY`
- `RangeRule`: `ADJACENT_ONLY | GLOBAL`

### GameContext 增量字段

- `List<Player> turnOrder`
- `Map<Player, Player> leftNeighbor / rightNeighbor`
- `boolean isTeamMode`
- `Map<Player, Integer> baseDamageDealtThisTurnToTarget`

### 事件模型建议

- 事件中追加：`sourcePlayerId`、`targetPlayerId`、`sourceLane`。
- 区分：`turnIndex`（全局）与 `ownerTurnCount`（玩家私有回合计数）。

## 12. 推荐上线顺序

1. 先做 3人 FFA（共享Lane + 相邻攻击范围）。
2. 再做 4人 FFA（加入基地直伤上限与战术连发上限）。
3. 最后做 2v2（加入队友目标与交错回合）。

## 13. 全域作战协议（All-Domain Warfare System）

目标：把多人局从“平面卡牌对撞”升级为“轨道层 + 地表层”协同战争。玩家扮演战区指挥官，在制天权、地面控制、任务目标间做取舍。

### 13.1 双层战场结构与同步

- 战场分为两层：
  - **轨道层（High Orbit）**：部署战舰、轰炸机、轨道设施。
  - **地表层（Surface Operations）**：沿用三条 Lane 的前后排作战。
- 每条地表 Lane 对应一个轨道扇区（同名 Alpha/Bravo/Charlie），形成“一对一映射”。
- 跨层交互通过“同步窗口”处理：在交战阶段开始前，先结算轨道指令，再进入地表战斗。

### 13.2 投送规则（手牌→轨道→地面）

- 单位上场新增两段式流程（适用于 `ODST` / `DROP_POD` / 具空投标记单位）：
  1) **装载（Load）**：从手牌进入己方轨道单位机库。
  2) **投送（Drop）**：从机库投送到地表指定 Lane 的前排或后排空位。
- 消耗公式建议：
  - `LoadCost = max(1, ceil(UnitCost * 0.5))`
  - `DropCost = 1`（若战舰有“快速投送”则可降为0）
  - 同回合总消耗：`Total = LoadCost + DropCost`
- 机库限制：每艘舰船每回合最多投送 `Hangars` 数量的单位。
- 绕拦截规则：通过 Orbital Drop 进入地面时不触发“前排优先拦截”，但落地后仍受正常目标规则约束。

### 13.3 指引打击与视野共享（Target Painting & Radar）

- 轨道单位默认只能攻击轨道层目标。
- 若要打击地表目标，必须满足以下任一条件：
  - 同 Lane 存在己方“雷达覆盖”地面单位；或
  - 目标已被 `Painted`（标记）。
- 指引打击（Target Painting）：地面步兵消耗一次行动，对目标施加 `PAINTED` 至回合结束。
- `PAINTED` 效果：本回合来自轨道层的对该目标打击 `+50%`（向下取整），或命中判定 +1 档（若启用命中制）。

### 13.4 防空工事与对天反制（AA Emplacements）

- 地面可部署 `AA` 工事（如 M41 炮塔），其合法目标仅限轨道单位。
- `AA` 不受“地面前排拦截”限制，按射界直接选择轨道目标。
- 失去制天权的一方仍可通过 `AA` 阻断对手投送与轰炸链。

### 13.5 舰船属性模板（Vessel Stat Template）

- `Hull`（船壳）：舰船生命值。
- `Shields`（护盾）：先于 Hull 结算。
- `ShieldHardening(X)`：每回合首次受伤时减免 `X` 点。
- `PointDefense`（点防御）：自动拦截小型导弹/登舰舱（每回合有拦截次数上限）。
- `Hangars`（机库）：决定每回合可投送单位数。
- `Mass`（体积）：用于旗舰坠落伤害计算。

### 13.6 旗舰坠落结算（Capital Ship Down）

- 当轨道层“旗舰”被摧毁时，立刻触发坠落判定。
- 对应地表 Lane 受到坠落压制伤害：
  - `CrashDamage = ceil(Mass / 2)`
  - 该 Lane 全体单位（含友军）受 `CrashDamage` 点 `TRUE` 伤害。
- 若启用偏移规则：50% 概率扩散到相邻 Lane，伤害减半（向下取整，至少1）。

### 13.7 宏观目标系统（Objective-Based Victory）

- 新增可选战略任务（与基础胜利条件并存）：
  - **先行者终端（The Terminal）**：连续3回合控制指定地表 Lane；奖励“立即胜利”或“轨道激光无限使用权（二选一，建房前声明）”。
  - **旗舰击沈（Capital Ship Down）**：击毁敌方旗舰；该敌方玩家所有单位获得 `ROUTED`（攻击减半）直到其下回合结束。
  - **地表撤离（Evacuation）**：在基地被摧毁前，成功撤离5个单位；该玩家按“撤离成功”结算（可记平局或额外战功）。

### 13.8 阵营协同风格（跨阵营同场）

- 本模式采用“**玩家绑定阵营**”而非“阵营天然敌对”。
- 同一战场可同时存在 UNSC、Covenant、Flood、Forerunner 单位。
- 敌我关系由玩家决策与协议决定，不由单位阵营自动锁定。
- 结算原则：效果中的“敌方/友方”以**玩家关系**判定，不以单位阵营判定。
- 最终胜利保持唯一：多人 FFA 仅最后1名存活玩家获胜。

### 13.9 星际事件流（每5个全局回合）

- 每逢 `turnIndex % 5 == 0`，触发1个随机星际事件（持续到下个全局回合结束）：
  - **断片预警**：轨道层单位攻击力翻倍。
  - **空间跃迁**：所有轨道单位随机交换 Lane。
  - （可扩展）**通信黑障**：本轮禁止发起 Truce。
  - （可扩展）**引力潮汐**：投送费用 `+1`。

Java 建议：
- 新增 `BattleLayer { ORBIT, SURFACE }`、`OrbitalLaneState`、`VesselState`。
- 新增状态 `PAINTED`、`ROUTED`，并扩展 `StatusResolver` 的跨层处理。
- 事件总线新增 `OnOrbitalStrike`、`OnDrop`、`OnCapitalShipDestroyed`、`OnGalacticEvent`。

## 14. 深度扩展提案（V1.1 增补）

以下规则用于增强多人局的地图博弈、社交博弈和反围殴体验。建议作为可开关模块（房间规则）上线。

### 14.1 战场动态：地标争夺（Map Control & Power Weapons）

- 每条 Lane 额外拥有一个 `Landmark` 槽位，放置1张地标卡（开局随机或按地图库抽取）。
- 地标结算时机：在交战阶段开始前，先检查各 Lane 控制者并结算地标收益。
- 控制判定沿用第7条 Lane 控制规则。
- 例子：
  - `Valhalla Base (Alpha)`：本回合你造成的 `BALLISTIC` 伤害 `+1`。
  - `Guardian Tower (Bravo)`：你获得 `+1 Supply`（本回合可用，超出上限可临时溢出1点）。
  - `Heavy Rack (Charlie)`：从“重型武器池”随机获得1张武器牌到手牌。
- 刷新规则：每经过 `X=2` 个全局回合，未被控制过的地标可重掷（可选规则）。

Java 建议：
- `Lane` 增加 `Landmark landmark` 字段。
- `Landmark` 提供 `applyEffect(Player controller, GameContext ctx)`。
- 在 `turnIndex % X == 0` 时触发 `maybeRotateLandmark()`。

### 14.2 社交机制：暗标重组（Truce）

- 提议时机：抽牌与充能阶段，主动玩家可向相邻玩家发起 `Truce`（可附带暗扣展示1张手牌）。
- 若对方接受，则在“当前回合”内双方互为不可选目标（单位攻击与单体战术均不可指向彼此）。
- 撕约判定：若一方通过群体效果或非法目标选择对停火对象造成伤害，记为 `Breach`。
- 违约惩罚：违约者在其下个回合 `SupplyCap = floor(SupplyCap / 2)`（最少1）。
- 频率限制：每名玩家每回合最多发起1次停火；同一对玩家连续两回合不能重复签约（冷却1回合）。

Java 建议：
- `DiplomacyState`：记录 `proposerId/targetId/startTurn/endTurn/breached`。
- 目标选择阶段增加 `isTruceBlocked(source, target)` 校验。

### 14.3 战斗细节：高度差与掩体（Elevation & Cover）

#### Cover（掩体值）
- `Cover(X)` 可来自单位、附件或地标。
- 受到伤害时，若伤害来源不是 `Ordnance`，先由 `Cover` 抵消 `X` 点，再进入护盾/生命结算。
- `Cover` 默认每回合刷新（静态掩体），一次性掩体需在牌面注明“触发后移除”。

#### High Ground（高地优势）
- 判定：单位在 Backline，且同 Lane 自己 Frontline 至少有1个友军单位。
- 效果：该单位攻击相邻玩家 Backline 时，可无视“必须先打前排”的拦截规则。
- 限制：`Ordnance` 和“全体目标”效果不因高地改变目标合法性。

Java 建议：
- `UnitState` 增加 `coverValue`、`hasHighGround`。
- 伤害流水线新增 `applyCoverMitigation()` 步骤，位于护盾结算前。

### 14.4 资源进阶：战功等级（Commendation）

- 获得规则：
  - 击杀敌方单位：`+1 Commendation`
  - 摧毁敌方载具：`+2 Commendation`
- 阶梯奖励（达到即永久生效）：
  - `Level 1 (3点)`：你打出的 `Infantry` 费用 `-1`（最低1）。
  - `Level 2 (7点)`：你的 `Hero Unit` 进场时获得 `Overshield(2)`。
  - `Level 3 (12点)`：获得1次免费的 `Orbital Strike`（生成临时战术牌，使用后移除）。
- 平衡限制：每回合最多获得 `3` 点战功（防止连锁击杀滚雪球过快）。

Java 建议：
- `PlayerState` 增加 `commendationPoints`、`commendationLevel`、`freeOrbitalCharges`。
- 在 `onUnitKilled` 与 `onVehicleDestroyed` 事件中更新并检查升级。

### 14.5 多人状态：Suppressed / Retribution / Marked

#### Suppressed（压制）
- 持续：直到目标拥有者下回合结束。
- 效果：被压制单位不能发动 `Hijack` 与主动 `Ability`。

#### Retribution（复仇）
- 触发：若你在一个对手回合内基地累计受伤 `>5`。
- 效果：你下个回合打出的所有 `Ordnance` 费用视为 `0`（每回合最多前2张生效）。
- 清除：该回合结束后移除。

#### Marked（标记）
- 持续：1个全局回合。
- 效果：被标记单位受到任何来源伤害 `+1`。
- 上限：同一单位最多叠加1层 `Marked`。

Java 建议：
- `StatusType` 扩展：`SUPPRESSED, RETRIBUTION, MARKED`。
- 统一通过 `StatusInstance{type, ownerId, expiresAtTurn}` 管理持续时间。

### 14.6 与现有规则的兼容优先级

- 伤害结算优先级建议：
  1) 目标合法性（Truce/距离/拦截）
  2) 基础伤害与类型
  3) `Cover` 减免
  4) `Plasma/Ballistic/Headshot/Marked` 修正
  5) 护盾与生命结算
  6) 击杀、战功、感染等触发
- 若规则冲突，优先级：`房间模块规则 > 卡面文本 > 通用规则`。

### 14.7 上线建议（多人增强模块）

1. 先开 `Landmark + Cover/High Ground`（低社交成本，提升战术深度）。
2. 再开 `Commendation + Marked`（增强追击和反滚雪球）。
3. 最后开 `Truce + Retribution`（提升博弈但实现复杂度最高）。

---

## 附：多人模式平衡参数建议（可调）

- 基地生命：3人24 / 4人22。
- 手坑与清场占比：卡池内清场建议 ≤ 8%。
- 高爆发牌（6费以上）数量建议每套 ≤ 6 张。
- `Battery` 转化维持每回合最多1次。

> 如果后续实测出现“拖太久”，优先把基地生命下调2点；
> 如果出现“太容易被秒”，优先把基地直伤上限从8下调到6并提高防御牌占比。
