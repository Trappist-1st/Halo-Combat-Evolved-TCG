# 先行者（Forerunner）阵营机制实现文档

## 概述

先行者作为银河系曾经的主宰，其核心设计理念是**"绝对的秩序"、"物质重组"以及"跨维度打击能力"**。他们不依赖信仰或战术协同，而是通过**"系统规则的降维打击"**来主宰战场。

先行者玩家的体验类似于操作一个精密、冷酷的**"自动化工厂"**。

---

## 核心机制系统

### 1. 虚空能源与物质重组 (Vacuum Energy & Matter Reconfiguration)

**管理器**: `ForerunnerVacuumEnergyManager`

#### 核心特性
- **能级系统**: 先行者不使用补给/信仰,使用"能级 (Power Levels)"
- **物质重组**: 单位死亡时返还 **40%** 构建费用
- **单位自适应**: 可根据 Lane 切换形态,无需额外费用

#### Java 集成点
```java
// 在 CombatHandler.handleUnitDeath() 中
if (HandlerUtils.hasTag(dead, "FORERUNNER")) {
    int refund = campaignManager.forerunnerVacuumEnergy()
        .reconfigureMatter(dead.ownerPlayerId(), dead.instanceId());
    // 返还能量到玩家能级池
}
```

#### 游戏效果
- 先行者战线极具韧性,损失单位后可快速重建
- 前期能级低,启动缓慢
- 后期能级饱和,可进行大规模部署

---

### 2. 哨兵网络 (Sentinel Network)

**管理器**: `ForerunnerSentinelNetworkManager`

#### 核心特性
- **哨兵制造厂**: 每回合生成 2 个哨兵 Token
- **自动修复**: 哨兵回到制造厂 Lane 时,回合结束自动回满血
- **抑制器领域**: 降低敌方 Token 单位攻击速度 30%
- **群体智能**: 多个哨兵攻击力叠加 (`baseDamage + sentinelCount * 1.5`)
- **节点依赖**: 需要 Monitor/制造厂指挥,失去后命中率 -50%

#### 哨兵类型
```java
public enum SentinelType {
    AGGRESSOR,   // 构造者：光束追踪,攻击不落空
    PROTECTOR,   // 防御哨兵：提供护盾叠加
    SUPER        // 超级哨兵：防空专用,对舰船双倍伤害
}
```

#### 游戏流程
1. **部署制造厂** → 建立哨兵生产线
2. **哨兵自动生成** → 每回合 +2 Token
3. **群体作战** → 多个哨兵协同攻击
4. **受损修复** → 哨兵返回制造厂 Lane 自动回血
5. **节点保护** → Monitor 被摧毁后哨兵能力大幅下降

---

### 3. 普罗米修斯系统 (Promethean System)

**管理器**: `ForerunnerPrometheanManager`

#### 核心特性
- **数据重组**: 死亡时掉落"数据残影",可被观察者复活
- **骑士形态**: 携带观察者,可切换重装/机动形态
- **爬行者攀爬**: 可跳过前排,直接攻击后排
- **观察者复活**: 每 3 回合可复活 1 个骑士

#### 单位类型
```java
public enum PrometheanType {
    KNIGHT,   // 骑士: 重装单位,伤害 +30% (重装形态)
    CRAWLER,  // 爬行者: 可攀爬,绕过前排
    WATCHER   // 观察者: 拦截投射物,复活队友
}
```

#### 骑士受损流程
```java
// 1. 骑士受到伤害
// 2. 观察者自动弹出
String watcherId = campaignManager.forerunnerPromethean()
    .ejectWatcher(knightId);
// 3. 观察者进行维修
// 4. 骑士死亡 → 生成数据残影
campaignManager.forerunnerPromethean().recordDeath(
    instanceId, lane, turnIndex, unitId, playerId
);
```

#### 复活序列
```java
// 观察者每 3 回合可复活骑士
if (campaignManager.forerunnerPromethean()
    .canReviveKnight(watcherId, currentTurn)) {
    String revivedUnitId = campaignManager.forerunnerPromethean()
        .reviveKnight(watcherId, deadKnightId, currentTurn);
    // 重生骑士单位
}
```

---

### 4. 环带协议 (Halo Array Protocol)

**管理器**: `ForerunnerHaloArrayManager`

#### 核心特性
- **索引器收集**: 需要收集 **3 张**索引器卡牌
- **终端激活**: 在地标 (Objective) 停留 **3 个回合**
- **脉冲发射**: 清除指定 Lane **所有生物单位** (不包括机械)

#### 威慑博弈
- 先行者开始收集索引器时,其他阵营必须立即考虑是否暂时停火
- 共同冲入先行者 Lane 破坏终端
- 终端被破坏后索引器不丢失,可重新激活

