package com.haloce.tcg.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModifierStack {
    private final List<Modifier> modifiers = new ArrayList<>();

    public void add(Modifier modifier) {
        modifiers.add(modifier);
    }

    public void removeExpired(int currentTurn) {
        modifiers.removeIf(modifier -> modifier.type() == ModifierType.TEMPORARY
                && modifier.expiresAtTurn() <= currentTurn);
    }

    public int calculateTotalDelta() {
        return modifiers.stream().mapToInt(Modifier::delta).sum();
    }

    public List<Modifier> all() {
        return Collections.unmodifiableList(modifiers);
    }
}
