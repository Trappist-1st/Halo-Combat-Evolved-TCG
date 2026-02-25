package com.haloce.tcg.game;

import com.haloce.tcg.card.runtime.CardInstance;

public record UnitPosition(
        String playerId,
        Lane lane,
        GameRow row,
        CardInstance card
) {
}
