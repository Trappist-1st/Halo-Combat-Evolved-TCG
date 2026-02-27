# Halo CE TCG - UNSC 阵营机制详解 V1.0

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
**UNSC (United Nations Space Command - 联合国太空司令部)** 代表了人类在光环宇宙中的军事力量。作为技术相对落后但战术灵活的阵营，UNSC的设计核心是：

- **战术适应性**: 通过战术预案系统应对不同战局
- **资源再利用**: 载具核心回收与改装
- **精英单位**: 斯巴达战士作为游戏改变者
- **空降打击**: ODST与补给空投的快速部署能力

### 阵营特色
- **资源系统**: 供应（Supply）+ 电力（Battery）双资源
- **战术预案**: 4种可切换的战场策略
- **空降机制**: 指挥点（Command Points）驱动的快速部署
- **载具经济**: 死亡回收核心降低重新部署成本

---

## 核心机制

### 1. 战术预案系统 (Tactical Protocol System)

#### 机制说明
UNSC玩家可以在回合开始前选择一种战术预案，影响整个回合的单位行为与资源分配。

#### 四种预案

##### 1.1 平衡态势 (BALANCED)
- **特点**: 攻守兼备，资源均衡分配
- **效果**:
  - 空降优先级: 中等
  - 修复优先级: 中等
  - MAC炮伤害加成: 0%
  - 科尔协议启用: 否
- **适用场景**: 对战局不确定时的默认选择

##### 1.2 防御强化 (FORTIFY)
- **特点**: 龟缩防御，优先修复与防护
- **效果**:
  - 空降优先级: 低
  - 修复优先级: **高**
  - 护盾充能速度: **+20%**
  - 科尔协议启用: **是** (战舰受损可自爆清空Lane)
- **适用场景**: 面对虫族/星盟大规模进攻时
- **代价**: 牺牲攻击主动性

##### 1.3 矛头突击 (SPEARHEAD)
- **特点**: 集中火力进攻，忽略防御
- **效果**:
  - 空降优先级: **高**
  - 修复优先级: 低
  - MAC炮伤害加成: **+20%**
  - 科尔协议启用: 否
- **适用场景**: 掌握主动权，快速推进取胜
- **风险**: 单位易受到集火击杀

##### 1.4 焦土战术 (SCORCHED_EARTH)
- **特点**: 鱼死网破，同归于尽
- **效果**:
  - 空降优先级: 低
  - 修复优先级: 中等
  - MAC炮伤害加成: **+10%**
  - 科尔协议启用: **是** (主动引爆战舰造成范围伤害)
- **适用场景**: 劣势局，清空敌方单位重新开局
- **特殊**: 可配合"科尔协议-092"卡牌引爆战舰

#### 实现代码

```java
// UNSCTacticalProtocolExecutor.java
public enum TacticalPreset {
    BALANCED,         // 平衡
    FORTIFY,          // 防御
    SPEARHEAD,        // 进攻
    SCORCHED_EARTH    // 焦土
}

public TacticalDecision decideForLane(String playerId, Lane lane) {
    TacticalPreset preset = presetOf(playerId);
    return switch (preset) {
        case BALANCED -> new TacticalDecision(lane, true, true, false, 0.0);
        case FORTIFY -> new TacticalDecision(lane, false, true, true, 0.0);
        case SPEARHEAD -> new TacticalDecision(lane, true, false, false, 0.20);
        case SCORCHED_EARTH -> new TacticalDecision(lane, false, true, true, 0.10);
    };
}
```

---

### 2. 空降打击系统 (Drop Pod System)

#### 指挥点 (Command Points)
- **获取方式**:
  - 部署"前线指挥所" (Forward Operating Base): 每回合生成 1 CP
  - 击杀敌方单位: 获得 1 CP
  - 战术卡牌: "轨道协调" +3 CP
- **使用方式**:
  - 快速空投ODST: 消耗 2 CP
  - 补给空投箱: 消耗 1 CP