#### 脉冲效果等级
```java
public enum PulseEffectType {
    LANE_CLEAR,      // 局部: 清除 1 个 Lane
    SECTOR_CLEAR,    // 中级: 清除 3 个相邻 Lane
    GALAXY_RESET     // 终极: 全局重置 (游戏结束)
}
```

#### 激活流程
```java
// 1. 收集索引器
campaignManager.forerunnerHaloArray()
    .collectIndex(playerId, "INDEX-ALPHA");

// 2. 检查是否集齐
if (campaignManager.forerunnerHaloArray().hasAllIndices(playerId)) {
    // 3. 开始激活终端
    campaignManager.forerunnerHaloArray()
        .startTerminalActivation(playerId, lane, objectiveId, currentTurn);
}

// 4. 每回合检查进度
boolean ready = campaignManager.forerunnerHaloArray()
    .updateActivationProgress(playerId, stillAtObjective, currentTurn);

// 5. 发射脉冲
if (ready) {
    Lane targetLane = campaignManager.forerunnerHaloArray()
        .firePulse(playerId);
    // 清除该 Lane 所有生物单位
}
```

---

### 5. 空间折叠 (Slipspace Technology)

**管理器**: `ForerunnerSlipspaceManager`

#### 核心特性
- **空间传送**: 瞬间将单位从 Lane A 传送到 Lane B (冷却 2 回合)
- **重力井**: 拦截敌方空投,弹回对方手牌 (不摧毁)
- **跃迁门**: 建立 Lane 之间的传送门网络

#### 传送流程
```java
// 1. 建立跃迁门
campaignManager.forerunnerSlipspace()
    .establishSlipspaceGate(Lane.ALPHA, Lane.GAMMA);

// 2. 检查是否可传送
if (campaignManager.forerunnerSlipspace()
    .getTeleportCooldownRemaining(playerId, currentTurn) == 0) {
    // 3. 执行传送
    boolean success = campaignManager.forerunnerSlipspace()
        .teleportUnit(playerId, instanceId, fromLane, toLane, currentTurn);
}
```

#### 重力井拦截
```java
// 敌方空投时触发
if (campaignManager.forerunnerSlipspace()
    .isGravityWellActive(playerId, lane)) {
    // 拦截空投,单位返回手牌
    campaignManager.forerunnerSlipspace()
        .interceptAirdrop(playerId, lane, airdropInstanceId);
    // 强制对方浪费回合
}
```

---

### 6. 硬光武器 (Hardlight Weaponry)

**管理器**: `ForerunnerHardlightWeaponManager`

#### 核心特性
- **穿透伤害**: 攻击前排时, **30%** 伤害穿透至后排
- **硬光护盾**: 可叠加 3 层能量护盾
- **光刃**: 充能状态下近战伤害 **×2**

#### 穿透逻辑 (已集成到 CombatHandler)
```java
// 攻击前排单位后
if (!result.lethal() && defenderPos.row() == GameRow.FRONTLINE) {
    int penetrationDamage = campaignManager.forerunnerHardlight()
        .applyPenetration(attackerInstanceId, result.healthDamage());
    if (penetrationDamage > 0) {
        // 对后排单位造成穿透伤害 (约30%原始伤害)
    }
}
```

#### 武器类型与伤害修正
```java
public enum HardlightWeaponType {
    SUPPRESSOR,           // 压制者: 连射 (远程 0.8x, 近战 0.6x)
    BOLTSHOT,            // 脉冲枪: 精准 (远程 1.2x, 近战 1.5x)
    LIGHTRIFLE,          // 光束步枪: 高精度 (远程 1.3x, 近战 0.9x)
    SCATTERSHOT,         // 散射枪: 近距离 (远程 0.7x, 近战 1.8x)
    INCINERATION_CANNON  // 焚化炮: 重型 (2.0x)
}
```

#### 护盾系统
```java
// 添加护盾层
campaignManager.forerunnerHardlight()
    .addShieldLayer(instanceId);  // 最多 3 层

// 受到攻击时移除
campaignManager.forerunnerHardlight()
    .removeShieldLayer(instanceId);  // 每次受击 -1 层

// EMP 攻击额外损失 1 层
if (isEMPAttack && hasActiveShield(instanceId)) {
    removeShieldLayer(instanceId);  // 硬光构造物脆弱性
}
```

---

### 7. 合成器系统 (Composer)

**管理器**: `ForerunnerComposerManager`

#### 核心特性
- **建造合成工厂**: 7 费用,解锁单位进化
- **收集生物数据**: 敌方生物单位阵亡时自动收集
- **哨兵进化**: 消耗数据将哨兵升级为普罗米修斯单位

