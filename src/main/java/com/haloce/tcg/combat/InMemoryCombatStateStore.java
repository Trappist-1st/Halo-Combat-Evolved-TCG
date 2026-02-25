package com.haloce.tcg.combat;

import java.util.HashMap;
import java.util.Map;

public class InMemoryCombatStateStore implements CombatStateStore {
    private final Map<String, EntityCombatState> states = new HashMap<>();

    public void put(String entityId, EntityCombatState state) {
        states.put(entityId, state);
    }

    @Override
    public EntityCombatState get(String entityId) {
        EntityCombatState state = states.get(entityId);
        if (state == null) {
            throw new IllegalArgumentException("Missing combat state for entity: " + entityId);
        }
        return state;
    }

    @Override
    public void remove(String entityId) {
        states.remove(entityId);
    }
}