#### ODST 震荡波 (Shockwave)
- **触发**: ODST空降舱撞击地面
- **效果**: 震晕相邻敌方单位 1 回合
- **代码实现**:
```java
// UNSCDropPodManager.java
public int dropShockwaveStun(List<CardInstance> adjacentEnemies) {
    return adjacentEnemies.size(); // 返回被震晕单位数量
}
```

#### 补给空投箱 (Supply Crate)
- **部署**: 消耗 1 CP，可部署到任意Lane
- **效果**:
  - **压制**: 敌方单位在该Lane攻击力 -1（持续到补给箱被摧毁）
  - **补给**: 友方单位在该Lane每回合恢复 1 HP
- **弱点**: 仅 2 HP，易被摧毁
- **代码实现**:
```java
// UNSCDropPodManager.java
public List<String> suppressBySupplyCrate(Lane lane, List<CardInstance> enemyUnitsInLane) {
    if (!hasSupplyCrateInLane(lane)) return List.of();
    // 返回被压制的敌方单位列表
    return enemyUnitsInLane.stream()
        .map(CardInstance::instanceId)
        .toList();
}
```

---

### 3. 载具回收系统 (Salvage & Refit System)

#### 核心机制
UNSC载具被摧毁后，会留下"载具核心"(Vehicle Core)，可用于降低相同载具的重新部署成本。

#### 回收流程
1. **载具阵亡**: 自动触发核心回收
2. **核心存储**: 按载具卡牌ID分类存储
3. **改装消耗**: 重新部署相同载具时自动消耗1个核心，降低费用

#### 费用减免
- **疣猪号** (Warthog): 原价 4 供应 → 改装价 2 供应
- **蝎式坦克** (Scorpion): 原价 6 供应 → 改装价 3 供应
- **鹈鹕运输机** (Pelican): 原价 5 供应 → 改装价 3 供应

#### 代码实现
```java
// UNSCSalvageManager.java
public void recoverVehicleCore(String vehicleCardId) {
    recoveredCoreByVehicleType.merge(vehicleCardId, 1, Integer::sum);
}

public int consumeRefitReduction(String vehicleCardId) {
    int current = recoveredCoreByVehicleType.getOrDefault(vehicleCardId, 0);
    if (current <= 0) return 0;
    recoveredCoreByVehicleType.put(vehicleCardId, current - 1);
    return 1; // 返回消耗的核心数量
}
```

#### 集成到 CombatHandler
```java
// CombatHandler.handleUnitDeath()
if (HandlerUtils.hasTag(dead, "VEHICLE") && HandlerUtils.hasTag(dead, "UNSC")) {
    campaignManager.unscSalvage().recoverVehicleCore(dead.definition().id());
    emit(EventType.UNSC_VEHICLE_CORE_RECOVERED, ...);
}
```

---

### 4. 斯巴达战士系统 (Spartan Hero System)

#### 斯巴达特性
- **每玩家限 1 名**: 斯巴达战士在牌组中具有"唯一性"
- **自由移动**: 每回合可在Lane间免费移动一次
- **经验成长**: 击杀敌方单位获得经验值，提升属性
- **MIA恢复**: 生命归零时进入"失踪"状态，4回合后自动恢复到手牌

#### MIA 机制 (Missing In Action)
```java
// SpartanHeroManager.java
public boolean triggerMia(String heroInstanceId, int currentTurn) {
    // 记录恢复回合 = 当前回合 + 4
    miaRecoverTurnByHero.put(heroInstanceId, currentTurn + 4);
    return true;
}

public boolean isRecoverableThisTurn(String heroInstanceId, int currentTurn) {
    return miaRecoverTurnByHero.getOrDefault(heroInstanceId, Integer.MAX_VALUE) <= currentTurn;
}

public boolean recoverFromMia(String heroInstanceId) {
    // 恢复时，经验值减半
    int xp = heroExperience.getOrDefault(heroInstanceId, 0);
    heroExperience.put(heroInstanceId, xp / 2);
    return true;
}
```

#### 载具劫持 (Vehicle Hijack)
- **生效条件**: 斯巴达战士相邻敌方载具
- **成功率**: 基础 40%，AI插件增加至 70%
- **效果**: 敌方载具转换为己方控制

