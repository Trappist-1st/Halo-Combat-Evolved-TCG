# Halo: Combat Evolved TCG
# 卡牌数据格式与加载机制规范（V1.0）

> 目标：统一 40 张首发卡 + 舰船卡 + 衍生物卡 的存储、校验、加载与运行时生成方式，保证规则引擎“数据驱动”。

---

## 1. 总体方案选型

- **存储格式**：JSON（人类可读、版本可控、便于热更新）。
- **文件组织**：按卡牌类型与版本拆分（单位/战术/场地/舰船/衍生物模板）。
- **加载方式**：启动时全量读取 + Schema 校验 + 语义校验 + 索引构建。
- **运行时数据**：`CardDef`（静态定义） + `CardInstance`（对局实例）。
- **数据库建议**：首发阶段不必上数据库；后续可做 JSON → SQLite 导入层。

推荐目录：

```text
resources/
  cards/
    manifest.json
    units.v1.json
    armory.v1.json
    tactical.v1.json
    field.v1.json
    vessels.v1.json
    token-templates.v1.json
  schema/
    card-def.schema.json
    deck.schema.json
```

---

## 2. 卡牌 JSON Schema（核心字段）

## 2.1 顶层字段（CardDef）

每张卡必须包含：
- `id`：全局唯一，如 `UNSC-001`、`VES-COV-003`。
- `name`：显示名称。
- `version`：数据版本，如 `1.0.0`。
- `faction`：`UNSC | COVENANT | FLOOD | FORERUNNER | NEUTRAL`。
- `cardType`：`UNIT | ARMORY | TACTICAL | FIELD | VESSEL | TOKEN`。
- `rarity`：`COMMON | RARE | EPIC | LEGENDARY`。
- `deckLimit`：同名最多携带数量（默认 3）。
- `isLegendary`：是否传奇（若 true，构筑时建议限制为 1）。
- `cost`：`{ supply, battery }`。
- `tags`：如 `INFANTRY`、`VEHICLE`、`ELITE`。
- `keywords`：关键词列表（可带参数）。
- `stats`：单位基础数值（非单位可缺省）。
- `abilities`：触发器 + 条件 + 效果列表。
- `vesselStats`：仅舰船卡存在。
- `tokenTemplate`：仅 TOKEN 或可生成对象存在。

## 2.2 关键词结构

```json
{
  "name": "SHIELDED",
  "value": 2
}
```

- `name` 必须来自枚举表（见第 4 节校验）。
- `value` 为可选，适用于 `SHIELDED(X)`、`POINT_DEFENSE(X)` 等。

## 2.3 能力结构（触发器+条件+效果）

```json
{
  "id": "a_infect_on_kill",
  "trigger": "KILL_OCCURRED",
  "priority": 220,
  "limitPerTurn": 99,
  "conditions": [
    { "op": "SOURCE_HAS_KEYWORD", "args": ["INFECT"] },
    { "op": "TARGET_NOT_TAG", "args": ["VEHICLE"] }
  ],
  "effects": [
    {
      "type": "SUMMON_TOKEN",
      "target": "SOURCE_LANE_BACKLINE",
      "params": { "tokenId": "TOKEN-COMBAT-FORM", "count": 1 }
    }
  ]
}
```

---

## 3. 舰船卡扩展：`vesselStats` 子对象

舰船卡（`cardType = VESSEL`）必须带 `vesselStats`：

```json
{
  "tonnageClass": "ESCORT",
  "hangars": 1,
  "payloadType": "MIXED",
  "shieldHardening": 1,
  "pointDefense": 2,
  "multiSection": false,
  "sections": []
}
```

字段定义：
- `tonnageClass`：`ESCORT | LINE | CAPITAL`
- `hangars`：每回合可投送单位数量上限。
- `payloadType`：`MAC | PLASMA_BEAM | POINT_DEFENSE | MIXED`
- `shieldHardening`：每回合首次受伤减免值。
- `pointDefense`：每回合点防御拦截次数。
- `multiSection`：是否分部位结算。
- `sections`：多部位列表（旗舰可用）。

`sections` 示例：

```json
[
  { "name": "ShieldGenerator", "maxHp": 6, "critical": false },
  { "name": "Core", "maxHp": 8, "critical": true }
]
```

---

## 4. 校验体系（启动时）

校验分三层：

## 4.1 结构校验（Schema）

- 使用 JSON Schema（Draft 2020-12）。
- 校验必填字段、类型、枚举、最小值。

## 4.2 语义校验（Rules）

- `id` 不可重复。
- `deckLimit` 与 `isLegendary` 一致性：传奇卡建议 `deckLimit <= 1`。
- `keywords.name` 必须在引擎关键词枚举中。
- `abilities.trigger` 必须在事件枚举中。
- `effects.type` 必须在效果枚举中。
- 若 `cardType = VESSEL`，则必须有 `vesselStats`。
- 若 `cardType != VESSEL`，默认禁止出现 `vesselStats`。

