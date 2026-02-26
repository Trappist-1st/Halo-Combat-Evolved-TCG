package com.haloce.tcg.game.campaign;

import java.util.Random;

public class CovenantWeaponManager {
    private final Random random;

    public CovenantWeaponManager(Random random) {
        this.random = random;
    }

    public CovenantWeaponManager() {
        this(new Random());
    }

    public int overwhelmingFirepower(int baseDamage) {
        return (int) Math.ceil(baseDamage * 1.25);
    }

    public int applyAblativeHeatArmorReduction(int currentArmor) {
        return Math.max(0, currentArmor - 1);
    }

    public boolean rollOverheat(double overheatChance, boolean suppressOverheat) {
        if (suppressOverheat) {
            return false;
        }
        double clamped = Math.max(0.0, Math.min(1.0, overheatChance));
        return random.nextDouble() < clamped;
    }
}