#### 升级路径
```java
// 哨兵 → 骑士: 5 个数据, 3 回合
// 哨兵 → 爬行者: 2 个数据, 1 回合
// 哨兵 → 观察者: 3 个数据, 2 回合
```

#### 进化流程
```java
// 1. 建造合成工厂
campaignManager.forerunnerComposer()
    .buildComposerLab(playerId);

// 2. 收集生物数据 (在 CombatHandler 中自动触发)
if (!HandlerUtils.hasTag(dead, "FORERUNNER") 
    && !HandlerUtils.hasTag(dead, "VEHICLE")) {
    campaignManager.forerunnerComposer()
        .collectBiologicalData(attackerPlayerId, dead.definition().id());
}

// 3. 开始哨兵升级
campaignManager.forerunnerComposer().startSentinelUpgrade(
    playerId, sentinelId, PrometheanTargetType.KNIGHT, currentTurn
);

// 4. 检查升级进度
if (campaignManager.forerunnerComposer()
    .isUpgradeComplete(sentinelId, currentTurn)) {
    var targetType = campaignManager.forerunnerComposer()
        .completeUpgrade(sentinelId);
    // 移除哨兵,生成普罗米修斯单位
}
```

#### 直接合成 (高级)
```java
// 消耗双倍数据,直接生成普罗米修斯单位 (无需基础哨兵)
if (campaignManager.forerunnerComposer()
    .canDirectCompose(playerId, PrometheanTargetType.KNIGHT)) {
    campaignManager.forerunnerComposer()
        .directCompose(playerId, PrometheanTargetType.KNIGHT);
    // 立即部署骑士
}
```

---

## 战术定位与平衡

### 优势
- **极高的资源效率**: 物质重组返还 40% 费用
- **自动化防御**: 哨兵制造厂持续生成 Token
- **穿透打击**: 硬光武器穿透前排
- **战术威慑**: 环带协议迫使对手放弃进攻

### 劣势
- **启动极慢**: 能级需要逐回合提升,前期非常空虚
- **昂贵**: 每个单位损失都是巨大的资源倒退
- **缺乏爆发**: 伤害稳定但缺乏一波流能力
- **节点依赖**: Monitor 被摧毁后哨兵能力大幅下降

### 战术建议
1. **前期**: 快速建造哨兵制造厂,建立防御网
2. **中期**: 建造合成工厂,开始收集生物数据
3. **后期**: 升级哨兵为普罗米修斯,启动环带威慑

---

## 事件类型列表

```java
// 新增的先行者事件 (已添加到 EventType.java)
FORERUNNER_MATTER_RECONFIGURED,          // 物质重组
FORERUNNER_POWER_LEVEL_INCREASED,        // 能级提升
SENTINEL_MANUFACTURED,                    // 哨兵生成
SENTINEL_AUTO_REPAIRED,                   // 自动修复
SUPPRESSOR_FIELD_ACTIVATED,               // 抑制器领域
PROMETHEAN_DATA_REMNANT_CREATED,          // 数据残影
PROMETHEAN_UNIT_REVIVED,                  // 单位复活
HALO_INDEX_COLLECTED,                     // 索引器收集
HALO_PULSE_FIRED,                         // 脉冲发射
SLIPSPACE_UNIT_TELEPORTED,                // 单位传送
GRAVITY_WELL_INTERCEPTED,                 // 空投拦截
HARDLIGHT_PENETRATION_TRIGGERED,          // 穿透伤害
COMPOSER_LAB_BUILT,                       // 合成工厂
SENTINEL_UPGRADE_COMPLETED                // 哨兵升级
```

---

## 示例卡牌数据

所有先行者示例卡牌已创建在:
`src/main/resources/cards/forerunner-units.v1.json`

包含:
- 哨兵制造厂
- 普罗米修斯骑士/爬行者/观察者
- 合成器工厂
- 环带索引器
- 硬光武器
- 跃迁门/重力井
- 343罪恶火花 (Monitor)

---

## 集成状态

✅ **已完成**:
- 7 个先行者管理器类
- CampaignManager 集成
- CombatHandler Hook (物质重组、硬光穿透、数据收集)
- EventType 事件定义
- 示例卡牌数据

⏳ **待后续实现**:
- DeploymentHandler 哨兵部署 Hook
- TurnFlowHandler 能级提升/哨兵修复 Hook
- 环带协议的胜利条件判定
- UI 展示能级/数据残影

---

## 致谢

先行者系统设计体现了 Halo 宇宙中技术种族的特征:
- 冷酷的效率
- 自动化的战争机器
- 对生命的改造与转化
- 绝对的技术优势

这是一个与 UNSC 的"战术协同"和星盟的"宗教热血"截然不同的玩法体验。
