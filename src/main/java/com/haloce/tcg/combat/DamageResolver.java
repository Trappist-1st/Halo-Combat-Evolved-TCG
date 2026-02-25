package com.haloce.tcg.combat;

import com.haloce.tcg.core.event.EventBus;
import com.haloce.tcg.core.event.EventType;
import com.haloce.tcg.core.event.GameEvent;

import java.util.HashMap;
import java.util.Map;

public class DamageResolver {
    private final EventBus eventBus;
    private final CombatStateStore stateStore;

    public DamageResolver(EventBus eventBus, CombatStateStore stateStore) {
        this.eventBus = eventBus;
        this.stateStore = stateStore;
    }

    public DamageResult resolve(DamageContext context, int globalTurnIndex, int roundIndex, String activePlayerId) {
        publish(EventType.DAMAGE_CALC_STARTED, context, globalTurnIndex, roundIndex, activePlayerId, Map.of("damage", context.baseDamage()));

        int damage = applyTypeModifiers(context.baseDamage(), context.damageType());
        damage = applyStatusAndCoverModifiers(damage, context);
        damage = Math.max(0, damage * Math.max(1, context.finalDamageMultiplier()));

        publish(EventType.DAMAGE_MODIFIED, context, globalTurnIndex, roundIndex, activePlayerId, Map.of("damage", damage));

        ShieldDamageResult shieldDamageResult = context.ignoreShield()
            ? new ShieldDamageResult(0, damage)
            : applyShieldDamage(context.defenderId(), damage);
        publish(EventType.SHIELD_DAMAGED, context, globalTurnIndex, roundIndex, activePlayerId,
                Map.of("shieldDamage", shieldDamageResult.shieldDamage(), "overflow", shieldDamageResult.overflowDamage()));

        HealthDamageResult healthDamageResult = applyHealthDamage(context.defenderId(), shieldDamageResult.overflowDamage());
        publish(EventType.HULL_OR_HEALTH_DAMAGED, context, globalTurnIndex, roundIndex, activePlayerId,
                Map.of("healthDamage", healthDamageResult.healthDamage(), "lethal", healthDamageResult.lethal()));

        publish(EventType.DAMAGE_DEALT, context, globalTurnIndex, roundIndex, activePlayerId,
                Map.of("finalDamage", damage));

        if (healthDamageResult.lethal()) {
            publish(EventType.KILL_OCCURRED, context, globalTurnIndex, roundIndex, activePlayerId, Map.of());
        }

        return new DamageResult(damage, shieldDamageResult.shieldDamage(), healthDamageResult.healthDamage(), healthDamageResult.lethal());
    }

    private int applyTypeModifiers(int damage, DamageType type) {
        return switch (type) {
            case BALLISTIC -> Math.max(0, damage);
            case PLASMA -> Math.max(1, damage);
            case TRUE -> Math.max(0, damage);
        };
    }

    private int applyStatusAndCoverModifiers(int damage, DamageContext context) {
        EntityCombatState defender = stateStore.get(context.defenderId());
        int modified = damage;

        if (defender.marked()) {
            modified += 1;
        }

        if (defender.suppressed()) {
            modified = Math.max(0, modified - 1);
        }

        if (!context.ordnance()) {
            modified = Math.max(0, modified - defender.coverValue());
        }

        if (context.orbitalStrike() && Boolean.TRUE.equals(defender.marked())) {
            modified = (int) Math.floor(modified * 1.5);
        }

        return Math.max(0, modified);
    }

    private ShieldDamageResult applyShieldDamage(String defenderId, int damage) {
        EntityCombatState defender = stateStore.get(defenderId);
        int beforeShield = defender.currentShield();
        int overflow = defender.applyShieldDamage(damage);
        int absorbed = beforeShield - defender.currentShield();
        return new ShieldDamageResult(absorbed, overflow);
    }

    private HealthDamageResult applyHealthDamage(String defenderId, int overflow) {
        EntityCombatState defender = stateStore.get(defenderId);
        int dealt = defender.applyHealthDamage(overflow);
        boolean lethal = defender.currentHealth() <= 0;
        return new HealthDamageResult(dealt, lethal);
    }

    private void publish(EventType eventType,
                         DamageContext context,
                         int globalTurnIndex,
                         int roundIndex,
                         String activePlayerId,
                         Map<String, Object> payload) {
        Map<String, Object> merged = new HashMap<>(payload);
        merged.put("damageType", context.damageType().name());
        merged.put("orbitalStrike", context.orbitalStrike());
        merged.put("ordnance", context.ordnance());
        merged.put("finalDamageMultiplier", context.finalDamageMultiplier());
        merged.put("ignoreShield", context.ignoreShield());

        eventBus.publish(new GameEvent(
                System.nanoTime(),
                eventType,
                globalTurnIndex,
                roundIndex,
                activePlayerId,
                context.attackerId(),
                null,
                context.attackerId(),
                context.defenderId(),
                null,
                context.orbitalStrike() ? "ORBIT" : "SURFACE",
                merged
        ));
    }
}
