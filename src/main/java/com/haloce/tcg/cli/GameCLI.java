package com.haloce.tcg.cli;

import com.haloce.tcg.card.loader.CardLoader;
import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.deck.DeckLoader;
import com.haloce.tcg.deck.model.DeckDef;
import com.haloce.tcg.game.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Halo CE TCG - Command Line Interface
 * 用于测试游戏功能的命令行界面
 */
public class GameCLI {
    private static final Scanner scanner = new Scanner(System.in);
    private GameStateManager game;
    private CardRepository cardRepository;
    private GameEngine engine;

    public static void main(String[] args) {
        GameCLI cli = new GameCLI();
        cli.run();
    }

    public void run() {
        printWelcome();
        
        while (true) {
            try {
                System.out.print("\n> ");
                String input = scanner.nextLine().trim();
                
                if (input.isEmpty()) {
                    continue;
                }
                
                String[] parts = input.split("\\s+");
                String command = parts[0].toLowerCase();
                
                switch (command) {
                    case "help":
                        printHelp();
                        break;
                    case "init":
                        initializeGame(parts);
                        break;
                    case "status":
                        showGameStatus();
                        break;
                    case "hand":
                        showHand(parts);
                        break;
                    case "battlefield":
                        showBattlefield();
                        break;
                    case "deploy":
                        deployUnit(parts);
                        break;
                    case "attack":
                        performAttack(parts);
                        break;
                    case "phase":
                        advancePhase();
                        break;
                    case "endturn":
                        endTurn();
                        break;
                    case "players":
                        showPlayers();
                        break;
                    case "exit":
                    case "quit":
                        System.out.println("感谢游玩 Halo CE TCG!");
                        return;
                    default:
                        System.out.println("未知命令: " + command + "  (输入 'help' 查看帮助)");
                }
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void printWelcome() {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║     Halo CE TCG - Command Line Interface          ║");
        System.out.println("║     光环：星盟战争 - 命令行测试界面                  ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println("\n输入 'help' 查看可用命令");
        System.out.println("输入 'init' 初始化新游戏");
    }

    private void printHelp() {
        System.out.println("\n=== 可用命令 ===");
        System.out.println("  init [mode]           - 初始化游戏 (mode: duel, team, ffa)");
        System.out.println("  status                - 显示游戏状态");
        System.out.println("  players               - 显示玩家信息");
        System.out.println("  hand <playerId>       - 显示玩家手牌");
        System.out.println("  battlefield           - 显示战场状态");
        System.out.println("  deploy <playerId> <cardIndex> <lane> <row> - 部署单位");
        System.out.println("                        lane: ALPHA, BETA, GAMMA");
        System.out.println("                        row: FRONTLINE, BACKLINE");
        System.out.println("  attack <attackerId> <defenderId> - 发起攻击");
        System.out.println("  phase                 - 进入下一阶段");
        System.out.println("  endturn               - 结束回合");
        System.out.println("  help                  - 显示此帮助信息");
        System.out.println("  exit/quit             - 退出程序");
    }

    private void initializeGame(String[] args) {
        try {
            // 加载卡牌数据
            System.out.println("正在加载卡牌数据...");
            
            // 使用CardLoader从资源目录加载卡牌
            CardLoader cardLoader = new CardLoader(new com.haloce.tcg.card.loader.SemanticValidator());
            try {
                Path resourcePath = java.nio.file.Paths.get("src/main/resources");
                if (!java.nio.file.Files.exists(resourcePath)) {
                    // 尝试从classpath加载
                    resourcePath = java.nio.file.Paths.get("target/classes");
                }
                cardRepository = cardLoader.loadFromResourceDir(resourcePath);
                System.out.println("✓ 加载了 " + cardRepository.size() + " 张卡牌定义");
            } catch (Exception e) {
                System.out.println("⚠ 无法从文件加载卡牌: " + e.getMessage());
                System.out.println("  使用空的卡牌仓库（仅供测试）");
                cardRepository = new CardRepository();
            }

            // 创建游戏引擎
            engine = new GameEngine(cardRepository);

            // 确定游戏模式
            GameMode mode = GameMode.DUEL_1V1;
            if (args.length > 1) {
                mode = switch (args[1].toLowerCase()) {
                    case "team" -> GameMode.TEAM_2V2;
                    case "ffa" -> GameMode.FFA;
                    default -> GameMode.DUEL_1V1;
                };
            }

            // 初始化游戏
            System.out.println("正在初始化游戏 (模式: " + mode + ")...");
            Map<String, DeckDef> decksByPlayer = loadDefaultDecks(mode);
            
            // 转换为 PlayerSetup 列表
            List<PlayerSetup> setups = new ArrayList<>();
            for (Map.Entry<String, DeckDef> entry : decksByPlayer.entrySet()) {
                setups.add(new PlayerSetup(entry.getKey(), entry.getValue()));
            }
            
            game = engine.initializeMatch(mode, setups);

            System.out.println("✓ 游戏初始化成功!");
            System.out.println("  当前玩家: " + game.activePlayerId());
            System.out.println("  游戏阶段: " + game.phase());
            
        } catch (Exception e) {
            System.out.println("✗ 初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, DeckDef> loadDefaultDecks(GameMode mode) {
        Map<String, DeckDef> decks = new HashMap<>();
        
        try {
            // 尝试加载预定义的测试卡组
            DeckLoader deckLoader = new DeckLoader();
            DeckDef deck1 = deckLoader.load(java.nio.file.Paths.get("src/main/resources/decks/p1_demo_deck.v1.json"));
            decks.put("P1", deck1);
            
            // 为其他玩家复制卡组
            decks.put("P2", deck1);
            if (mode == GameMode.TEAM_2V2) {
                decks.put("P3", deck1);
                decks.put("P4", deck1);
            } else if (mode == GameMode.FFA) {
                decks.put("P3", deck1);
            }
            
            System.out.println("✓ 成功加载测试卡组");
        } catch (Exception e) {
            System.out.println("⚠ 无法加载预定义卡组: " + e.getMessage());
            // 创建简单的默认卡组
            decks = createBasicDecks(mode);
        }
        
        return decks;
    }

    private Map<String, DeckDef> createBasicDecks(GameMode mode) {
        // 创建最基本的卡组用于测试
        DeckDef basicDeck = new DeckDef(
            "basic_test_deck",
            "P1",
            new ArrayList<>()
        );
        
        Map<String, DeckDef> decks = new HashMap<>();
        decks.put("P1", basicDeck);
        decks.put("P2", basicDeck);
        
        if (mode == GameMode.TEAM_2V2) {
            decks.put("P3", basicDeck);
            decks.put("P4", basicDeck);
        } else if (mode == GameMode.FFA) {
            decks.put("P3", basicDeck);
        }
        
        return decks;
    }

    private void showGameStatus() {
        if (game == null) {
            System.out.println("游戏未初始化。请先使用 'init' 命令。");
            return;
        }

        System.out.println("\n=== 游戏状态 ===");
        System.out.println("状态: " + game.status());
        System.out.println("模式: " + game.gameMode());
        System.out.println("阶段: " + game.phase());
        System.out.println("回合数: " + game.globalTurnIndex());
        System.out.println("轮次: " + game.roundIndex());
        System.out.println("当前玩家: " + game.activePlayerId());
        
        if (game.winnerPlayerId() != null) {
            System.out.println("\n🏆 胜者: " + game.winnerPlayerId());
        }
    }

    private void showPlayers() {
        if (game == null) {
            System.out.println("游戏未初始化。");
            return;
        }

        System.out.println("\n=== 玩家列表 ===");
        for (String playerId : game.playerIds()) {
            PlayerState player = game.player(playerId);
            System.out.println("\n玩家 " + playerId + ":");
            System.out.println("  生命值: " + player.baseHealth());
            System.out.println("  补给: " + player.currentSupply() + "/" + player.supplyCap());
            System.out.println("  手牌数: " + player.hand().size());
            System.out.println("  牌库数: " + player.librarySize());
            System.out.println("  弃牌堆: " + player.discardSize());
        }
    }

    private void showHand(String[] args) {
        if (game == null) {
            System.out.println("游戏未初始化。");
            return;
        }

        if (args.length < 2) {
            System.out.println("用法: hand <playerId>");
            return;
        }

        String playerId = args[1];
        try {
            PlayerState player = game.player(playerId);
            List<CardInstance> hand = player.hand();

            System.out.println("\n=== " + playerId + " 的手牌 ===");
            if (hand.isEmpty()) {
                System.out.println("(空)");
                return;
            }

            for (int i = 0; i < hand.size(); i++) {
                CardInstance card = hand.get(i);
                System.out.printf("[%d] %s (ID: %s)\n",
                    i,
                    card.definition().name(),
                    card.instanceId().substring(0, 8)
                );
            }
        } catch (Exception e) {
            System.out.println("错误: " + e.getMessage());
        }
    }

    private void showBattlefield() {
        if (game == null) {
            System.out.println("游戏未初始化。");
            return;
        }

        BattlefieldState battlefield = game.battlefield();
        System.out.println("\n=== 战场状态 ===");

        for (Lane lane : Lane.values()) {
            System.out.println("\n" + lane + " 战线:");
            
            LaneState laneState = battlefield.lane(lane);
            for (GameRow row : GameRow.values()) {
                System.out.println("  " + row + ":");
                
                boolean foundAny = false;
                for (String playerId : game.playerIds()) {
                    List<CardInstance> unitsHere = laneState.side(playerId).allUnits().stream()
                        .filter(card -> {
                            GameRow cardRow = laneState.side(playerId).rowOf(card.instanceId());
                            return cardRow == row;
                        })
                        .collect(Collectors.toList());
                    
                    for (CardInstance card : unitsHere) {
                        System.out.printf("    [%s] %s (玩家: %s)\n",
                            card.instanceId().substring(0, 8),
                            card.definition().name(),
                            playerId
                        );
                        foundAny = true;
                    }
                }
                
                if (!foundAny) {
                    System.out.println("    (空)");
                }
            }
        }
    }

    private void deployUnit(String[] args) {
        if (game == null) {
            System.out.println("游戏未初始化。");
            return;
        }

        if (args.length < 5) {
            System.out.println("用法: deploy <playerId> <cardIndex> <lane> <row>");
            System.out.println("示例: deploy P1 0 ALPHA FRONTLINE");
            return;
        }

        try {
            String playerId = args[1];
            int cardIndex = Integer.parseInt(args[2]);
            Lane lane = Lane.valueOf(args[3].toUpperCase());
            GameRow row = GameRow.valueOf(args[4].toUpperCase());

            PlayerState player = game.player(playerId);
            List<CardInstance> hand = player.hand();

            if (cardIndex < 0 || cardIndex >= hand.size()) {
                System.out.println("错误: 无效的卡牌索引");
                return;
            }

            CardInstance card = hand.get(cardIndex);
            game.deployUnitFromHand(playerId, card.instanceId(), lane, row);
            
            System.out.println("✓ 成功部署: " + card.definition().name());
        } catch (Exception e) {
            System.out.println("✗ 部署失败: " + e.getMessage());
        }
    }

    private void performAttack(String[] args) {
        if (game == null) {
            System.out.println("游戏未初始化。");
            return;
        }

        if (args.length < 3) {
            System.out.println("用法: attack <attackerId> <defenderId>");
            System.out.println("使用前8位ID字符即可");
            return;
        }

        try {
            String attackerIdPrefix = args[1];
            String defenderIdPrefix = args[2];

            // 在战场上查找匹配的单位
            BattlefieldState battlefield = game.battlefield();
            
            UnitPosition attacker = findUnitByIdPrefix(battlefield, attackerIdPrefix);
            UnitPosition defender = findUnitByIdPrefix(battlefield, defenderIdPrefix);

            if (attacker == null) {
                System.out.println("错误: 找不到攻击者单位");
                return;
            }
            if (defender == null) {
                System.out.println("错误: 找不到防御者单位");
                return;
            }

            game.declareAttack(attacker.card().instanceId(), defender.card().instanceId());
            System.out.println("✓ 攻击执行成功");
            
        } catch (Exception e) {
            System.out.println("✗ 攻击失败: " + e.getMessage());
        }
    }

    private UnitPosition findUnitByIdPrefix(BattlefieldState battlefield, String idPrefix) {
        for (Lane lane : Lane.values()) {
            LaneState laneState = battlefield.lane(lane);
            UnitPosition pos = laneState.locate(idPrefix + "*", lane);
            if (pos != null && pos.card().instanceId().startsWith(idPrefix)) {
                return pos;
            }
        }
        return null;
    }

    private void advancePhase() {
        if (game == null) {
            System.out.println("游戏未初始化。");
            return;
        }

        try {
            game.advancePhase();
            System.out.println("✓ 进入阶段: " + game.phase());
        } catch (Exception e) {
            System.out.println("✗ 切换阶段失败: " + e.getMessage());
        }
    }

    private void endTurn() {
        if (game == null) {
            System.out.println("游戏未初始化。");
            return;
        }

        try {
            String oldPlayer = game.activePlayerId();
            game.endTurn();
            System.out.println("✓ 回合结束");
            System.out.println("  当前玩家: " + game.activePlayerId() + " (之前: " + oldPlayer + ")");
            System.out.println("  当前阶段: " + game.phase());
        } catch (Exception e) {
            System.out.println("✗ 结束回合失败: " + e.getMessage());
        }
    }
}
