package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.game.Lane;

import java.util.List;
import java.util.Random;

/**
 * Handles Ship-to-Ship combat and Blockade mechanics.
 */
public class OrbitalManager {
    private final Random random = new Random();

    /**
     * Checks if a drop pod token is intercepted by enemy orbital presence.
     * Blockade Logic: If there's an ACTIVE enemy ship in the same lane orbit, 50% chance to intercept.
     */
    public boolean checkInterception(Lane lane, String droppingPlayerId, List<CardInstance> enemyShipsInOrbit) {
        boolean isBlockaded = enemyShipsInOrbit.stream()
            .anyMatch(ship -> isValidBlockader(ship));

        if (isBlockaded) {
            // 50% chance to fail drop
            return random.nextBoolean();
        }
        return false; // No blockade, safe drop
    }

    private boolean isValidBlockader(CardInstance ship) {
        // In a real game, check if ship is stunned/EMP?
        // Check if ship is Capital Class?
        return true; 
    }
}
