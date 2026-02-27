package com.haloce.tcg;

import com.haloce.tcg.card.loader.CardLoader;
import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.loader.SemanticValidator;
import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.deck.DeckLoader;
import com.haloce.tcg.deck.DeckValidator;
import com.haloce.tcg.deck.model.DeckDef;
import com.haloce.tcg.game.GameEngine;
import com.haloce.tcg.game.GameMode;
import com.haloce.tcg.game.GamePhase;
import com.haloce.tcg.game.GameStatus;
import com.haloce.tcg.game.GameStateManager;
import com.haloce.tcg.game.Lane;
import com.haloce.tcg.game.PlayerSeat;
import com.haloce.tcg.game.GameRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * In-process gameplay tests (no network). Run with: mvn test
 */
@DisplayName("Gameplay (in-process)")
class GamePlayTest {

    private CardRepository repository;
    private DeckDef deckDef;
    private GameEngine engine;

    @BeforeEach
    void setUp() {
        Path resources = Path.of("src/main/resources");
        CardLoader cardLoader = new CardLoader(new SemanticValidator());
        repository = cardLoader.loadFromResourceDir(resources);
        DeckLoader deckLoader = new DeckLoader();
        deckDef = deckLoader.load(resources.resolve("decks/p1_demo_deck.v1.json"));
        new DeckValidator().validate(deckDef, repository);
        engine = new GameEngine(repository);
    }

    @Test
    @DisplayName("game starts in DEPLOYMENT (P1), both players have 5 cards")
    void gameStartsCorrectly() {
        GameStateManager game = engine.initializeMatchWithSeats(
                GameMode.DUEL_1V1,
                List.of(
                        new PlayerSeat("P1", deckDef, "P1", false),
                        new PlayerSeat("P2", deckDef, "P2", false)
                )
        );

        assertEquals(GameStatus.RUNNING, game.status());
        assertEquals(GamePhase.DEPLOYMENT, game.phase());
        assertEquals(5, game.player("P1").handSize());
        assertEquals(5, game.player("P2").handSize());
        assertEquals("P1", game.activePlayerId());
    }

    @Test
    @DisplayName("deploy a unit, advance to SKIRMISH, end turn")
    void fullTurnFlow() {
        GameStateManager game = engine.initializeMatchWithSeats(
                GameMode.DUEL_1V1,
                List.of(
                        new PlayerSeat("P1", deckDef, "P1", false),
                        new PlayerSeat("P2", deckDef, "P2", false)
                )
        );

        assertEquals(GamePhase.DEPLOYMENT, game.phase());

        List<CardInstance> hand = game.player("P1").hand();
        CardInstance toDeploy = hand.stream()
                .filter(c -> c.definition().stats() != null)
                .findFirst()
                .orElse(hand.get(0));

        game.deployUnitFromHand("P1", toDeploy.instanceId(), Lane.ALPHA, GameRow.FRONTLINE);
        game.advancePhase();
        assertEquals(GamePhase.SKIRMISH, game.phase());

        game.endTurn();
        assertEquals("P2", game.activePlayerId());
        assertEquals(GamePhase.DRAW_RECHARGE, game.phase());
    }

    @Test
    @DisplayName("victory by elimination: reduce opponent base to 0 then end turn")
    void victoryByElimination() {
        GameStateManager game = engine.initializeMatchWithSeats(
                GameMode.DUEL_1V1,
                List.of(
                        new PlayerSeat("P1", deckDef, "P1", false),
                        new PlayerSeat("P2", deckDef, "P2", false)
                )
        );

        game.player("P2").applyBaseDamage(30);
        game.advancePhase(); // DEPLOYMENT -> SKIRMISH
        game.advancePhase(); // SKIRMISH -> ENDSTEP
        game.endTurn();     // evaluateWinConditions(P1) -> P2 eliminated -> P1 wins

        assertEquals(GameStatus.FINISHED, game.status());
        assertNotNull(game.winnerPlayerId());
        assertEquals("P1", game.winnerPlayerId());
    }
}