```java
// SpartanHeroManager.java
public boolean tryVehicleHijack(double successChance) {
    return Math.random() < Math.min(1.0, successChance);
}
```

#### 终局状态 (Last Stand)
- **触发**: 斯巴达战士 HP ≤ 20%
- **效果**: 攻击力 +2，护盾完全恢复一次
- **持续**: 直到下次受伤或回合结束

---

## 管理器系统

### UNSCDropPodManager
**职责**: 管理指挥点、空降、补给箱

**关键方法**:
- `grantCommandPoint(playerId, amount)`: 增加指挥点
- `tryInstantOdstDrop(playerId, cpCost)`: 消耗CP空投ODST
- `deploySupplyCrate(playerId, lane)`: 部署补给箱
- `suppressBySupplyCrate(lane, enemies)`: 计算压制效果

**集成点**:
- `DeploymentHandler.deployUnitFromHand()`: 记录空降单位
- `TurnFlowHandler.onTurnStart()`: 处理机库队列

---

### UNSCSalvageManager
**职责**: 管理载具核心回收与改装

**关键方法**:
- `recoverVehicleCore(vehicleCardId)`: 回收核心
- `consumeRefitReduction(vehicleCardId)`: 消耗核心降低费用
- `pendingCore(vehicleCardId)`: 查询可用核心数量

**集成点**:
- `CombatHandler.handleUnitDeath()`: 载具阵亡时回收
- `DeploymentHandler.deployUnitFromHand()`: 部署时消耗核心

---

### UNSCTacticalProtocolExecutor
**职责**: 执行战术预案，调整战场参数

**关键方法**:
- `setPreset(playerId, preset)`: 设置战术预案
- `presetOf(playerId)`: 查询当前预案
- `decideForLane(playerId, lane)`: 为Lane生成战术决策

**集成点**:
- `GameStateManager.switchTacticalPreset()`: 玩家切换预案
- `CombatHandler.calculateDamage()`: MAC炮伤害加成
- `TurnFlowHandler.rechargeShields()`: 护盾充能倍率

---

### SpartanHeroManager
**职责**: 管理斯巴达战士的唯一性、MIA、劫持机制

**关键方法**:
- `registerSpartanOwner(playerId)`: 注册斯巴达拥有者（保证唯一性）
- `triggerMia(heroInstanceId, currentTurn)`: 触发MIA状态
- `isRecoverableThisTurn(heroInstanceId, currentTurn)`: 检查是否可恢复
- `tryVehicleHijack(successChance)`: 尝试劫持载具
- `canUseFreeMove(heroInstanceId)`: 检查是否可免费移动

**集成点**:
- `DeploymentHandler.deployUnitFromHand()`: 部署时检查唯一性
- `CombatHandler.handleUnitDeath()`: 触发MIA
- `TurnFlowHandler.onTurnStart()`: 检查MIA恢复

---

## 战术定位

### 游戏前期 (1-5 回合)
**策略**: 建立经济基础，部署前线指挥所
- 部署"前线指挥所" (FOB) 积累指挥点
- 使用海军陆战队 (Marines) 防御
- 避免过度投资高费单位

### 游戏中期 (6-10 回合)
**策略**: 空降打击 + 载具压制
- 使用ODST快速支援薄弱Lane
- 部署疣猪号/蝎式坦克建立防线
- 利用补给箱压制敌方进攻
- 切换战术预案应对战局

### 游戏后期 (11+ 回合)
**策略**: 斯巴达战士终结战斗
- 部署斯巴达战士（士官长/Noble Team）
- 利用载具核心快速重建损失
- 防御强化预案保护关键单位
- 焦土战术破局

---

## 代码集成

### 1. DeploymentHandler 集成

```java
// 1. 指挥点跟踪
if (hasKeyword(deployed, "DROP_POD")) {
    campaignManager.unscDropPod().recordDropPodDeployment(playerId, deployed.instanceId(), lane);
}

// 2. 斯巴达唯一性检查
if (hasKeyword(deployed, "SPARTAN")) {
    if (!campaignManager.spartanHero().registerSpartanOwner(playerId)) {
        throw new IllegalStateException("Player already has a Spartan in play");
    }
}
```

