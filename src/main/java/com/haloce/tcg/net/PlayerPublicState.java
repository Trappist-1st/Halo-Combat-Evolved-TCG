package com.haloce.tcg.net;

public record PlayerPublicState(
        String playerId,
        String teamId,
        boolean alive,
        int baseHealth,
        int supplyCap,
        int currentSupply,
        int battery,
        int handSize,
        int librarySize,
        int discardSize,
        int controlledLaneCount,
        int fullControlStreak
) {
}
