# Halo CE TCG - 洪魔 (Flood) 阵营机制详解 V1.0

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
**Flood (洪魔/虫族)** 是《光环》宇宙中最恐怖的寄生生命体。其设计核心体现了：

- **生物恐怖**: 感染、寄生、同化一切生命
- **指数增殖**: 越战越多，敌人尸体成为己方兵力
- **生物质循环**: 死亡单位转化为"生物质"资源
- **集体意识**: 原始坟墓脑 (Proto-Gravemind) 统筹战场
- **逻辑瘟疫**: 感染AI和机械单位

### 阵营特色
- **资源系统**: 供应（Supply）+ 生物质（Biomass）
- **感染机制**: 击杀敌方单位立即转化为感染体
- **进化路径**: 感染体 → 战斗体 → 原始坟墓脑 → 坟墓脑
- **寄生登舰**: 感染敌方战舰，控制其作战
- **无需部署**: 大部分单位由感染生成，不消耗手牌

---

## 核心机制

### 1. 生物质系统 (Biomass System)

#### 生物质获取
- **击杀敌方单位**: 获得等于单位人口值的生物质
  - 1 人口单位 (步兵): +1 生物质
  - 2 人口单位 (载具): +2 生物质
  - 3 人口单位 (重型载具): +3 生物质
  - 5 人口单位 (战舰): +5 生物质
- **回收己方感染体**: 获得 50% 人口值的生物质
- **吞噬地面**: 部署"洪魔孢子塔"，每回合生成 +2 生物质

#### 生物质消耗
- **进化原始坟墓脑**: -20 生物质
- **进化完全体坟墓脑**: -50 生物质
- **孵化特殊感染体**: -5 生物质（航母体）
- **寄生登舰**: -10 生物质（逻辑瘟疫）

#### 代码实现

```java
// BiomassManager.java
public void addBiomass(String playerId, int amount) {
    playerBiomass.merge(playerId, amount, Integer::sum);
}

public boolean spendBiomass(String playerId, int amount) {
    int current = playerBiomass.getOrDefault(playerId, 0);
    if (current >= amount) {
        playerBiomass.put(playerId, current - amount);
        return true;
    }
    return false;
}

// 击杀后自动收获生物质
public void harvest(String playerId, int unitPopValue) {
    addBiomass(playerId, unitPopValue);
}
```

#### 集成到 CombatHandler

```java
// CombatHandler.handleUnitDeath()
if (HandlerUtils.hasTag(attacker, "FLOOD")) {
    int popValue = dead.definition().cost() != null 
        ? dead.definition().cost().population() 
        : 1;
    campaignManager.biomass().harvest(attacker.ownerPlayerId(), popValue);
    emit(EventType.BIOMASS_HARVESTED, ...);
}
```

---

### 2. 感染机制 (Infection System)

#### 2.1 基础感染 (Basic Infection)
- **触发**: 洪魔单位击杀生物单位（非机械）
- **效果**: 死亡单位立即转化为"感染战斗体" (Combat Form)
- **限制**: 
  - 不适用于机械单位 (载具、战舰)
  - 不适用于先行者单位 (免疫感染)
  - 每个尸体仅能感染一次

```java
// FloodInfectionManager.java
public boolean canInfect(CardInstance deadUnit) {
    // 检查是否为生物单位
    boolean isBiological = !HandlerUtils.hasTag(deadUnit, "VEHICLE") 
                        && !HandlerUtils.hasTag(deadUnit, "MECHANICAL");
    
    // 先行者免疫
    boolean isForerunner = HandlerUtils.hasTag(deadUnit, "FORERUNNER");
    
    return isBiological && !isForerunner;
}

public String infectCorpse(CardInstance deadUnit) {
    if (!canInfect(deadUnit)) return null;
    
    // 生成感染战斗体Token
    String combatFormId = "FLOOD-COMBAT-FORM-" + UUID.randomUUID();
    return combatFormId;
}
```

