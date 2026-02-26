package com.haloce.tcg.game.campaign;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the Flood's Biomass resource pool, allowing for evolution and infection mechanics.
 */
public class BiomassManager {
    private final Map<String, Integer> playerBiomass = new HashMap<>();

    public void addBiomass(String playerId, int amount) {
        playerBiomass.merge(playerId, amount, Integer::sum);
    }

    public boolean spendBiomass(String playerId, int amount) {
        int current = playerBiomass.getOrDefault(playerId, 0);
        if (current >= amount) {
            playerBiomass.put(playerId, current - amount);
            return true;
        }
        return false;
    }

    public int getBiomass(String playerId) {
        return playerBiomass.getOrDefault(playerId, 0);
    }

    /**
     * Harvests biomass from a dead unit based on its population value.
     */
    public void harvest(String playerId, int unitPopValue) {
        addBiomass(playerId, unitPopValue);
    }
}
