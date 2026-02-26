package com.haloce.tcg.game.campaign;

import java.util.HashMap;
import java.util.Map;

public class CovenantFaithManager {
    private static final int FAITH_PER_KILL = 2;
    private static final int FAITH_BURST_COST = 10;

    private final Map<String, Integer> faithByPlayer = new HashMap<>();
    private final Map<String, Integer> antiOverheatTurnByPlayer = new HashMap<>();

    public void onEnemyKilled(String playerId) {
        faithByPlayer.merge(playerId, FAITH_PER_KILL, Integer::sum);
    }

    public void onUnitLostOrRetreated(String playerId, int penalty) {
        int current = faithByPlayer.getOrDefault(playerId, 0);
        faithByPlayer.put(playerId, Math.max(0, current - Math.max(0, penalty)));
    }

    public int faith(String playerId) {
        return faithByPlayer.getOrDefault(playerId, 0);
    }

    public boolean activateRitualOverdrive(String playerId, int globalTurnIndex) {
        int current = faith(playerId);
        if (current < FAITH_BURST_COST) {
            return false;
        }
        faithByPlayer.put(playerId, current - FAITH_BURST_COST);
        antiOverheatTurnByPlayer.put(playerId, globalTurnIndex);
        return true;
    }

    public boolean isOverheatSuppressedThisTurn(String playerId, int globalTurnIndex) {
        return antiOverheatTurnByPlayer.getOrDefault(playerId, -1) == globalTurnIndex;
    }
}