### 2. CombatHandler 集成

```java
// 1. 载具核心回收
if (HandlerUtils.hasTag(dead, "VEHICLE") && HandlerUtils.hasTag(dead, "UNSC")) {
    campaignManager.unscSalvage().recoverVehicleCore(dead.definition().id());
    emit(EventType.UNSC_VEHICLE_CORE_RECOVERED, ...);
}

// 2. 斯巴达战士MIA
if (HandlerUtils.hasTag(dead, "SPARTAN")) {
    campaignManager.spartanHero().triggerMia(dead.instanceId(), globalTurnIndex);
    campaignManager.spartanHero().releaseSpartanOwner(dead.ownerPlayerId());
    emit(EventType.SPARTAN_MIA_TRIGGERED, ...);
    return; // 不移除卡牌，4回合后恢复
}
```

### 3. TurnFlowHandler 集成

```java
// 1. 斯巴达MIA恢复检查
List<String> recoveredSpartans = campaignManager.spartanHero()
    .checkMIARecovery(activePlayerId, globalTurnIndex);
for (String spartanId : recoveredSpartans) {
    emit(EventType.SPARTAN_MIA_RECOVERED, ...);
}

// 2. 载具改装冷却
campaignManager.unscSalvage().processRefitCooldowns(endingPlayerId, globalTurnIndex);

// 3. 战术预案护盾加成
double rechargeMultiplier = campaignManager.unscTactical()
    .getMoraleRechargeMultiplier(activePlayerId);
int rechargeAmount = (int) Math.ceil(stats.shieldCap() * rechargeMultiplier);
```

---

## 示例卡牌

### 单位卡牌

#### UNSC-MARINE-001: 海军陆战队
```json
{
  "id": "UNSC-MARINE-001",
  "name": "UNSC Marine",
  "cardType": "UNIT",
  "faction": "UNSC",
  "cost": {"supply": 2, "battery": 0},
  "stats": {"attack": 2, "healthCap": 3, "shieldCap": 0},
  "keywords": [{"name": "RANGED"}],
  "tags": ["INFANTRY", "UNSC"]
}
```

#### UNSC-ODST-001: 轨道空降突击队
```json
{
  "id": "UNSC-ODST-001",
  "name": "ODST Squad",
  "cardType": "UNIT",
  "faction": "UNSC",
  "cost": {"supply": 3, "battery": 0},
  "stats": {"attack": 3, "healthCap": 4, "shieldCap": 0},
  "keywords": [
    {"name": "DROP_POD"},
    {"name": "SHOCKWAVE", "value": "1"}
  ],
  "tags": ["ELITE_INFANTRY", "UNSC"],
  "abilityText": "空降时震晕相邻敌方单位1回合"
}
```

#### UNSC-SPARTAN-117: 士官长
```json
{
  "id": "UNSC-SPARTAN-117",
  "name": "Master Chief Petty Officer John-117",
  "cardType": "UNIT",
  "faction": "UNSC",
  "rarity": "LEGENDARY",
  "cost": {"supply": 8, "battery": 0},
  "stats": {"attack": 5, "healthCap": 8, "shieldCap": 5},
  "keywords": [
    {"name": "SPARTAN"},
    {"name": "HERO"},
    {"name": "FREE_MOVE"},
    {"name": "MIA_RECOVERY", "value": "4"},
    {"name": "VEHICLE_HIJACK"}
  ],
  "tags": ["SPARTAN", "HERO", "UNSC"],
  "abilityText": "每回合可免费移动一次。生命归零时进入MIA，4回合后恢复到手牌。可劫持相邻敌方载具。"
}
```

### 载具卡牌

#### UNSC-WARTHOG-001: M12疣猪号
```json
{
  "id": "UNSC-WARTHOG-001",
  "name": "M12 Warthog LRV",
  "cardType": "UNIT",
  "faction": "UNSC",
  "cost": {"supply": 4, "battery": 0},
  "stats": {"attack": 3, "healthCap": 5, "shieldCap": 0},
  "keywords": [{"name": "VEHICLE"}, {"name": "FAST"}],
  "tags": ["VEHICLE", "LIGHT", "UNSC"],
  "abilityText": "被摧毁时回收载具核心，降低重新部署成本至2供应"
}
```

