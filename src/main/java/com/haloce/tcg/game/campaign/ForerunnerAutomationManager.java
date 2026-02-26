package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.game.Lane;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles Forerunner automated unit construction and death-rattle spawns.
 */
public class ForerunnerAutomationManager {

    /**
     * Builder Pulse: Passive action that spawns Sentinel if slots available.
     * Check: Unit Cap not exceeded.
     */
    public String attemptSentinelSpawn(PopulationManager popManager, Lane lane, String sentinelTokenId, int popValue) {
        if (popManager.canAddUnit(popValue, lane, false)) { // Assume Sentinel is ground unit? 
            // Or maybe Orbit/Air? Need flag in call.
            return sentinelTokenId;
        }
        return null;
    }

    /**
     * Sub-unit System: On Destruction of parent, spawn child units.
     * e.g. War Sphinx -> 2 Attack Drones
     */
    public List<String> spawnSubUnitsOnDeath(String parentUnitId, String childTokenId, int count) {
        List<String> children = new ArrayList<>();
        // In actual logic, check specific unit ID map
        if ("UNIT-FOR-WAR-SPHINX".equals(parentUnitId)) {
            for (int i=0; i<count; i++) {
                children.add(childTokenId);
            }
        }
        return children;
    };
}
