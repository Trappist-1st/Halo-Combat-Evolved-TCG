# Halo: Combat Evolved TCG 各阵营舰船一览（V1.0）

> 适用模式：多人 FFA / 2v2 的“全域作战协议（轨道层 + 地表层）”

## 1. 设计总纲：舰船是战略平台，不是大号单位

在全域作战中，舰船承担三种职能：
- **制天权**：轨道层压制、反空投、反舰拦截。
- **投送权**：通过 `Hangars` 把单位从轨道投送到地表。
- **战役支援**：轨道炮、玻璃化、战术增益、干扰屏障。

舰船按吨位分为：
- **1 轻型舰（Escort）**：低费、快节奏、反制与战术辅助。
- **2 中型舰（Line）**：稳定主力、轨道对地压制核心。
- **3 旗舰/超重型（Capital/Supercarrier）**：改变规则的战场核心目标。

---

## 2. 舰船通用规则（多人版）

### 2.1 核心属性
- `Hull`：船体生命，不自动恢复。
- `Shields`：护盾，若本轮未被彻底击毁则在拥有者回合开始时回复到上限。
- `Hangars`：每回合可投送单位数量上限。
- `Point Defense`：用于拦截空投舱、小型导弹、登舰舱。

### 2.2 核心关键词
- `ORBITAL_BATTERY`：可对地表 Lane 发动轨道打击。
- `POINT_DEFENSE(X)`：每回合可拦截 X 次空投或小型弹药。
- `SHIELD_HARDENING(X)`：每回合首次受伤减免 X。
- `MULTI_SECTION`：舰船分部位结算（如“护盾发生器”“核心舱段”）。
- `TARGET_LINK`：为地面友军提供指引，提升协同打击精度。

### 2.3 吨位对规则的影响
- 轻型舰：可更早上场，偏功能型。
- 中型舰：数值稳定，主力输出。
- 旗舰：通常需要 `Supply + Battery`，且可触发“旗舰坠落”与“战略目标”结算。

---

## 3. Java 舰船数据模板（建议）

```java
enum TonnageClass { ESCORT, LINE, CAPITAL }
enum PayloadType { MAC, PLASMA_BEAM, POINT_DEFENSE, MIXED }
enum OrbitalRole { INTERCEPT, STRIKE, SUPPORT, CARRIER }

record VesselSection(String name, int maxHp, int currentHp, boolean critical) {}

class VesselState {
    String cardId;
    String name;
    String faction;

    TonnageClass tonnageClass;    // 1轻型/2中型/3旗舰
    OrbitalRole role;
    PayloadType payloadType;

    int shieldCap;
    int currentShields;
    int hullCap;
    int currentHull;

    int deployLimit;              // Hangars
    int deployedThisTurn;

    int pointDefenseCharges;
    int shieldHardening;

    boolean multiSection;
    java.util.List<VesselSection> sections;

    java.util.Set<String> keywords;
}
```

### 3.1 推荐扩展字段
- `targetLinkBonusPct`：指引打击增幅（如 50%）。
- `orbitalStrikeCooldown`：轨道武器冷却。
- `jamChancePct`：轨道打击干扰概率。
- `laneTaxSupply`：部署到该 Lane 的额外补给税（威慑类效果）。

---

## 4. 舰船对地交互：三段结算流程

### 4.1 轨道压制（Suppression）
触发条件：A 在某轨道扇区有舰船，B 在该扇区无舰船。  
效果：B 在该 Lane 执行“移动/撤退”前进行压制判定；失败则单位失去 1 点生命。

建议判定：
- 基础成功率 50%。
- 若 A 有 `ORBITAL_BATTERY`，B 判定 `-20%`。
- 若 B 有地面 `AA` 工事，B 判定 `+20%`。

### 4.2 战术空投（Orbital Deployment）
- 玩家先支付单位费用，将单位置入舰船机库队列。
- 结算窗口中按 `deployLimit` 投送到前排/后排空位。
- 空投单位可绕过敌方前排拦截，但落地后立即受地面规则约束。

### 4.3 协同打击（Combined Arms）
- 若地面单位拥有 `Laser Designator` 或目标处于 `PAINTED` 状态，
- 本回合对应轨道舰船可进行一次“零偏差”打击（命中判定视为成功，或伤害 +50%）。

---

## 5. 阵营舰船一览（首发建议）

## 5.1 UNSC

| 名称 | 级别 | 费用 | 属性 (S/H) | 机库 | 载荷 | 关键能力 |
|---|---|---:|---:|---:|---|---|
| Paris-class Frigate | 轻型舰 | 4 | 3/5 | 1 | MIXED | `TARGET_LINK`：同Lane友军地面攻击命中 +1 档 |
| Stalwart-class Frigate | 轻型舰 | 5 | 4/6 | 1 | POINT_DEFENSE | `POINT_DEFENSE(2)`，拦截空投与导弹 |
| Marathon-class Cruiser | 中型舰 | 7 | 6/10 | 2 | MAC | `ORBITAL_BATTERY`：对地重炮，偏破甲 |
| Autumn-class Heavy Cruiser | 中型舰 | 8 | 7/11 | 2 | MIXED | `SHIELD_HARDENING(1)` 与对地压制并存 |
| UNSC Infinity | 旗舰 | 10 + Battery1 | 10/14 | 4 | MAC | 每回合可免费投送1个 Infantry；`MULTI_SECTION` |
| UNSC Savannah | 轻型舰 | 4 | 3/5 | 1 | SUPPORT | `Chaff`：敌方轨道打击 50% 落空；投送单位本回合获得 `Quick Response` |