#### UNSC-SCORPION-001: M808蝎式坦克
```json
{
  "id": "UNSC-SCORPION-001",
  "name": "M808 Scorpion MBT",
  "cardType": "UNIT",
  "faction": "UNSC",
  "cost": {"supply": 6, "battery": 0},
  "stats": {"attack": 6, "healthCap": 10, "shieldCap": 0},
  "keywords": [{"name": "VEHICLE"}, {"name": "HEAVY"}, {"name": "SIEGE"}],
  "tags": ["VEHICLE", "HEAVY", "UNSC"],
  "abilityText": "被摧毁时回收载具核心。重新部署成本降至3供应。"
}
```

### 战术卡牌

#### UNSC-TACTICAL-001: 战术预案切换
```json
{
  "id": "UNSC-TACTICAL-001",
  "name": "Tactical Protocol Switch",
  "cardType": "TACTICAL",
  "faction": "UNSC",
  "cost": {"supply": 0, "battery": 1},
  "effect": {
    "type": "CHANGE_PRESET",
    "target": "SELF",
    "value": "CHOOSE_ONE"
  },
  "abilityText": "选择一种战术预案：平衡/防御/进攻/焦土"
}
```

#### UNSC-TACTICAL-002: 科尔协议-092
```json
{
  "id": "UNSC-TACTICAL-002",
  "name": "Cole Protocol Emergency Directive 092",
  "cardType": "TACTICAL",
  "faction": "UNSC",
  "rarity": "RARE",
  "cost": {"supply": 0, "battery": 2},
  "effect": {
    "type": "SELF_DESTRUCT",
    "target": "OWN_SHIP",
    "damage": 10,
    "range": "LANE_AOE"
  },
  "abilityText": "牺牲己方一艘战舰，对该Lane所有敌方单位造成10点伤害"
}
```

### 地标卡牌

#### UNSC-FIELD-001: 前线指挥所
```json
{
  "id": "UNSC-FIELD-001",
  "name": "Forward Operating Base",
  "cardType": "FIELD",
  "faction": "UNSC",
  "cost": {"supply": 3, "battery": 0},
  "stats": {"healthCap": 6},
  "effect": {
    "type": "GENERATE_CP",
    "frequency": "TURN_START",
    "value": 1
  },
  "abilityText": "每回合开始时生成1点指挥点"
}
```

---

## 平衡性分析

### 优势
1. **战术灵活**: 4种预案覆盖所有战局
2. **经济循环**: 载具回收降低重复投资成本
3. **快速支援**: 指挥点驱动的即时空降
4. **英雄单位**: 斯巴达战士改变战局

### 劣势
1. **初期弱势**: 建立经济体系需要时间
2. **资源依赖**: 双资源系统限制部署速度
3. **载具脆弱**: 缺少护盾，易被集火
4. **斯巴达限制**: 每玩家仅1名，死亡损失巨大

### 克制关系
- **优势对抗**: 
  - **虫族** (Flood): 焦土战术清场，科尔协议破局
  - **先行者** (Forerunner): 斯巴达劫持先行者载具
- **劣势对抗**:
  - **星盟** (Covenant): 等离子武器克制UNSC装甲
  - **先行者** (Forerunner): 硬光武器穿透载具防御

---

## 设计理念总结

UNSC阵营体现了**"人类的韧性与适应性"**：
- 没有超凡的科技（先行者）
- 没有狂热的信仰（星盟）
- 没有恐怖的增殖（虫族）

但通过**战术灵活性、资源循环、精英单位**，UNSC在绝境中寻找生机，用智慧与勇气对抗更强大的敌人。

---

**实现状态**: ✅ 完整实现  
**代码状态**: ✅ 编译通过  
**测试状态**: ⏳ 待测试  
**文档版本**: V1.0  
**更新日期**: 2026-02-26
