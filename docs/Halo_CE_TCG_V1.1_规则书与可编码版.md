# Halo: Combat Evolved TCG 规则书与可编码版（V1.1）

## 一、规则书（V1.1）

- 游戏类型：2人对战，牌库构筑式 TCG。
- 初始状态：每位玩家基地生命 `30`，起手 `5` 张。
- 牌库：每套 `40` 张；同名最多 `3` 张；“传奇”（如部分英雄）最多 `1` 张。
- 先手：先手玩家第1回合不抽牌。
- 胜利条件：
  - **歼灭胜**：将对手基地生命降至 `0`。
  - **全面控制胜**：你在自己回合结束时控制3条 Lane，连续达成2次。

### 战场与站位

- 战场有3条 Lane：`Alpha / Bravo / Charlie`。
- 每条 Lane 每位玩家最多4个单位：前排2、后排2。
- 前排优先承伤与拦截；后排默认受前排保护。

### 资源系统（Supplies / Battery）

- Supplies：每回合补给上限 +1（最大10），并回满到上限。
- Battery Power：仅部分牌需要；默认规则为“弃1张手牌，获得1点Battery（每回合最多1次）”。
- 费用支付顺序：先声明打出 → 校验目标 → 一次性支付 `Supply + Battery`。

### 回合流程（Combat Cycle）

1. **抽牌与充能阶段**
   - 抽1张。
   - 护盾充能：若单位在对手上回合未受伤，则其护盾回满。
2. **部署阶段**
   - 打出单位/军械/战术/场地。
   - 新上场单位默认有“部署疲劳”，本回合不能攻击（除非 `DROP_POD`）。
3. **交战阶段**
   - 主动玩家可让可攻击单位逐个发起攻击（每单位每回合最多1次）。
   - 目标规则：若敌方该 Lane 有前排，必须先选前排（除非效果明确无视）。
   - 伤害结算：先护盾后生命。
4. **结束阶段**
   - 结算“回合结束”触发。
   - 切换主动玩家。

### 战斗细则（统一判定）

- 伤害类型：`PLASMA / BALLISTIC / TRUE`。
- 基础顺序：修正收集 → 护盾结算 → 生命结算 → 阵亡检查 → 击杀触发。
- 同步伤害：单位战斗中的互殴伤害同时生效。
- 友军误伤：标注“整条Lane”或“全体单位”的效果会影响己方。

### 控制与占领定义

- 你在某 Lane 回合结束时满足以下即“控制该 Lane”：
  - 你方该 Lane 单位数 > 对方单位数；且
  - 对方该 Lane 前排为空。

### 关键机制（Halo 风味）

- **Noob Combo（协同规则）**：同一目标在同回合内先受 `PLASMA` 伤害并获得 `PLASMA_TAG`，再受 `BALLISTIC` 攻击时，该次 `BALLISTIC` 最终伤害 ×2（每目标每回合最多触发1次）。
- **Hijack（夺取）**：具 `HIJACK` 的单位与敌方载具同 Lane 时，可支付 `2` Supply 直接夺取该载具。
- **Battery 限速**：每回合最多转化1次，避免爆发失控。

---

## 二、可编码版 V1.1｜完整关键词字典

- `SHIELDED(X)`：入场/获得时提高护盾上限X；受伤先扣护盾；若单位在对手上回合未受伤，你回合开始时护盾回满。
- `ARMOR`：受到 `BALLISTIC` 或通用物理伤害时伤害 `-1`（最低1）；不减 `TRUE`。
- `EMP`：本回合若你对载具造成过伤害，则该载具下回合 `cannotAttack=true` 且 `cannotMove=true`。
- `CAMO`：不能成为单体效果目标；宣告攻击后失效至回合结束。
- `HIJACK`：同 Lane 面对敌方载具时可支付2补给夺取。
- `PLASMA`：对护盾伤害 ×2；对生命伤害 ×0.5（向下取整，至少1）。
- `BALLISTIC`：标准伤害类型。
- `HEADSHOT`：若目标当前护盾为0，则本次攻击伤害 ×2。
- `INFECT`：本单位击杀敌方**非载具**单位后，在同 Lane 后排生成 `Combat Form Token(1/0/1)`。
- `DROP_POD`：入场当回合可攻击（无部署疲劳）。
- `RANGED`：可指定后排单位为攻击目标（仍不能越过“不可被选中”效果）。
- `SENTINEL`：攻击时无视护盾，直接对生命造成伤害。
- `SQUAD`：与同 Lane 友方步兵形成编组增益（由卡面给出具体数值与上限）。
- `VEHICLE`：类型标签（被 `EMP`、`HIJACK`、反载具效果引用）。