## 4.3 交叉引用校验（References）

- `SUMMON_TOKEN.params.tokenId` 必须在 Token 模板表可找到。
- `manifest.json` 引用的文件都必须存在并可解析。
- 同名卡版本冲突（同 `id` 不同内容）必须拒绝启动。

启动策略建议：
- **严格模式（默认）**：任意校验失败则启动失败。
- **开发模式**：收集错误并继续，仅加载有效卡（用于编辑器预览）。

---

## 5. 衍生物（Token）与临时卡机制

## 5.1 设计原则

- 衍生物不是“硬编码实体”，而是由 `token-templates.v1.json` 定义。
- 运行时只生成 `CardInstance`，不修改原始 `CardDef`。

## 5.2 Token 模板示例

```json
{
  "id": "TOKEN-COMBAT-FORM",
  "name": "Combat Form Token",
  "version": "1.0.0",
  "faction": "FLOOD",
  "cardType": "TOKEN",
  "rarity": "COMMON",
  "deckLimit": 0,
  "isLegendary": false,
  "cost": { "supply": 0, "battery": 0 },
  "tags": ["INFANTRY"],
  "keywords": [{ "name": "INFECT" }],
  "stats": { "attack": 1, "shieldCap": 0, "healthCap": 1 },
  "abilities": []
}
```

## 5.3 运行时生成协议

- 触发 `SUMMON_TOKEN` 时：
  1) 从 `TokenRegistry` 获取模板。
  2) 复制模板构造 `CardInstance`（新 `instanceId`）。
  3) 写入来源链路：`sourceEventSeq`、`sourceCardId`、`ownerPlayerId`。
  4) 放置到目标槽位（失败则按规则丢弃或改为待命区）。

---

## 6. JSON 示例（首发可直接参考）

## 6.1 UNIT 示例（UNSC Marine Fireteam）

```json
{
  "id": "UNSC-001",
  "name": "Marine Fireteam",
  "version": "1.0.0",
  "faction": "UNSC",
  "cardType": "UNIT",
  "rarity": "COMMON",
  "deckLimit": 3,
  "isLegendary": false,
  "cost": { "supply": 1, "battery": 0 },
  "tags": ["INFANTRY"],
  "keywords": [
    { "name": "BALLISTIC" },
    { "name": "SQUAD" }
  ],
  "stats": { "attack": 1, "shieldCap": 0, "healthCap": 2 },
  "abilities": [
    {
      "id": "a_squad_bonus",
      "trigger": "DAMAGE_CALC_STARTED",
      "priority": 240,
      "limitPerTurn": 99,
      "conditions": [
        { "op": "SOURCE_IN_LANE_WITH_ALLY_TAG", "args": ["INFANTRY", "1"] }
      ],
      "effects": [
        { "type": "MODIFY_DAMAGE", "target": "SOURCE", "params": { "delta": 1, "maxBonus": 2 } }
      ]
    }
  ]
}
```

## 6.2 VESSEL 示例（UNSC Savannah）

```json
{
  "id": "VES-UNSC-SAVANNAH",
  "name": "UNSC Savannah",
  "version": "1.0.0",
  "faction": "UNSC",
  "cardType": "VESSEL",
  "rarity": "RARE",
  "deckLimit": 2,
  "isLegendary": false,
  "cost": { "supply": 4, "battery": 0 },
  "tags": ["VESSEL", "ESCORT"],
  "keywords": [
    { "name": "POINT_DEFENSE", "value": 1 },
    { "name": "TARGET_LINK" }
  ],
  "stats": { "attack": 0, "shieldCap": 3, "healthCap": 5 },
  "vesselStats": {
    "tonnageClass": "ESCORT",
    "hangars": 1,
    "payloadType": "MIXED",
    "shieldHardening": 0,
    "pointDefense": 1,
    "multiSection": false,
    "sections": []
  },
  "abilities": [
    {
      "id": "a_chaff",
      "trigger": "ORBITAL_STRIKE_DECLARED",
      "priority": 180,
      "limitPerTurn": 99,
      "conditions": [
        { "op": "SAME_LANE", "args": [] },
        { "op": "IS_ENEMY_SOURCE", "args": [] }
      ],
      "effects": [
        { "type": "ROLL_MISS_CHANCE", "target": "EVENT", "params": { "chancePct": 50 } }
      ]
    }
  ]
}
```

---

## 7. 牌库构筑与动态验证

## 7.1 构筑规则（推荐）

- 每套牌固定 `40` 张。
- 同名最多 `3` 张（`deckLimit` 覆盖默认值）。
- 传奇卡最多 `1` 张（`isLegendary=true`）。
- 可选：舰船卡最多 `6` 张，旗舰最多 `1` 张。

## 7.2 Deck JSON 示例