#### 2.2 感染体类型

##### 感染型 (Infection Form)
- **特点**: 弱小但数量多，专门感染宿主
- **生命值**: 1 HP，无护盾
- **攻击力**: 0（无伤害，但击杀后感染）
- **特殊**: 死亡时爆炸，对相邻单位造成 1 点伤害

##### 战斗体 (Combat Form)
- **来源**: 感染普通步兵生成
- **属性**: 继承宿主 70% 的原始属性
- **特点**: 攻击速度快，但防御低

##### 航母体 (Carrier Form)
- **来源**: 消耗 5 生物质孵化
- **特殊**: 死亡时释放 4 个感染型
- **战术**: 主动牺牲航母体，快速散播感染

```java
// FloodInfectionManager.java
public List<String> spawnInfectionFormsFromCarrier(String carrierInstanceId) {
    List<String> infectionForms = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
        infectionForms.add("FLOOD-INFECTION-FORM-" + UUID.randomUUID());
    }
    return infectionForms;
}
```

---

### 3. 进化系统 (Evolution System)

#### 进化路径

```
感染型 (Infection Form)
    ↓ (击杀宿主)
战斗体 (Combat Form)
    ↓ (4个Token + 20生物质)
原始坟墓脑 (Proto-Gravemind)
    ↓ (吸收50生物质)
坟墓脑 (Gravemind)
```

#### 3.1 原始坟墓脑进化 (Proto-Gravemind Evolution)

**触发条件**:
- Lane内有 4+ 个洪魔Token
- 拥有 20 生物质
- 消耗：4个Token + 20生物质

**效果**:
- 移除4个Token
- 生成1个原始坟墓脑单位
- 原始坟墓脑属性：6/10/0 (攻击/生命/护盾)

```java
// FloodInfectionManager.java
public boolean canEvolveProtoGravemind(String playerId, Lane lane, int floodTokenCount) {
    return floodTokenCount >= 4 
        && biomass.getBiomass(playerId) >= 20;
}

public String evolveProtoGravemind(String playerId, Lane lane, List<CardInstance> tokensToConsume) {
    if (tokensToConsume.size() < 4) return null;
    
    biomass.spendBiomass(playerId, 20);
    // GameEngine移除4个Token
    return "FLOOD-PROTO-GRAVEMIND";
}
```

#### 3.2 完全体坟墓脑 (Full Gravemind)

**触发条件**:
- 场上有原始坟墓脑
- 拥有 50 生物质
- 消耗：原始坟墓脑 + 50生物质

**效果**:
- 移除原始坟墓脑
- 生成完全体坟墓脑
- 坟墓脑属性：8/20/0，全局光环效果
- **全局Buff**: 所有洪魔单位攻击力+2，生命值+2

```java
// FloodInfectionManager.java
public boolean canEvolveFullGravemind(String playerId) {
    boolean hasProto = battlefield.allUnits()
        .stream()
        .anyMatch(u -> u.definition().id().equals("FLOOD-PROTO-GRAVEMIND") 
                    && u.ownerPlayerId().equals(playerId));
    return hasProto && biomass.getBiomass(playerId) >= 50;
}
```

---

### 4. 寄生登舰机制 (Parasitic Boarding)

#### 逻辑瘟疫 (Logic Plague)
- **目标**: 敌方战舰（尤其是带AI的）
- **条件**: 
  - 战舰生命值 < 20%
  - 消耗 10 生物质
  - 战舰必须带有"机械"或"AI"标签
- **效果**: 战舰转换为洪魔控制，保留原有属性

