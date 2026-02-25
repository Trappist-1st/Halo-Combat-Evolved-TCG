package com.haloce.tcg.core.event;

import java.util.Map;

public record GameEvent(
        long sequence,
        EventType type,
        int globalTurnIndex,
        int roundIndex,
        String activePlayerId,
        String sourcePlayerId,
        String targetPlayerId,
        String sourceEntityId,
        String targetEntityId,
        String lane,
        String layer,
        Map<String, Object> payload
) {
}
