package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.model.CardDef;
import com.haloce.tcg.card.runtime.CardInstance;

/**
 * Represents a live entity in the game, distinguishing between
 * deck-based cards and dynamically spawned tokens.
 */
public record CardEntity(
    String instanceId,
    CardDef definition,
    String ownerPlayerId,
    boolean isToken,
    String sourceInstanceId, // The parent entity (e.g. Factory Ship, Tactical Card) that created this
    long createdAtTurn
) {
    public static CardEntity fromInstance(CardInstance instance, boolean isToken, String sourceId, long turn) {
        return new CardEntity(
            instance.instanceId(),
            instance.definition(),
            instance.ownerPlayerId(),
            isToken,
            sourceId,
            turn
        );
    }
}
