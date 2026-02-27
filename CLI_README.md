# Halo CE TCG - 光环：星盟战争 TCG

## CLI 终端测试界面

这是一个用于测试 Halo CE TCG 游戏功能的命令行界面。

### 运行方式

#### 方式1：使用 Maven 运行

```bash
mvn compile exec:java -Dexec.mainClass="com.haloce.tcg.cli.GameCLI"
```

#### 方式2：编译后运行

```bash
# 编译项目
mvn clean compile

# 运行 CLI
mvn exec:java -Dexec.mainClass="com.haloce.tcg.cli.GameCLI"
```

### 可用命令

初始进入程序后，输入以下命令进行操作：

#### 基本命令

- `help` - 显示所有可用命令  
- `init [mode]` - 初始化新游戏
  - mode 可选值: `duel` (1v1), `team` (2v2), `ffa` (多人混战)
  - 示例: `init duel`
- `exit` 或 `quit` - 退出程序

#### 游戏信息

- `status` - 显示当前游戏状态（回合数、阶段、玩家等）
- `players` - 显示所有玩家信息（生命值、补给、手牌数等）
- `hand <playerId>` - 显示指定玩家的手牌
  - 示例: `hand P1`
- `battlefield` - 显示战场上所有单位的部署情况

#### 游戏操作

- `deploy <playerId> <cardIndex> <lane> <row>` - 从手牌部署单位到战场
  - playerId: 玩家ID (如 P1, P2)
  - cardIndex: 手牌中的卡牌索引 (从0开始)
  - lane: 战线 (ALPHA, BETA, GAMMA)
  - row: 行 (FRONTLINE 前线, BACKLINE 后线)
  - 示例: `deploy P1 0 ALPHA FRONTLINE`

- `attack <attackerId> <defenderId>` - 发起攻击
  - 使用单位ID的前8位字符
  - 示例: `attack a1b2c3d4 e5f6g7h8`

- `phase` - 进入下一个游戏阶段
- `endturn` - 结束当前回合，切换到下一个玩家

### 游戏流程示例

```
> init duel
> status
> hand P1
> deploy P1 0 ALPHA FRONTLINE
> phase
> endturn
> hand P2
```

### 当前状态

- ✅ 所有编译错误已修复
- ✅ CLI 界面已创建
- ✅ 基本命令系统已实现
- ⚠️ 卡牌加载需要完善
- ⚠️ 游戏规则实现正在开发中

### 注意事项

1. 当前版本的卡牌加载功能可能需要进一步完善
2. 部分游戏规则仍在开发中，可能会遇到未实现的功能
3. 建议先使用简单的操作测试基本流程

### 开发状态

项目当前处于活跃开发状态，主要修复了以下问题：

- ✅ GameStateManager 语法错误
- ✅ DeploymentHandler 方法调用
- ✅ TurnFlowHandler 方法调用  
- ✅ CombatHandler 方法签名
- ✅ 测试代码和其他小问题
- ✅ CLI 终端界面创建

### 贡献与反馈

如有问题或建议，请创建 Issue。