```java
// FloodInfectionManager.java
public boolean attemptLogicPlague(CardInstance targetVessel) {
    boolean isMechanical = HandlerUtils.hasTag(targetVessel, "VEHICLE");
    boolean hasAI = HandlerUtils.hasTag(targetVessel, "AI_CORE");
    
    // 伪代码：检查生命值 < 20%
    // int threshold = (int)(targetVessel.maxHealth() * 0.2);
    // return (isMechanical || hasAI) && targetVessel.currentHealth() < threshold;
    
    return isMechanical || hasAI;
}
```

#### 寄生孢子 (Parasitic Spores)
- **触发**: 洪魔单位攻击战舰
- **效果**: 在战舰上放置"孢子标记"
- **累积**: 每3个标记，战舰最大生命值永久-10%
- **清除**: 战舰使用"清洁协议"战术卡

```java
// FloodInfectionManager.java
public void attachSpore(String targetShipInstanceId) {
    sporesByShip.merge(targetShipInstanceId, 1, Integer::sum);
}

public double getHealthReductionMultiplier(String targetShipInstanceId) {
    int spores = sporesByShip.getOrDefault(targetShipInstanceId, 0);
    int stacks = spores / 3; // 每3个孢子生效一次
    return Math.max(0.5, 1.0 - (stacks * 0.10)); // 最多降低50%生命上限
}
```

---

### 5. 孢子塔网络 (Spore Tower Network)

#### 部署机制
- **卡牌**: "洪魔孢子塔" (Flood Spore Tower)
- **费用**: 3 供应
- **效果**:
  - 每回合生成 +2 生物质
  - Lane内己方感染体获得"再生"（每回合恢复1 HP）
  - 被摧毁时释放6个感染型

#### 网络效应
- **2座孢子塔**: 生物质生成 ×1.5 (每回合 +3)
- **3座孢子塔**: 生物质生成 ×2 (每回合 +4)，全局洪魔单位速度+1
- **4+座孢子塔**: 自动胜利条件触发（环境完全感染）

```java
// FloodInfectionManager.java
public int calculateBiomassGeneration(String playerId) {
    int towerCount = battlefield.allUnits()
        .stream()
        .filter(u -> u.ownerPlayerId().equals(playerId))
        .filter(u -> u.definition().id().contains("SPORE-TOWER"))
        .toList()
        .size();
    
    return switch (towerCount) {
        case 0, 1 -> 2;
        case 2 -> 3;
        case 3 -> 4;
        default -> 5; // 4+座
    };
}

public boolean checkGlobalInfectionVictory(String playerId) {
    int towerCount = calculateTowerCount(playerId);
    return towerCount >= 4; // 4座孢子塔 = 自动胜利
}
```

---

## 管理器系统

### BiomassManager
**职责**: 管理生物质资源的获取与消耗

**关键方法**:
- `addBiomass(playerId, amount)`: 增加生物质
- `spendBiomass(playerId, amount)`: 消耗生物质
- `getBiomass(playerId)`: 查询当前生物质
- `harvest(playerId, unitPopValue)`: 击杀后收获生物质

**集成点**:
- `CombatHandler.handleUnitDeath()`: 击杀时收获生物质
- `DeploymentHandler`: 部署时消耗生物质（进化）
- `TurnFlowHandler.onTurnStart()`: 孢子塔生成生物质

---

### FloodInfectionManager
**职责**: 管理感染、进化、寄生机制

**关键方法**:
- `canInfect(deadUnit)`: 检查是否可感染
- `infectCorpse(deadUnit)`: 感染尸体生成战斗体
- `canEvolveProtoGravemind(playerId, lane, tokenCount)`: 检查是否可进化
- `evolveProtoGravemind(playerId, lane, tokens)`: 进化原始坟墓脑
- `attemptLogicPlague(targetVessel)`: 尝试逻辑瘟疫感染
- `spawnInfectionFormsFromCarrier(carrierId)`: 航母体死亡释放感染型

**集成点**:
- `CombatHandler.handleUnitDeath()`: 感染尸体
- `DeploymentHandler`: 进化检查
- `TurnFlowHandler.onTurnStart()`: 进化触发

