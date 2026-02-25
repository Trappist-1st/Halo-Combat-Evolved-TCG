package com.haloce.tcg.game;

import com.haloce.tcg.deck.model.DeckDef;

public record PlayerSeat(
        String playerId,
        DeckDef deck,
        String teamId,
        boolean remote
) {
    public PlayerSeat {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId is required");
        }
        if (deck == null) {
            throw new IllegalArgumentException("deck is required");
        }
    }
}