```json
{
  "deckId": "p1_unsc_midrange_v1",
  "owner": "player-1",
  "cards": [
    { "id": "UNSC-001", "count": 3 },
    { "id": "UNSC-004", "count": 1 },
    { "id": "VES-UNSC-SAVANNAH", "count": 2 }
  ]
}
```

## 7.3 动态验证流程

1. 统计总张数是否为 40。
2. 检查每条目 `id` 存在且可加载。
3. 检查 `count <= deckLimit`。
4. 若 `isLegendary=true`，强制 `count <= 1`。
5. 输出详细错误报告（包含 `deckId/cardId/reason`）。

---

## 8. Java 加载器骨架（最小可实现）

```java
public final class CardRepository {
    private final java.util.Map<String, CardDef> defsById = new java.util.HashMap<>();

    public CardDef get(String id) { return defsById.get(id); }
    public boolean contains(String id) { return defsById.containsKey(id); }
    public java.util.Collection<CardDef> all() { return defsById.values(); }

    void put(CardDef def) {
        if (defsById.putIfAbsent(def.id(), def) != null) {
            throw new IllegalStateException("Duplicate card id: " + def.id());
        }
    }
}

public final class CardLoader {
    private final SchemaValidator schemaValidator;
    private final SemanticValidator semanticValidator;

    public CardLoader(SchemaValidator schemaValidator, SemanticValidator semanticValidator) {
        this.schemaValidator = schemaValidator;
        this.semanticValidator = semanticValidator;
    }

    public CardRepository loadFromResourceDir(java.nio.file.Path resourceDir) {
        CardRepository repo = new CardRepository();

        Manifest manifest = ManifestReader.read(resourceDir.resolve("cards/manifest.json"));
        for (String file : manifest.cardFiles()) {
            java.nio.file.Path p = resourceDir.resolve("cards").resolve(file);
            String json = readUtf8(p);

            schemaValidator.validate("card-def.schema.json", json);
            java.util.List<CardDef> defs = CardJsonParser.parseList(json);

            for (CardDef def : defs) {
                semanticValidator.validate(def, repo);
                repo.put(def);
            }
        }

        semanticValidator.validateCrossReferences(repo);
        return repo;
    }

    private static String readUtf8(java.nio.file.Path p) {
        try {
            return java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read: " + p, e);
        }
    }
}
```

## 8.1 语义校验器骨架

```java
public final class SemanticValidator {
    private final java.util.Set<String> validKeywords;
    private final java.util.Set<String> validTriggers;
    private final java.util.Set<String> validEffectTypes;

    public void validate(CardDef def, CardRepository repo) {
        // id 唯一性在 repo.put() 保证
        if (def.cardType() == CardType.VESSEL && def.vesselStats() == null) {
            throw new IllegalArgumentException(def.id() + " missing vesselStats");
        }
        if (def.cardType() != CardType.VESSEL && def.vesselStats() != null) {
            throw new IllegalArgumentException(def.id() + " has unexpected vesselStats");
        }
        for (KeywordInstance k : def.keywords()) {
            if (!validKeywords.contains(k.name())) {
                throw new IllegalArgumentException(def.id() + " unknown keyword " + k.name());
            }
        }
        for (Ability a : def.abilities()) {
            if (!validTriggers.contains(a.trigger())) {
                throw new IllegalArgumentException(def.id() + " unknown trigger " + a.trigger());
            }
            for (Effect e : a.effects()) {
                if (!validEffectTypes.contains(e.type())) {
                    throw new IllegalArgumentException(def.id() + " unknown effect type " + e.type());
                }
            }
        }
    }

    public void validateCrossReferences(CardRepository repo) {
        // 校验 tokenId / card 引用等
    }
}
```

---

## 9. 版本与兼容策略

- `manifest.json` 记录当前卡池版本与文件清单。
- `CardDef.version` 支持精确比较（`major.minor.patch`）。
- 大版本变更（major）可并行保留旧版本加载器。
- 对局快照必须记录 `cardDataVersion`，保证回放一致。

`manifest.json` 示例：

```json
{
  "cardDataVersion": "1.0.0",
  "schemaVersion": "1.0.0",
  "cardFiles": [
    "units.v1.json",
    "armory.v1.json",
    "tactical.v1.json",
    "field.v1.json",
    "vessels.v1.json",
    "token-templates.v1.json"
  ]
}
```

---

## 10. 验收标准（DoD）

- 启动时能从 `resources/cards` 成功加载所有定义并通过校验。
- 任意未知关键词/触发器/效果类型会在启动阶段报错阻断。
- 构筑校验可准确拦截“同名超限、传奇超限、总张数错误”。
- `SUMMON_TOKEN` 可正确实例化 `Combat Form Token` 并放置到合法槽位。
- 回放日志中可追溯每张临时卡的来源事件（`sourceEventSeq`）。