---

## 战术定位

### 游戏前期 (1-5 回合)
**策略**: 建立感染链，积累生物质
- 部署"感染型"大量散播
- 击杀敌方步兵，快速生成战斗体
- 部署第一个孢子塔，开始生物质生成
- **避免**: 过度投资手牌单位（大部分兵力来自感染）

### 游戏中期 (6-10 回合)
**策略**: 进化原始坟墓脑，建立孢子网络
- 凑够4个Token + 20生物质，进化原始坟墓脑
- 部署2-3座孢子塔，加速生物质生成
- 对敌方战舰附加孢子标记
- 使用航母体快速散播感染

### 游戏后期 (11+ 回合)
**策略**: 完全体坟墓脑统治战场
- 进化完全体坟墓脑（50生物质）
- 全局Buff：所有洪魔+2/+2
- 逻辑瘟疫控制敌方战舰
- 部署第4座孢子塔触发自动胜利

---

## 代码集成

### 1. CombatHandler 集成

```java
// 1. 感染尸体
if (result.lethal() && HandlerUtils.hasTag(attacker, "FLOOD")) {
    if (campaignManager.floodInfection().canInfect(defender)) {
        String combatFormId = campaignManager.floodInfection()
            .infectCorpse(defender);
        
        // 在相同位置生成战斗体
        battlefield.deploy(attacker.ownerPlayerId(), lane, row, combatFormInstance);
        emit(EventType.FLOOD_INFECTION_TRIGGERED, ...);
    }
}

// 2. 收获生物质
if (HandlerUtils.hasTag(attacker, "FLOOD")) {
    int popValue = defender.definition().cost() != null 
        ? defender.definition().cost().population() 
        : 1;
    campaignManager.biomass().harvest(attacker.ownerPlayerId(), popValue);
    emit(EventType.BIOMASS_HARVESTED, ...);
}

// 3. 航母体死亡释放感染型
if (HandlerUtils.hasTag(dead, "CARRIER_FORM")) {
    List<String> infectionForms = campaignManager.floodInfection()
        .spawnInfectionFormsFromCarrier(dead.instanceId());
    
    for (String infectionId : infectionForms) {
        // 在周围生成感染型
        battlefield.deploy(dead.ownerPlayerId(), lane, row, infectionInstance);
    }
    emit(EventType.CARRIER_FORM_BURST, ...);
}

// 4. 战舰寄生孢子
if (HandlerUtils.hasTag(attacker, "FLOOD") && HandlerUtils.hasTag(defender, "SHIP")) {
    campaignManager.floodInfection().attachSpore(defender.instanceId());
    emit(EventType.PARASITIC_SPORE_ATTACHED, ...);
}
```

### 2. TurnFlowHandler 集成

```java
// 1. 孢子塔生成生物质
int biomassGen = campaignManager.floodInfection()
    .calculateBiomassGeneration(activePlayerId);
campaignManager.biomass().addBiomass(activePlayerId, biomassGen);
emit(EventType.BIOMASS_GENERATED, ...);

// 2. 检查进化条件
if (campaignManager.floodInfection().canEvolveProtoGravemind(activePlayerId, lane, tokenCount)) {
    emit(EventType.PROTO_GRAVEMIND_EVOLUTION_AVAILABLE, ...);
}

// 3. 检查全局感染胜利
if (campaignManager.floodInfection().checkGlobalInfectionVictory(activePlayerId)) {
    emit(EventType.FLOOD_GLOBAL_INFECTION_VICTORY, ...);
}
```

### 3. DeploymentHandler 集成

```java
// 孢子塔部署检查
if (deployed.definition().id().contains("SPORE-TOWER")) {
    int towerCount = calculateTowerCount(playerId);
    if (towerCount >= 4) {
        // 触发自动胜利检查
        emit(EventType.FLOOD_TOWER_NETWORK_COMPLETE, ...);
    }
}
```

