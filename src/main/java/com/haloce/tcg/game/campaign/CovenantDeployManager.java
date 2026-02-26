package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.game.Lane;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles Covenant logic for Mass Deploy (fill slots) and Suicide Attacks.
 */
public class CovenantDeployManager {
    private static final int SUICIDE_BLAST_DAMAGE = 3;
    private static final int GROUND_POP_VALUE = 1;
    
    /**
     * Executes 'Mass Deploy' ability: Fills all empty slots with specific unit.
     */
    public List<String> massDeploy(
            PopulationManager popManager,
            Lane lane,
            String gruntTokenId,
            int availableFaith,
            int faithCostPerToken
    ) {
        if (faithCostPerToken <= 0 || availableFaith <= 0) {
            return List.of();
        }

        List<String> deployedBatch = new ArrayList<>();
        int slots = popManager.remainingSlots(lane, LaneLayer.GROUND);
        int budgetByFaith = availableFaith / faithCostPerToken;
        int budgetByPop = Math.max(0, popManager.maxPop() - popManager.currentPop());
        int toSpawn = Math.min(slots, Math.min(budgetByFaith, budgetByPop));

        for (int i = 0; i < toSpawn; i++) {
            deployedBatch.add(gruntTokenId);
            popManager.addUnit(GROUND_POP_VALUE, lane, false);
        }
        return deployedBatch;
    }

    /**
     * Handles 'Suicide Grunt' mechanics.
     */
    public Map<String, Integer> sacrificeUnit(CardInstance unit, List<CardInstance> adjacentTargets) {
        if (unit == null || adjacentTargets == null || adjacentTargets.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> damageByTargetInstance = new LinkedHashMap<>();
        for (CardInstance target : adjacentTargets) {
            damageByTargetInstance.put(target.instanceId(), SUICIDE_BLAST_DAMAGE);
        }
        return damageByTargetInstance;
    }
}
