package com.haloce.tcg.combat;

public record Modifier(
        String id,
        int delta,
        ModifierType type,
        int expiresAtTurn,
        String source
) {
}