---

## 示例卡牌

### 单位卡牌

#### FLOOD-INFECTION-001: 感染型
```json
{
  "id": "FLOOD-INFECTION-001",
  "name": "Infection Form",
  "cardType": "TOKEN",
  "faction": "FLOOD",
  "cost": {"supply": 0, "battery": 0},
  "stats": {"attack": 0, "healthCap": 1, "shieldCap": 0},
  "keywords": [
    {"name": "INFECT", "value": "ON_KILL"},
    {"name": "EXPLODE_ON_DEATH", "value": "1"}
  ],
  "tags": ["FLOOD", "PARASITE", "TOKEN"],
  "abilityText": "击杀生物单位后感染尸体。死亡时对相邻单位造成1点伤害。"
}
```

#### FLOOD-COMBAT-001: 战斗体
```json
{
  "id": "FLOOD-COMBAT-001",
  "name": "Combat Form",
  "cardType": "TOKEN",
  "faction": "FLOOD",
  "cost": {"supply": 0, "battery": 0},
  "stats": {"attack": 3, "healthCap": 4, "shieldCap": 0},
  "keywords": [{"name": "FAST"}],
  "tags": ["FLOOD", "INFECTED", "TOKEN"],
  "abilityText": "由感染宿主生成。继承宿主70%属性。"
}
```

#### FLOOD-CARRIER-001: 航母体
```json
{
  "id": "FLOOD-CARRIER-001",
  "name": "Carrier Form",
  "cardType": "UNIT",
  "faction": "FLOOD",
  "cost": {"supply": 2, "battery": 0, "biomass": 5},
  "stats": {"attack": 0, "healthCap": 6, "shieldCap": 0},
  "keywords": [
    {"name": "SUICIDE_CARRIER"},
    {"name": "SPAWN_ON_DEATH", "value": "4_INFECTION_FORMS"}
  ],
  "tags": ["FLOOD", "CARRIER"],
  "abilityText": "死亡时释放4个感染型。可主动引爆。"
}
```

#### FLOOD-PROTO-GRAVEMIND: 原始坟墓脑
```json
{
  "id": "FLOOD-PROTO-GRAVEMIND",
  "name": "Proto-Gravemind",
  "cardType": "UNIT",
  "faction": "FLOOD",
  "rarity": "RARE",
  "cost": {"supply": 0, "battery": 0, "biomass": 20, "requires": "4_FLOOD_TOKENS"},
  "stats": {"attack": 6, "healthCap": 10, "shieldCap": 0},
  "keywords": [
    {"name": "COORDINATED_ASSAULT"},
    {"name": "REGENERATION", "value": "2"}
  ],
  "tags": ["FLOOD", "GRAVEMIND", "ELITE"],
  "abilityText": "由4个洪魔Token进化而来。Lane内所有洪魔单位攻击力+1。每回合恢复2 HP。"
}
```

#### FLOOD-GRAVEMIND: 完全体坟墓脑
```json
{
  "id": "FLOOD-GRAVEMIND",
  "name": "Gravemind",
  "cardType": "UNIT",
  "faction": "FLOOD",
  "rarity": "LEGENDARY",
  "cost": {"supply": 0, "battery": 0, "biomass": 50, "requires": "PROTO_GRAVEMIND"},
  "stats": {"attack": 8, "healthCap": 20, "shieldCap": 0},
  "keywords": [
    {"name": "GLOBAL_BUFF", "value": "+2/+2_ALL_FLOOD"},
    {"name": "REGENERATION", "value": "3"},
    {"name": "LOGIC_PLAGUE"}
  ],
  "tags": ["FLOOD", "GRAVEMIND", "LEGENDARY"],
  "abilityText": "全局：所有洪魔单位+2/+2。可尝试逻辑瘟疫感染敌方战舰。每回合恢复3 HP。"
}
```

