package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.game.Lane;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages UNSC Drop Pod logic, simulating valid hangar queues and delayed deployment.
 */
public class HangarManager {
    // Key: Owner Vessel ID -> List of units waiting to drop
    private final Map<String, List<DropQueueItem>> dropQueues = new HashMap<>();

    record DropQueueItem(CardInstance unit, int pendingTurns) {}

    public void loadUnit(CardInstance vessel, CardInstance unit) {
        dropQueues.computeIfAbsent(vessel.instanceId(), k -> new ArrayList<>())
                .add(new DropQueueItem(unit, 1)); // Default 1 turn delay
    }

    public List<CardInstance> processTurn(String vesselId) {
        List<CardInstance> readyToDrop = new ArrayList<>();
        List<DropQueueItem> remainingQueue = new ArrayList<>();

        if (dropQueues.containsKey(vesselId)) {
            for (DropQueueItem item : dropQueues.get(vesselId)) {
                if (item.pendingTurns <= 0) {
                    readyToDrop.add(item.unit);
                } else {
                    remainingQueue.add(new DropQueueItem(item.unit, item.pendingTurns - 1));
                }
            }
            dropQueues.put(vesselId, remainingQueue);
        }
        return readyToDrop;
    }

    /**
     * Applies impact damage logic when units land.
     */
    public void applyImpactDamage(Lane lane, CardInstance droppingUnit, PopulationManager popManager) {
        // Impact logic: Deal 1 damage to enemy units in landing zone
        // This would interact with GameEngine damage system
    }
}
