package com.haloce.tcg.combat;

public interface CombatStateStore {
    EntityCombatState get(String entityId);

    void remove(String entityId);
}