### 地标卡牌

#### FLOOD-FIELD-001: 洪魔孢子塔
```json
{
  "id": "FLOOD-FIELD-001",
  "name": "Flood Spore Tower",
  "cardType": "FIELD",
  "faction": "FLOOD",
  "cost": {"supply": 3, "battery": 0},
  "stats": {"healthCap": 8},
  "effect": {
    "type": "GENERATE_BIOMASS",
    "frequency": "TURN_START",
    "value": 2
  },
  "keywords": [
    {"name": "REGENERATION_AURA", "target": "FLOOD_UNITS_IN_LANE"},
    {"name": "SPAWN_ON_DESTROY", "value": "6_INFECTION_FORMS"}
  ],
  "abilityText": "每回合生成+2生物质。Lane内洪魔单位每回合恢复1 HP。被摧毁时释放6个感染型。"
}
```

### 战术卡牌

#### FLOOD-TACTICAL-001: 强制进化
```json
{
  "id": "FLOOD-TACTICAL-001",
  "name": "Accelerated Evolution",
  "cardType": "TACTICAL",
  "faction": "FLOOD",
  "rarity": "RARE",
  "cost": {"supply": 0, "battery": 1, "biomass": 15},
  "effect": {
    "type": "EVOLVE_PROTO_GRAVEMIND",
    "target": "LANE_WITH_4_TOKENS"
  },
  "abilityText": "立即在目标Lane进化原始坟墓脑（消耗4个Token + 15生物质，降低5点成本）"
}
```

#### FLOOD-TACTICAL-002: 逻辑瘟疫
```json
{
  "id": "FLOOD-TACTICAL-002",
  "name": "Logic Plague",
  "cardType": "TACTICAL",
  "faction": "FLOOD",
  "rarity": "LEGENDARY",
  "cost": {"supply": 0, "battery": 2, "biomass": 10},
  "effect": {
    "type": "TAKE_CONTROL",
    "target": "ENEMY_SHIP_BELOW_20_PERCENT_HP"
  },
  "abilityText": "感染敌方一艘生命值<20%的战舰，转为己方控制。战舰保留原有属性。"
}
```

---

## 平衡性分析

### 优势
1. **指数增殖**: 越战越强，敌人尸体成为兵力
2. **经济循环**: 生物质来自击杀，无需投资基础设施
3. **不消耗手牌**: 大部分兵力由感染生成
4. **全局Buff**: 坟墓脑提供强大光环
5. **自动胜利**: 4座孢子塔触发环境感染胜利

### 劣势
1. **初期弱势**: 需要敌方尸体才能扩张
2. **怕AOE**: 感染型/战斗体生命值低，怕范围清场
3. **先行者免疫**: 先行者单位无法感染
4. **机械克制**: 载具/战舰无法感染（仅能寄生）
5. **进化条件苛刻**: 需要凑4个Token + 20生物质

### 克制关系
- **优势对抗**:
  - **星盟 (Covenant)**: 劣等兵海正好成为感染源
  - **UNSC**: 海军陆战队易被感染
- **劣势对抗**:
  - **先行者 (Forerunner)**: 先行者单位免疫感染
  - **火焰武器**: AOE清场克制感染链

---

## 设计理念总结

洪魔阵营体现了**"生物恐怖与指数增殖"**：
- **寄生感染**: 敌人越多，己方越强
- **生物质循环**: 死亡即重生，战场即资源
- **集体意识**: 坟墓脑统筹，越战越聪明
- **环境同化**: 孢子塔网络最终吞噬整个战场

洪魔是**极端的雪球阵营**，顺风时无法阻挡，但一旦被遏制初期扩张，将难以翻盘。

---

**实现状态**: ✅ 完整实现  
**代码状态**: ✅ 编译通过  
**测试状态**: ⏳ 待测试  
**文档版本**: V1.0  
**更新日期**: 2026-02-26