### 建议状态标记（引擎）

- `damagedLastOpponentTurn`
- `plasmaTaggedThisTurn`
- `attackedThisTurn`
- `cannotAttackUntilTurn`
- `cannotMoveUntilTurn`
- `hasCamoThisTurn`

---

## 三、40张首发卡池（费用/数值/效果）

### UNSC（10）

1. `UNSC-001` Marine Fireteam｜单位｜费1｜`1/0/2`｜`BALLISTIC,SQUAD`｜同Lane每有1个其他友方步兵，本单位+1攻击（最多+2）。
2. `UNSC-002` ODST Drop Trooper｜单位｜费2｜`2/0/2`｜`BALLISTIC,DROP_POD`｜无。
3. `UNSC-003` Corpsman Medic｜单位｜费2｜`1/0/3`｜无｜入场：治疗1个友军单位2生命。
4. `UNSC-004` Spartan-IV Breacher｜单位｜费4｜`3/2/4`｜`SHIELDED(2),BALLISTIC,HIJACK`｜无。
5. `UNSC-005` M12 Warthog｜单位(载具)｜费3｜`3/0/5`｜`VEHICLE,ARMOR,BALLISTIC`｜无。
6. `UNSC-006` Battle Rifle｜军械-武器｜费2｜附着步兵/斯巴达｜附着单位+2攻击并获得`HEADSHOT`。
7. `UNSC-007` M90 Shotgun｜军械-武器｜费1｜附着步兵/斯巴达｜附着单位+1攻击；攻击前排时额外+1伤害。
8. `UNSC-008` Frag Grenade｜战术｜费2｜选择1条Lane｜对该Lane所有前排单位各造成2伤害（含友军）。
9. `UNSC-009` Medikit｜战术｜费1｜单体｜恢复目标3生命；若目标为UNSC单位，再抽1张。
10. `UNSC-010` Firebase Echo｜场地｜费3｜全场｜你方前排单位+1生命上限。

### Covenant（10）

11. `COV-001` Grunt Lance｜单位｜费1｜`1/1/1`｜`SHIELDED(1),PLASMA`｜无。
12. `COV-002` Jackal Marksman｜单位｜费3｜`2/1/2`｜`SHIELDED(1),RANGED,HEADSHOT,BALLISTIC`｜无。
13. `COV-003` Elite Zealot｜单位｜费4｜`4/3/3`｜`SHIELDED(3),PLASMA,CAMO`｜无。
14. `COV-004` Ghost Raider｜单位(载具)｜费3｜`3/1/4`｜`VEHICLE,SHIELDED(1),PLASMA`｜无。
15. `COV-005` Wraith Mortar｜单位(载具)｜费6｜`5/2/7`｜`VEHICLE,ARMOR,SHIELDED(2),PLASMA`｜无。
16. `COV-006` Energy Sword｜军械-武器｜费2｜附着精英/步兵/斯巴达｜+3攻击并获得`PLASMA`；本回合获得`CAMO`。
17. `COV-007` Plasma Pistol Overcharge｜军械-武器｜费1｜附着任意单位｜+1攻击并获得`PLASMA,EMP`。
18. `COV-008` Plasma Grenade｜战术｜费2｜单体｜造成3点`PLASMA`伤害。
19. `COV-009` Glassing Beam｜战术｜费7 + Battery1｜选择1条Lane｜该Lane所有单位各受5伤害（含友军）。
20. `COV-010` High Charity Spire｜场地｜费4｜全场｜你方Covenant单位护盾上限+1。

### Flood（10）

21. `FLD-001` Infection Form Swarm｜单位｜费1｜`1/0/1`｜`INFECT`｜无。
22. `FLD-002` Combat Form｜单位｜费2｜`2/0/2`｜`INFECT`｜无。
23. `FLD-003` Carrier Form｜单位｜费3｜`1/0/4`｜无｜死亡：在同Lane后排生成2个`Combat Form Token(1/0/1)`。
24. `FLD-004` Pure Form Stalker｜单位｜费4｜`4/0/3`｜`CAMO`｜无。
25. `FLD-005` Flood Juggernaut｜单位｜费5｜`5/0/6`｜`ARMOR`｜无。
26. `FLD-006` Spore Tendrils｜军械-改件｜费1｜附着单位｜+1攻击并获得`INFECT`。
27. `FLD-007` Mutated Carapace｜军械-改件｜费2｜附着单位｜+3生命并获得`ARMOR`。
28. `FLD-008` Viral Burst｜战术｜费3｜全场单位｜全部单位各受1伤害；每有1个敌方单位因此死亡，你在对应Lane生成1个`Combat Form Token`（每Lane最多2）。
29. `FLD-009` Gravemind Whisper｜战术｜费4｜墓地目标｜将1张己方单位牌回手；其本回合费用-1。
30. `FLD-010` Infested Zone｜场地｜费3｜全场｜你回合结束时，若你控制任意Lane，在其中1条受控Lane生成1个`Combat Form Token`。

