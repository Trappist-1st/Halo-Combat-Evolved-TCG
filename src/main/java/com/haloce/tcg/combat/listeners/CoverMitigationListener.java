package com.haloce.tcg.combat.listeners;

import com.haloce.tcg.combat.CombatStateStore;
import com.haloce.tcg.combat.EntityCombatState;
import com.haloce.tcg.core.event.EventContext;
import com.haloce.tcg.core.event.EventListener;
import com.haloce.tcg.core.event.EventType;
import com.haloce.tcg.core.event.GameEvent;

import java.util.Map;

public class CoverMitigationListener implements EventListener {
    private final CombatStateStore stateStore;

    public CoverMitigationListener(CombatStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public EventType supports() {
        return EventType.DAMAGE_MODIFIED;
    }

    @Override
    public int priority() {
        return 250;
    }

    @Override
    public void onEvent(GameEvent event, EventContext context) {
        Object ordnanceValue = event.payload().get("ordnance");
        boolean isOrdnance = ordnanceValue instanceof Boolean b && b;
        if (isOrdnance) {
            return;
        }

        String defenderId = event.targetEntityId();
        if (defenderId == null) {
            return;
        }

        EntityCombatState defender = stateStore.get(defenderId);
        int cover = Math.max(0, defender.coverValue());
        Object value = event.payload().get("damage");
        if (!(value instanceof Integer currentDamage)) {
            return;
        }

        int mitigated = Math.max(0, currentDamage - cover);
        Map<String, Object> mutableMap = (Map<String, Object>) event.payload();
        mutableMap.put("damage", mitigated);
    }
}
