package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.game.Lane;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UNSCDropPodManager {
    private final Map<String, Integer> commandPointByPlayer = new HashMap<>();
    private final Map<String, Lane> activeSupplyCrateByInstance = new HashMap<>();
    private final Map<String, List<DropPodRecord>> dropPodsByPlayer = new HashMap<>();
    
    private static class DropPodRecord {
        @SuppressWarnings("unused")
        String instanceId;
        @SuppressWarnings("unused")
        Lane lane;
        
        DropPodRecord(String instanceId, Lane lane) {
            this.instanceId = instanceId;
            this.lane = lane;
        }
    }

    public void grantCommandPoint(String playerId, int amount) {
        if (playerId == null || amount <= 0) {
            return;
        }
        commandPointByPlayer.merge(playerId, amount, Integer::sum);
    }

    public int commandPoints(String playerId) {
        return commandPointByPlayer.getOrDefault(playerId, 0);
    }

    public boolean tryInstantOdstDrop(String playerId, int cpCost) {
        int cp = commandPoints(playerId);
        if (cpCost <= 0 || cp < cpCost) {
            return false;
        }
        commandPointByPlayer.put(playerId, cp - cpCost);
        return true;
    }

    public int dropShockwaveStun(List<CardInstance> adjacentEnemies) {
        if (adjacentEnemies == null) {
            return 0;
        }
        return adjacentEnemies.size();
    }

    public String deploySupplyCrate(String ownerPlayerId, Lane lane) {
        String crateId = "SUPPLY-CRATE-" + UUID.randomUUID();
        activeSupplyCrateByInstance.put(crateId, lane);
        return crateId;
    }

    public boolean hasSupplyCrateInLane(Lane lane) {
        return activeSupplyCrateByInstance.values().stream().anyMatch(value -> value == lane);
    }

    public List<String> suppressBySupplyCrate(Lane lane, List<CardInstance> enemyUnitsInLane) {
        if (!hasSupplyCrateInLane(lane) || enemyUnitsInLane == null || enemyUnitsInLane.isEmpty()) {
            return List.of();
        }
        List<String> suppressed = new ArrayList<>();
        for (CardInstance unit : enemyUnitsInLane) {
            suppressed.add(unit.instanceId());
        }
        return suppressed;
    }

    public void recordDropPodDeployment(String playerId, String instanceId, Lane lane) {
        dropPodsByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>())
            .add(new DropPodRecord(instanceId, lane));
    }

    public void processHangarQueue(String playerId, int globalTurnIndex) {
        // Placeholder for future implementation
    }
}
