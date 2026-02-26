package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;

/**
 * Manages the production queue for "Factory" units (e.g. Carrier Ships).
 */
public class FactoryLogic {
    private final CardInstance parentVessel;
    private final String spawnTokenId;
    private final int spawnInterval;
    private int cooldown;

    public FactoryLogic(CardInstance vessel, String spawnTokenId, int interval) {
        this.parentVessel = vessel;
        this.spawnTokenId = spawnTokenId;
        this.spawnInterval = interval;
        this.cooldown = interval;
    }

    public void onTurnStart() {
        if (cooldown > 0) {
            cooldown--;
        }
    }

    public boolean isReady() {
        return cooldown <= 0;
    }

    public String produce() {
        if (isReady()) {
            cooldown = spawnInterval; // Reset cooldown
            return spawnTokenId;
        }
        return null;
    }

    public int cooldown() { return cooldown; }
}
