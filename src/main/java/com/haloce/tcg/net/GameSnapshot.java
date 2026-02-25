package com.haloce.tcg.net;

import java.util.List;

public record GameSnapshot(
        String mode,
        String status,
        String phase,
        int roundIndex,
        int globalTurnIndex,
        String activePlayerId,
        String winnerPlayerId,
        String winnerTeamId,
        List<PlayerPublicState> players,
        List<LanePublicState> lanes
) {
}