## 5.2 Covenant

| 名称 | 级别 | 费用 | 属性 (S/H) | 机库 | 载荷 | 关键能力 |
|---|---|---:|---:|---:|---|---|
| SDV-class Heavy Corvette | 轻型舰 | 4 | 4/4 | 1 | PLASMA_BEAM | `CAMO`：未攻击前不可被选中 |
| Zanar-pattern Light Cruiser | 轻型舰 | 2 | 2/2 | 1 | PLASMA_BEAM | 首次轨道打击附带 `Marked` |
| CCS-class Battlecruiser | 中型舰 | 8 | 8/9 | 2 | PLASMA_BEAM | `Energy Projector`：每2回合一次小范围玻璃化 |
| CPV-class Destroyer | 中型舰 | 7 | 7/8 | 2 | MIXED | 对被 `PAINTED` 目标伤害 +50% |
| CAS-class Assault Carrier | 旗舰 | 10 + Battery2 | 12/15 | 4 | PLASMA_BEAM | 该Lane Elite 获得 `Zealot` 增益；`MULTI_SECTION` |
| Shadow of Intent (CAS) | 旗舰 | 10 + Battery2 | 12/15 | 4 | PLASMA_BEAM | 回合结束对该Lane敌方地面单位各造成3点 `TRUE`；敌方部署到该Lane额外 +1 Supply |

## 5.3 Flood

| 名称 | 级别 | 费用 | 属性 (S/H) | 机库 | 载荷 | 关键能力 |
|---|---|---:|---:|---:|---|---|
| Infested Tender | 轻型舰 | 4 | 2/6 | 2 | SUPPORT | 投送的 Flood 单位获得 `Infect` |
| Spore Frigate | 轻型舰 | 5 | 2/7 | 2 | MIXED | 地面单位死亡时，有概率在该Lane生成 `Combat Form Token` |
| Proto-Gravemind Barge | 中型舰 | 7 | 3/11 | 3 | SUPPORT | 每回合可从弃牌区回收1个低费 Flood 单位到机库 |
| Corrupted Carrier Node | 中型舰 | 8 | 4/12 | 3 | MIXED | `Suppression` 失败的敌方单位额外 -1 生命 |
| Gravemind Nexus Hull | 旗舰 | 10 + Battery1 | 6/16 | 4 | SUPPORT | 被摧毁的 Flood 机械/异形单位有概率重生到后排 |

## 5.4 Forerunner

| 名称 | 级别 | 费用 | 属性 (S/H) | 机库 | 载荷 | 关键能力 |
|---|---|---:|---:|---:|---|---|
| Sentinel Dock Ring | 轻型舰 | 4 | 5/4 | 1 | POINT_DEFENSE | `POINT_DEFENSE(2)`；拦截成功后给目标施加 `Suppressed` |
| Monitor Escort Array | 轻型舰 | 5 | 6/5 | 1 | MIXED | 对地支援时为友军附加 `Shield +1` |
| Forerunner War Sphinx Carrier | 中型舰 | 8 | 8/10 | 2 | MIXED | 投送的 Sentinel 单位本回合可立即执行一次攻击 |
| Hardlight Bastion Ship | 中型舰 | 9 | 9/10 | 2 | MAC | `SHIELD_HARDENING(2)` |
| Ecumene Command Spire | 旗舰 | 10 + Battery2 | 13/13 | 3 | MIXED | `Reconstruction`：每回合可重建1个被摧毁的机械单位 |

---

## 6. 具体卡牌实例（落地版）

### 6.1 UNSC - 萨凡纳号（UNSC Savannah）
- 级别：轻型护卫舰（Paris-class）
- 费用：`4 Supply`
- 属性：`3S / 5H`
- 机库：`1`
- 能力：
  - **干扰箔（Chaff）**：本卡在场时，该 Lane 敌方轨道打击（MAC/等离子）有 50% 概率落空。
  - **支援空投**：由本卡投送至地面的单位本回合获得 `Quick Response`（可立即行动）。

### 6.2 星盟 - 鬼影号（CAS-class Shadow of Intent）
- 级别：旗舰（Supercarrier）
- 费用：`10 Supply + 2 Battery`
- 属性：`12S / 15H`
- 机库：`3`
- 能力：
  - **玻璃化光束（Glassing Beam）**：每回合结束，对该 Lane 地面层所有敌方单位造成 3 点 `TRUE` 伤害。
  - **母舰威慑**：敌方玩家向该 Lane 部署地面单位时，额外支付 `1 Supply`。

---

## 7. 平衡约束建议（防止旗舰统治）

- 每名玩家同一时间最多1艘 `Capital` 在场。
- 轨道打击与投送共享“轨道行动点”（建议每回合 2 点）。
- 旗舰被摧毁后，拥有者进入 `Disrupted`：下回合轨道层不能发动主动技能。
- `Chaff` 与 `Point Defense` 的拦截判定建议采用“不可叠加到100%”的上限（推荐 75%）。

---

## 8. 与“多阵营同场”规则的兼容说明

- 单位所属阵营可混战同场，不自动互为敌对。
- 敌我关系由玩家关系决定，目标合法性按“玩家-玩家关系”判断。
- 舰船增益若写“本阵营单位”，仅作用于同阵营卡牌；若写“友方单位”，作用于该玩家（或2v2队友）控制的单位。

> 结论：你提出的“同一战场可同时存在 UNSC、星盟、洪魔、先行者，最终仅一方获胜”是完全成立的，而且非常适合多人政治博弈。
