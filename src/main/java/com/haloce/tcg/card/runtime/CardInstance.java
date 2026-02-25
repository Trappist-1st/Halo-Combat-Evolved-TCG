package com.haloce.tcg.card.runtime;

import com.haloce.tcg.card.model.CardDef;

public record CardInstance(
        String instanceId,
        CardDef definition,
        String ownerPlayerId,
        long sourceEventSequence,
        String sourceCardId
) {
}
