# Halo CE TCG - 星盟 (Covenant) 阵营机制详解 V1.0

## 目录
1. [阵营概述](#阵营概述)
2. [核心机制](#核心机制)
3. [管理器系统](#管理器系统)
4. [战术定位](#战术定位)
5. [代码集成](#代码集成)
6. [示例卡牌](#示例卡牌)
7. [平衡性分析](#平衡性分析)

---

## 阵营概述

### 设计理念
**Covenant (星盟)** 是《光环》宇宙中由多个外星种族组成的宗教军事联盟。其设计核心体现了：

- **狂热信仰**: 通过"信仰值"(Faith)系统驱动强力能力
- **种族等级制**: 精英/野猪人领导咕噜人/豺狼人，形成战场等级链
- **大规模部署**: "群体部署"快速填满战场
- **轨道优势**: 轨道玻璃化打击威慑敌方
- **不稳定性**: 大分裂事件导致内部火拼

### 阵营特色
- **资源系统**: 供应（Supply）+ 信仰（Faith）
- **等级制度**: 领袖 (Leader) > 精英 (Major) > 劣等兵 (Minor)
- **信仰机制**: 击杀获得信仰，损失单位惩罚信仰
- **轨道打击**: 玻璃化标记 (Glassing Mark) 系统
- **自杀攻击**: 咕噜人携带等离子手雷同归于尽

---

## 核心机制

### 1. 狂热等级系统 (Zealotry Rank System)

#### 等级划分

##### 1.1 领袖单位 (LEADER)
- **代表**: 先知 (Prophet)、仲裁者 (Arbiter)、舰队司令
- **特权**: 
  - Lane内所有劣等兵攻击力 +1
  - 可使用仪式超载 (Ritual Overdrive)
- **标签**: `LEADER`, `PROPHET`, `HIERARCH`

##### 1.2 精英单位 (MAJOR)
- **代表**: 精英战士 (Elite)、野猪人酋长 (Brute Chieftain)
- **特权**:
  - Lane内所有劣等兵攻击力 +1
  - 自身受到普通单位攻击时额外伤害 -1
- **标签**: `ELITE`, `BRUTE`, `MAJOR`, `ULTRA`

##### 1.3 劣等兵 (MINOR)
- **代表**: 咕噜人 (Grunt)、豺狼人 (Jackal)
- **特性**:
  - 有精英/领袖同Lane时: 攻击力 +1，受伤害 +20%
  - 领袖阵亡时: 士气崩溃，逃离战场
- **标签**: `GRUNT`, `JACKAL`, `MINOR`

#### 狂热加成计算

```java
// CovenantZealotryManager.java
public int zealotryAttackBonus(CardInstance unit, List<CardInstance> laneAllies) {
    // 劣等兵在有精英/领袖的Lane获得+1攻击
    return rankOf(unit) == CovenantRank.MINOR && hasMajorOrLeaderInLane(laneAllies) ? 1 : 0;
}

public double zealotryDamageTakenMultiplier(CardInstance unit, List<CardInstance> laneAllies) {
    // 劣等兵在有精英/领袖的Lane受伤害×1.2
    return rankOf(unit) == CovenantRank.MINOR && hasMajorOrLeaderInLane(laneAllies) 
        ? 1.20 
        : 1.0;
}
```

#### 士气崩溃 (Morale Collapse)

```java
// CovenantZealotryManager.java
public boolean shouldCollapse(CardInstance unit, boolean leaderDiedThisTurn) {
    // 领袖阵亡时，所有劣等兵逃离战场
    return leaderDiedThisTurn && rankOf(unit) == CovenantRank.MINOR;
}
```

**集成到 CombatHandler**:
```java
if (HandlerUtils.hasTag(dead, "LEADER") && HandlerUtils.hasTag(dead, "COVENANT")) {
    // 标记领袖阵亡
    List<CardInstance> minorUnits = battlefield.unitsInLane(dead.lane())
        .stream()
        .filter(u -> u.ownerPlayerId().equals(dead.ownerPlayerId()))
        .filter(u -> campaignManager.covenantZealotry().rankOf(u) == CovenantRank.MINOR)
        .toList();
    
    for (CardInstance minor : minorUnits) {
        // 移除所有劣等兵
        battlefield.removeUnit(minor.instanceId());
        emit(EventType.COVENANT_MORALE_COLLAPSE, ...);
    }
}
```

---

### 2. 信仰系统 (Faith System)

#### 信仰获取
- **击杀敌方单位**: +2 信仰
- **占领圣地**: +3 信仰/回合
- **信仰卡牌**: "先知祝福" +5 信仰

#### 信仰消耗
- **单位阵亡**: -1 信仰
- **单位撤退**: -2 信仰
- **仪式超载**: -10 信仰

#### 信仰值应用

##### 2.1 仪式超载 (Ritual Overdrive)
- **消耗**: 10 信仰
- **效果**: 本回合所有等离子武器不会过热
- **触发**: 回合结束时
- **代码实现**:
```java
// CovenantFaithManager.java
public boolean activateRitualOverdrive(String playerId, int globalTurnIndex) {
    int current = faith(playerId);
    if (current < 10) return false;
    faithByPlayer.put(playerId, current - 10);
    antiOverheatTurnByPlayer.put(playerId, globalTurnIndex);
    return true;
}

public boolean isOverheatSuppressedThisTurn(String playerId, int globalTurnIndex) {
    return antiOverheatTurnByPlayer.getOrDefault(playerId, -1) == globalTurnIndex;
}
```

##### 2.2 群体部署 (Mass Deploy)
- **消耗**: 5 信仰/单位
- **效果**: 填满选定Lane的所有空位（仅咕噜人Token）
- **限制**: 受人口上限约束

```java
// CovenantDeployManager.java
public List<String> massDeploy(
    PopulationManager popManager,
    Lane lane,
    String gruntTokenId,
    int availableFaith,
    int faithCostPerToken
) {
    int slots = popManager.remainingSlots(lane, LaneLayer.GROUND);
    int budgetByFaith = availableFaith / faithCostPerToken;
    int budgetByPop = Math.max(0, popManager.maxPop() - popManager.currentPop());
    int toSpawn = Math.min(slots, Math.min(budgetByFaith, budgetByPop));
    
    List<String> deployedBatch = new ArrayList<>();
    for (int i = 0; i < toSpawn; i++) {
        deployedBatch.add(gruntTokenId);
        popManager.addUnit(1, lane, false);
    }
    return deployedBatch;
}
```

##### 2.3 先知预言 (Prophet's Vision)
- **消耗**: 8 信仰
- **效果**: 查看对手手牌，选择1张弃掉
- **冷却**: 每局限用1次

---

### 3. 自杀攻击机制 (Suicide Attack)

#### 自杀咕噜人 (Suicide Grunt)
- **触发**: 玩家主动激活，或咕噜人生命值 ≤ 1
- **效果**: 对相邻所有单位（包括友军）造成 3 点伤害
- **特殊**: 不受护盾保护

```java
// CovenantDeployManager.java
public Map<String, Integer> sacrificeUnit(CardInstance unit, List<CardInstance> adjacentTargets) {
    if (unit == null || adjacentTargets.isEmpty()) return Map.of();
    
    Map<String, Integer> damageByTargetInstance = new LinkedHashMap<>();
    for (CardInstance target : adjacentTargets) {
        damageByTargetInstance.put(target.instanceId(), 3); // 固定3点伤害
    }
    return damageByTargetInstance;
}
```

#### 集成示例
```java
// CombatHandler - 自杀攻击触发
if (HandlerUtils.hasKeyword(unit, "SUICIDE_GRUNT")) {
    List<CardInstance> adjacents = HandlerUtils.getAdjacentUnits(unit, battlefield);
    Map<String, Integer> damageMap = campaignManager.covenantDeploy()
        .sacrificeUnit(unit, adjacents);
    
    for (var entry : damageMap.entrySet()) {
        DamageContext ctx = new DamageContext(...);
        DamageResult result = damageResolver.resolve(ctx);
        emit(EventType.SUICIDE_ATTACK_TRIGGERED, ...);
    }
}
```

---

### 4. 轨道优势系统 (Orbital Dominance System)

#### 玻璃化打击 (Glassing Strike)
- **机制**: 在敌方Lane放置"玻璃化标记"
- **效果**: 该Lane每有单位部署，立即受到 1 点穿透伤害
- **清除**: 标记在造成伤害后自动消耗

```java
// CovenantOrbitalDominanceManager.java
public void applyGlassingMark(String enemyPlayerId, Lane lane) {
    glassingMarksByEnemy
        .computeIfAbsent(enemyPlayerId, k -> new HashMap<>())
        .merge(lane, 1, Integer::sum); // 可叠加标记
}

public int onEnemyUnitSpawned(String enemyPlayerId, Lane lane) {
    int marks = glassingMarksByEnemy
        .getOrDefault(enemyPlayerId, Map.of())
        .getOrDefault(lane, 0);
    return marks > 0 ? 1 : 0; // 返回伤害值
}
```

#### 登陆艇攻击 (Boarding Craft)
- **触发**: 星盟战舰攻击敌方战舰
- **效果**: 在目标战舰上放置2个"登陆标记"
- **惩罚**: 每个标记使战舰攻击力 -1
- **清除**: 敌方玩家使用"损管小队"卡牌

```java
// CovenantOrbitalDominanceManager.java
public void boardingCraftHit(String targetShipInstanceId) {
    boardingTokensByShip.merge(targetShipInstanceId, 2, Integer::sum);
}

public boolean isShipDisabled(String targetShipInstanceId) {
    return boardingTokensByShip.getOrDefault(targetShipInstanceId, 0) > 0;
}
```

---

### 5. 大分裂机制 (Great Schism)

#### 触发条件
- **同时存在**: 精英 (Elite) 和 野猪人 (Brute) 单位
- **概率**: 每回合 10% 触发

#### 效果
- 随机选择1个星盟单位
- 该单位立即对相邻友军发动攻击
- 攻击后该单位变为"混乱"状态（无法控制）

```java
// CovenantZealotryManager.java
public String rollGreatSchismFriendlyFire(List<CardInstance> covenantUnits) {
    Map<String, List<CardInstance>> byRace = covenantUnits.stream()
        .collect(Collectors.groupingBy(this::raceKey));
    
    boolean hasElite = byRace.containsKey("ELITE");
    boolean hasBrute = byRace.containsKey("BRUTE");
    if (!(hasElite && hasBrute)) return null;
    
    if (random.nextDouble() >= 0.10) return null; // 10%概率
    
    int pick = random.nextInt(covenantUnits.size());
    return covenantUnits.get(pick).instanceId(); // 返回火拼单位
}
```

#### 游戏策略
- **避免混编**: 不要同时部署精英和野猪人
- **纯种部队**: 全精英或全野猪人降低风险
- **先知控制**: 先知在场时可压制大分裂（需特定卡牌）

---

## 管理器系统

### CovenantZealotryManager
**职责**: 管理等级制度、狂热加成、大分裂

**关键方法**:
- `rankOf(CardInstance unit)`: 判断单位等级
- `hasMajorOrLeaderInLane(laneAllies)`: 检查Lane内是否有精英/领袖
- `zealotryAttackBonus(unit, laneAllies)`: 计算狂热攻击加成
- `zealotryDamageTakenMultiplier(unit, laneAllies)`: 计算受伤倍率
- `shouldCollapse(unit, leaderDiedThisTurn)`: 检查士气崩溃
- `rollGreatSchismFriendlyFire(covenantUnits)`: 大分裂火拼判定

**集成点**:
- `DeploymentHandler`: 部署时记录单位等级
- `CombatHandler.calculateDamage()`: 应用狂热加成
- `CombatHandler.handleUnitDeath()`: 领袖阵亡触发士气崩溃
- `TurnFlowHandler.onTurnEnd()`: 大分裂判定

---

### CovenantFaithManager
**职责**: 管理信仰值的获取与消耗

**关键方法**:
- `onEnemyKilled(playerId)`: 击杀获得 +2 信仰
- `onUnitLostOrRetreated(playerId, penalty)`: 损失单位扣信仰
- `faith(playerId)`: 查询当前信仰值
- `activateRitualOverdrive(playerId, globalTurnIndex)`: 消耗10信仰防止过热
- `isOverheatSuppressedThisTurn(playerId, globalTurnIndex)`: 检查是否激活超载

**集成点**:
- `CombatHandler.handleUnitDeath()`: 击杀/阵亡时调整信仰
- `TurnFlowHandler.onTurnEnd()`: 仪式超载检查
- `WeaponOverheatSystem`: 超载判定前检查仪式状态

---

### CovenantDeployManager
**职责**: 管理群体部署和自杀攻击

**关键方法**:
- `massDeploy(popManager, lane, gruntTokenId, faith, cost)`: 群体部署咕噜人
- `sacrificeUnit(unit, adjacentTargets)`: 自杀攻击伤害计算

**集成点**:
- `TurnFlowHandler.onTurnStart()`: 群体部署检查
- `CombatHandler`: 自杀攻击触发

---

### CovenantOrbitalDominanceManager
**职责**: 管理轨道打击、登陆艇

**关键方法**:
- `applyGlassingMark(enemyPlayerId, lane)`: 在Lane放置玻璃化标记
- `onEnemyUnitSpawned(enemyPlayerId, lane)`: 单位部署时触发标记伤害
- `consumeGlassingMark(enemyPlayerId, lane)`: 消耗标记
- `boardingCraftHit(targetShipInstanceId)`: 放置登陆标记
- `isShipDisabled(targetShipInstanceId)`: 检查战舰是否被登陆

**集成点**:
- `DeploymentHandler`: 部署时检查玻璃化标记
- `CombatHandler`: 战舰战斗时触发登陆
- `TurnFlowHandler.onRoundEnd()`: 玻璃化标记处理

---

## 战术定位

### 游戏前期 (1-5 回合)
**策略**: 建立信仰经济，部署劣等兵
- 部署咕噜人/豺狼人快速占领Lane
- 避免过早损失单位（信仰惩罚）
- 积累信仰至10点准备群体部署

### 游戏中期 (6-10 回合)
**策略**: 群体部署 + 轨道压制
- 使用"群体部署"填满关键Lane
- 部署精英/野猪人提供加成
- 轨道玻璃化打击压制敌方扩展
- 注意避免精英+野猪人混编（大分裂风险）

### 游戏后期 (11+ 回合)
**策略**: 仲裁者/先知终结战斗
- 部署传奇单位（仲裁者、真理先知）
- 仪式超载 + 等离子武器集火
- 登陆艇瘫痪敌方舰队
- 自杀攻击破局

---

## 代码集成

### 1. DeploymentHandler 集成

```java
// 1. 狂热等级追踪
campaignManager.covenantZealotry().recordDeployment(playerId, deployed.definition().id());

// 2. 玻璃化标记伤害检查
int glassingDamage = campaignManager.covenantOrbital()
    .onEnemyUnitSpawned(playerId, lane);
if (glassingDamage > 0) {
    // 应用1点穿透伤害
    combatStateStore.get(deployed.instanceId()).directDamage(glassingDamage);
    campaignManager.covenantOrbital().consumeGlassingMark(playerId, lane);
    emit(EventType.GLASSING_MARK_TRIGGERED, ...);
}
```

### 2. CombatHandler 集成

```java
// 1. 狂热攻击加成
if (HandlerUtils.hasTag(attacker, "COVENANT")) {
    List<CardInstance> laneAllies = battlefield.unitsInLane(attackerPos.lane())
        .stream()
        .filter(u -> u.ownerPlayerId().equals(attacker.ownerPlayerId()))
        .toList();
    int zealotryBonus = campaignManager.covenantZealotry()
        .zealotryAttackBonus(attacker, laneAllies);
    baseDamage += zealotryBonus;
}

// 2. 信仰值调整（击杀/阵亡）
if (result.lethal()) {
    campaignManager.covenantFaith().onEnemyKilled(attackerPlayerId);
    emit(EventType.COVENANT_FAITH_GAINED, ...);
}

if (HandlerUtils.hasTag(dead, "COVENANT")) {
    campaignManager.covenantFaith().onUnitLostOrRetreated(dead.ownerPlayerId(), 1);
}

// 3. 领袖阵亡 → 士气崩溃
if (HandlerUtils.hasTag(dead, "LEADER") && HandlerUtils.hasTag(dead, "COVENANT")) {
    List<CardInstance> minors = battlefield.unitsInLane(lane)
        .stream()
        .filter(u -> campaignManager.covenantZealotry().shouldCollapse(u, true))
        .toList();
    for (CardInstance minor : minors) {
        battlefield.removeUnit(minor.instanceId());
        emit(EventType.COVENANT_MORALE_COLLAPSE, ...);
    }
}

// 4. 登陆艇攻击
if (HandlerUtils.hasTag(attacker, "COVENANT") && HandlerUtils.hasTag(defender, "SHIP")) {
    campaignManager.covenantOrbital().boardingCraftHit(defender.instanceId());
    emit(EventType.BOARDING_CRAFT_HIT, ...);
}
```

### 3. TurnFlowHandler 集成

```java
// 1. 群体部署检查
campaignManager.covenantDeploy().checkMassDeploy(activePlayerId, battlefield, globalTurnIndex);

// 2. 仪式超载检查
campaignManager.covenantFaith().checkRitualOverdrive(endingPlayerId, globalTurnIndex);

// 3. 玻璃化标记处理
campaignManager.covenantOrbital().processGlassingMarks(endingPlayerId, globalTurnIndex);

// 4. 大分裂判定
if (random.nextDouble() < 0.10) {
    List<CardInstance> covenantUnits = battlefield.allUnits()
        .stream()
        .filter(u -> u.ownerPlayerId().equals(activePlayerId))
        .filter(u -> HandlerUtils.hasTag(u, "COVENANT"))
        .toList();
    String targetId = campaignManager.covenantZealotry()
        .rollGreatSchismFriendlyFire(covenantUnits);
    if (targetId != null) {
        emit(EventType.GREAT_SCHISM_TRIGGERED, ...);
    }
}
```

---

## 示例卡牌

### 单位卡牌

#### COV-GRUNT-001: 咕噜人步兵
```json
{
  "id": "COV-GRUNT-001",
  "name": "Unggoy Combat Infantry",
  "cardType": "UNIT",
  "faction": "COVENANT",
  "cost": {"supply": 1, "battery": 0},
  "stats": {"attack": 1, "healthCap": 2, "shieldCap": 0},
  "keywords": [{"name": "SUICIDE_GRUNT"}],
  "tags": ["GRUNT", "MINOR", "COVENANT"],
  "abilityText": "有精英/领袖同Lane时攻击力+1，但受伤害+20%。领袖阵亡时逃离战场。"
}
```

#### COV-ELITE-001: 精英战士
```json
{
  "id": "COV-ELITE-001",
  "name": "Sangheili Major",
  "cardType": "UNIT",
  "faction": "COVENANT",
  "cost": {"supply": 4, "battery": 0},
  "stats": {"attack": 3, "healthCap": 5, "shieldCap": 3},
  "keywords": [{"name": "ENERGY_SWORD"}],
  "tags": ["ELITE", "MAJOR", "COVENANT"],
  "abilityText": "Lane内所有劣等兵攻击力+1。"
}
```

#### COV-ARBITER-001: 仲裁者
```json
{
  "id": "COV-ARBITER-001",
  "name": "The Arbiter",
  "cardType": "UNIT",
  "faction": "COVENANT",
  "rarity": "LEGENDARY",
  "cost": {"supply": 8, "battery": 0},
  "stats": {"attack": 6, "healthCap": 7, "shieldCap": 5},
  "keywords": [
    {"name": "LEADER"},
    {"name": "ACTIVE_CAMO"},
    {"name": "ENERGY_SWORD"}
  ],
  "tags": ["ELITE", "LEADER", "HERO", "COVENANT"],
  "abilityText": "Leader单位。Lane内所有劣等兵攻击力+1。可使用主动隐身。首次攻击必定暴击。"
}
```

#### COV-PROPHET-001: 真理先知
```json
{
  "id": "COV-PROPHET-001",
  "name": "Prophet of Truth",
  "cardType": "UNIT",
  "faction": "COVENANT",
  "rarity": "LEGENDARY",
  "cost": {"supply": 10, "battery": 0},
  "stats": {"attack": 0, "healthCap": 6, "shieldCap": 8},
  "keywords": [{"name": "PROPHET"}, {"name": "LEADER"}],
  "tags": ["PROPHET", "HIERARCH", "LEADER", "COVENANT"],
  "abilityText": "Leader单位。每回合获得+2信仰。阵亡时己方所有劣等兵逃离战场。可使用先知预言（8信仰）。"
}
```

### 战术卡牌

#### COV-TACTICAL-001: 群体部署
```json
{
  "id": "COV-TACTICAL-001",
  "name": "For the Great Journey!",
  "cardType": "TACTICAL",
  "faction": "COVENANT",
  "rarity": "RARE",
  "cost": {"supply": 0, "battery": 0, "faith": 15},
  "effect": {
    "type": "MASS_DEPLOY",
    "target": "LANE",
    "unitType": "GRUNT_TOKEN"
  },
  "abilityText": "消耗15信仰，填满选定Lane的所有空位（咕噜人Token）"
}
```

#### COV-TACTICAL-002: 仪式超载
```json
{
  "id": "COV-TACTICAL-002",
  "name": "Ritual Overdrive",
  "cardType": "TACTICAL",
  "faction": "COVENANT",
  "cost": {"supply": 0, "battery": 0, "faith": 10},
  "effect": {
    "type": "PREVENT_OVERHEAT",
    "duration": "THIS_TURN"
  },
  "abilityText": "消耗10信仰，本回合所有等离子武器不会过热"
}
```

#### COV-TACTICAL-003: 玻璃化打击
```json
{
  "id": "COV-TACTICAL-003",
  "name": "Glassing Strike",
  "cardType": "TACTICAL",
  "faction": "COVENANT",
  "rarity": "RARE",
  "cost": {"supply": 0, "battery": 2},
  "effect": {
    "type": "GLASSING_MARK",
    "target": "ENEMY_LANE",
    "stacks": 3
  },
  "abilityText": "在目标Lane放置3个玻璃化标记。敌方每部署1个单位，立即受到1点穿透伤害并消耗1个标记。"
}
```

### 地标卡牌

#### COV-FIELD-001: 圣地遗迹
```json
{
  "id": "COV-FIELD-001",
  "name": "Forerunner Relic Site",
  "cardType": "FIELD",
  "faction": "COVENANT",
  "cost": {"supply": 4, "battery": 0},
  "stats": {"healthCap": 8},
  "effect": {
    "type": "GENERATE_FAITH",
    "frequency": "TURN_START",
    "value": 3
  },
  "abilityText": "每回合开始时获得3点信仰。被摧毁时己方所有单位士气-1。"
}
```

---

## 平衡性分析

### 优势
1. **爆发力强**: 群体部署快速填满战场
2. **等级加成**: 劣等兵在精英领导下攻击力高
3. **轨道压制**: 玻璃化打击限制敌方扩展
4. **信仰循环**: 击杀获得信仰，信仰驱动强力能力

### 劣势
1. **依赖等级制**: 领袖阵亡导致全线崩盘
2. **劣等兵脆弱**: 受伤害+20%，易被AOE清场
3. **大分裂风险**: 精英+野猪人混编可能内讧
4. **信仰惩罚**: 损失单位扣信仰，劣势雪崩

### 克制关系
- **优势对抗**:
  - **UNSC**: 等离子武器克制UNSC装甲
  - **先行者**: 数量优势对抗高科技单位
- **劣势对抗**:
  - **虫族 (Flood)**: 大规模感染克制劣等兵海
  - **先行者 (Forerunner)**: 硬光武器穿透护盾

---

## 设计理念总结

星盟阵营体现了**"宗教狂热与种族等级制"**：
- **狂热信仰**: 越战越勇，信仰驱动奇迹
- **等级森严**: 精英领导劣等兵，领袖阵亡全线崩溃
- **轨道优势**: 玻璃化打击威慑战场
- **不稳定性**: 大分裂风险，内部矛盾

星盟是**极端的爆发阵营**，爆发时无可匹敌，但一旦失去节奏，信仰惩罚导致劣势雪崩。

---

**实现状态**: ✅ 完整实现  
**代码状态**: ✅ 编译通过  
**测试状态**: ⏳ 待测试  
**文档版本**: V1.0  
**更新日期**: 2026-02-26
