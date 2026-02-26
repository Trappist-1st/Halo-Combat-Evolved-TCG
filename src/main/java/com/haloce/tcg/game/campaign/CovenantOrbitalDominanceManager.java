package com.haloce.tcg.game.campaign;

import com.haloce.tcg.game.Lane;

import java.util.HashMap;
import java.util.Map;

public class CovenantOrbitalDominanceManager {
    private final Map<String, Map<Lane, Integer>> glassingMarksByEnemy = new HashMap<>();
    private final Map<String, Integer> boardingTokensByShip = new HashMap<>();

    public void applyGlassingMark(String enemyPlayerId, Lane lane) {
        glassingMarksByEnemy
                .computeIfAbsent(enemyPlayerId, k -> new HashMap<>())
                .merge(lane, 1, Integer::sum);
    }

    public int onEnemyUnitSpawned(String enemyPlayerId, Lane lane) {
        int marks = glassingMarksByEnemy
                .getOrDefault(enemyPlayerId, Map.of())
                .getOrDefault(lane, 0);
        return marks > 0 ? 1 : 0;
    }

    public int consumeGlassingMark(String enemyPlayerId, Lane lane) {
        Map<Lane, Integer> byLane = glassingMarksByEnemy.get(enemyPlayerId);
        if (byLane == null) {
            return 0;
        }
        int current = byLane.getOrDefault(lane, 0);
        if (current <= 0) {
            return 0;
        }
        byLane.put(lane, current - 1);
        return 1;
    }

    public void boardingCraftHit(String targetShipInstanceId) {
        boardingTokensByShip.merge(targetShipInstanceId, 2, Integer::sum);
    }

    public void clearBoardingToken(String targetShipInstanceId) {
        int current = boardingTokensByShip.getOrDefault(targetShipInstanceId, 0);
        if (current <= 1) {
            boardingTokensByShip.remove(targetShipInstanceId);
            return;
        }
        boardingTokensByShip.put(targetShipInstanceId, current - 1);
    }

    public boolean isShipDisabled(String targetShipInstanceId) {
        return boardingTokensByShip.getOrDefault(targetShipInstanceId, 0) > 0;
    }
}
