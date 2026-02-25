package com.haloce.tcg.combat;

public record DamageContext(
        String attackerId,
        String defenderId,
        int baseDamage,
        DamageType damageType,
        boolean orbitalStrike,
        boolean ordnance,
        int finalDamageMultiplier,
        boolean ignoreShield
) {
}
