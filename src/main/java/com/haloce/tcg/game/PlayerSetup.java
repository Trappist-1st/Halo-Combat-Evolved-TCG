package com.haloce.tcg.game;

import com.haloce.tcg.deck.model.DeckDef;

public record PlayerSetup(
        String playerId,
        DeckDef deck
) {
}
