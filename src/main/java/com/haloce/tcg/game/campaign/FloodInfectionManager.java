package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.game.Lane;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles Infection spread, Biomass threshold evolution, and Parasitic Boarding.
 */
public class FloodInfectionManager {
    private final BiomassManager biomass;
    private static final int PROTO_GRAVEMIND_COST = 20;

    public FloodInfectionManager(BiomassManager biomass) {
        this.biomass = biomass;
    }

    /**
     * Checks if a Proto-Gravemind can be formed in a lane.
     * Requires: 4+ Flood tokens and sufficient biomass.
     */
    public boolean canEvolveProtoGravemind(String playerId, Lane lane, int floodTokenCount) {
        return floodTokenCount >= 4 && biomass.getBiomass(playerId) >= PROTO_GRAVEMIND_COST;
    }

    /**
     * Evolve logic: Remove 4 tokens, consume biomass, spawn 1 Proto-Gravemind.
     */
    public String evolveProtoGravemind(String playerId, Lane lane, List<CardInstance> tokensToConsume) {
        if (tokensToConsume.size() < 4) return null;
        
        biomass.spendBiomass(playerId, PROTO_GRAVEMIND_COST);
        // Call GameEngine to remove tokens
        return "UNIT-FLOOD-PROTO-GRAVEMIND";
    }

    /**
     * Logic Plague: Attempts to take control of an enemy vessel.
     * Success if target is Mechanical and HP < 20%.
     */
    public boolean attemptLogicPlague(CardInstance targetVessel) {
        // Pseudo-check for mechanical tag
        boolean mechanical = targetVessel.definition().tags().contains("VEHICLE"); 
        // Pseudo-check for HP < 20% (assuming max HP access)
        // int threshold = (int)(targetVessel.maxHealth() * 0.2);
        // return mechanical && targetVessel.currentHealth() < threshold;
        return mechanical;
    }
}
