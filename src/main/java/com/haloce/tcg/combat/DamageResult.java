package com.haloce.tcg.combat;

public record DamageResult(
        int finalDamage,
        int shieldDamage,
        int healthDamage,
        boolean lethal
) {
}