### Forerunner（10）

31. `FOR-001` Sentinel Drone｜单位｜费2｜`2/1/2`｜`SENTINEL,RANGED`｜无。
32. `FOR-002` Aggressor Sentinel｜单位｜费4｜`3/2/4`｜`SENTINEL`｜无。
33. `FOR-003` Enforcer｜单位｜费6｜`5/3/7`｜`SENTINEL,ARMOR`｜无。
34. `FOR-004` Monitor Custodian｜单位｜费5｜`2/4/5`｜`SHIELDED(4)`｜入场：抽1张；你方所有Forerunner单位护盾+1（不超上限）。
35. `FOR-005` Hardlight Architect｜单位｜费3｜`1/2/3`｜无｜入场：1个友军单位本回合获得`ARMOR`。
36. `FOR-006` Sentinel Beam｜军械-武器｜费2｜附着任意单位｜+2攻击并获得`SENTINEL`。
37. `FOR-007` Hardlight Shield｜军械-改件｜费2｜附着任意单位｜护盾上限+2并获得`ARMOR`。
38. `FOR-008` Constraint Pulse｜战术｜费3｜敌方全体载具｜施加`EMP`。
39. `FOR-009` Composer Surge｜战术｜费6｜单体｜消灭1个非载具单位（其当前生命≤4，忽略护盾）。
40. `FOR-010` Installation Network｜场地｜费4｜全场｜你每回合打出的第一张Forerunner牌费用-1（最低1）。

---

## 四、Java 枚举与数据结构草案（OpenJDK）

```java
import java.util.*;

enum Faction { UNSC, COVENANT, FLOOD, FORERUNNER, NEUTRAL }
enum CardType { UNIT, ARMORY, TACTICAL, FIELD }
enum UnitTag { INFANTRY, SPARTAN, ELITE, VEHICLE }

enum DamageType { BALLISTIC, PLASMA, TRUE }
enum Lane { ALPHA, BRAVO, CHARLIE }
enum Row { FRONT, BACK }

enum Keyword {
    SHIELDED, ARMOR, EMP, CAMO, HIJACK, PLASMA, BALLISTIC,
    HEADSHOT, INFECT, DROP_POD, RANGED, SENTINEL, SQUAD, VEHICLE
}

enum Trigger {
    ON_PLAY, ON_ATTACK_DECLARE, ON_DEAL_DAMAGE, ON_KILL,
    ON_TURN_START, ON_TURN_END, ON_DEATH, STATIC_AURA
}

enum EffectType {
    DEAL_DAMAGE, HEAL, DRAW, ADD_KEYWORD, REMOVE_KEYWORD, APPLY_STATUS,
    SUMMON_TOKEN, MODIFY_ATK, MODIFY_HP, MODIFY_SHIELD_CAP, DESTROY, REDUCE_COST
}

record KeywordInstance(Keyword keyword, int value) {}

record UnitStats(int attack, int shieldCap, int healthCap) {}

record Cost(int supply, int battery) {}

record TargetSpec(
    boolean enemyOnly,
    boolean allyOnly,
    boolean unitOnly,
    boolean vehicleOnly,
    boolean includeFront,
    boolean includeBack,
    boolean canTargetStealthed
) {}

record Condition(
    String expr // 例: "target.shield==0", "target.isVehicle", "owner.controlsLane"
) {}

record Effect(
    EffectType type,
    int amount,
    DamageType damageType,
    String statusKey,      // 例: "plasmaTaggedThisTurn"
    String summonCardId    // 例: "TOKEN-COMBAT-FORM"
) {}

record Ability(
    Trigger trigger,
    TargetSpec targetSpec,
    List<Condition> conditions,
    List<Effect> effects,
    int limitPerTurn
) {}

record CardDef(
    String id,
    String name,
    Faction faction,
    CardType cardType,
    Cost cost,
    Set<UnitTag> tags,                  // 非单位可为空
    UnitStats stats,                    // 非单位为null
    List<KeywordInstance> keywords,
    List<Ability> abilities
) {}
```

```java
// Noob Combo 处理建议
// 1) PLASMA 造成伤害后：给目标加 status "plasmaTaggedThisTurn"。
// 2) BALLISTIC 计算最终伤害时：若目标带该status且本回合未触发过combo，则伤害x2并标记combo已触发。
// 3) 回合结束清理 plasmaTaggedThisTurn / comboTriggeredThisTurn。
```
