package com.haloce.tcg.test;

import com.haloce.tcg.card.loader.CardLoader;
import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.loader.SemanticValidator;
import com.haloce.tcg.deck.DeckLoader;
import com.haloce.tcg.deck.DeckValidator;
import com.haloce.tcg.deck.model.DeckDef;
import com.haloce.tcg.game.GameEngine;
import com.haloce.tcg.game.GameMode;
import com.haloce.tcg.game.GameStateManager;
import com.haloce.tcg.game.PlayerSeat;

import java.nio.file.Path;
import java.util.List;

/**
 * 简单的游戏玩法测试类
 * 演示基本游戏流程：部署、攻击、触发效果等
 */
public class GamePlayTest {
    public static void main(String[] args) {
        System.out.println("=== Halo CE TCG 玩法测试 ===");

        // 1. 加载卡牌和卡组
        CardLoader cardLoader = new CardLoader(new SemanticValidator());
        CardRepository repository = cardLoader.loadFromResourceDir(Path.of("src/main/resources"));

        DeckLoader deckLoader = new DeckLoader();
        DeckDef deckDef = deckLoader.load(Path.of("src/main/resources/decks/p1_demo_deck.v1.json"));
        new DeckValidator().validate(deckDef, repository);

        System.out.println("✓ 加载了 " + repository.size() + " 张卡牌");
        System.out.println("✓ 验证了卡组: " + deckDef.deckId());

        // 2. 创建游戏引擎
        GameEngine engine = new GameEngine(repository);

        // 3. 初始化对战游戏
        List<PlayerSeat> seats = List.of(
            new PlayerSeat("P1", deckDef, "P1", false),
            new PlayerSeat("P2", deckDef, "P2", false)
        );

        GameStateManager game = engine.initializeMatchWithSeats(GameMode.DUEL_1V1, seats);
        System.out.println("✓ 创建了 DUEL_1V1 游戏，玩家: " + game.playerIds());

        // 4. 启动游戏
        game.startGame();
        System.out.println("✓ 游戏已启动，当前阶段: " + game.phase());
        System.out.println("✓ 主动玩家: " + game.activePlayerId());

        // 5. 演示基本操作
        try {
            // 抽牌阶段自动处理
            System.out.println("\n--- 抽牌与充能阶段 ---");
            System.out.println("P1 手牌数: " + game.player("P1").handSize());
            System.out.println("P2 手牌数: " + game.player("P2").handSize());

            // 部署阶段：部署一个单位
            System.out.println("\n--- 部署阶段 ---");
            String p1HandCard = game.player("P1").hand().get(0).instanceId();
            game.deployUnitFromHand("P1", p1HandCard, com.haloce.tcg.game.Lane.ALPHA, com.haloce.tcg.game.GameRow.FRONTLINE);
            System.out.println("✓ P1 部署了一个单位到 ALPHA 前排");

            // 推进到交战阶段
            game.advancePhase();
            System.out.println("✓ 推进到交战阶段");

            // 演示攻击（如果有单位的话）
            if (!game.battlefield().unitsOfPlayer("P1").isEmpty() &&
                !game.battlefield().unitsOfPlayer("P2").isEmpty()) {
                String attackerId = game.battlefield().unitsOfPlayer("P1").get(0).instanceId();
                String defenderId = game.battlefield().unitsOfPlayer("P2").get(0).instanceId();

                var result = game.declareAttack(attackerId, defenderId);
                System.out.println("✓ 攻击结果: 伤害=" + result.finalDamage() + ", 护盾=" + result.shieldDamage() + ", 生命=" + result.healthDamage() + ", 致命=" + result.lethal());
            }

            // 结束回合
            game.endTurn();
            System.out.println("✓ P1 回合结束，切换到 P2");

            System.out.println("\n=== 测试完成 ===");
            System.out.println("游戏状态: " + game.status());
            System.out.println("当前回合: " + game.globalTurnIndex());
            System.out.println("当前阶段: " + game.phase());

        } catch (Exception e) {
            System.out.println("测试中遇到错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}