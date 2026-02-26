package com.haloce.tcg.game.handlers;

import com.haloce.tcg.card.model.CardType;
import com.haloce.tcg.card.model.KeywordInstance;
import com.haloce.tcg.card.model.Stats;
import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.combat.EntityCombatState;
import com.haloce.tcg.combat.InMemoryCombatStateStore;
import com.haloce.tcg.game.BattlefieldState;
import com.haloce.tcg.game.Lane;
import com.haloce.tcg.game.UnitPosition;

import java.util.List;
import java.util.Optional;

/**
 * Shared utility methods for handlers
 */
public class HandlerUtils {

    public static boolean hasKeyword(CardInstance card, String keyword) {
        if (card.definition().keywords() == null) {
            return false;
        }
        return card.definition().keywords().stream()
                .map(KeywordInstance::name)
                .anyMatch(keyword::equalsIgnoreCase);
    }

    public static boolean hasTag(CardInstance card, String tag) {
        if (card.definition().tags() == null) {
            return false;
        }
        return card.definition().tags().stream().anyMatch(tag::equalsIgnoreCase);
    }

    public static boolean isVehicle(CardInstance card) {
        return card.definition().cardType() == CardType.VESSEL
                || hasKeyword(card, "VEHICLE")
                || hasTag(card, "VEHICLE")
                || hasTag(card, "VESSEL");
    }

    public static boolean isInfantry(CardInstance card) {
        return card.definition().cardType() == CardType.UNIT
                && !isVehicle(card)
                && (hasTag(card, "INFANTRY") || !hasTag(card, "VEHICLE"));
    }

    public static int attackValue(CardInstance card) {
        Stats stats = card.definition().stats();
        if (stats == null) {
            return 0;
        }
        return stats.attack();
    }

    public static Optional<UnitPosition> findMostDamagedAlly(
            String ownerPlayerId,
            Lane preferredLane,
            String excludeInstanceId,
            BattlefieldState battlefield,
            InMemoryCombatStateStore combatStateStore
    ) {
        UnitPosition selected = null;
        int maxMissing = 0;

        List<Lane> laneOrder = List.of(preferredLane, Lane.ALPHA, Lane.BRAVO, Lane.CHARLIE);
        for (Lane lane : laneOrder) {
            List<CardInstance> allies = battlefield.lane(lane).unitsOf(ownerPlayerId);
            for (CardInstance ally : allies) {
                if (ally.instanceId().equals(excludeInstanceId)) {
                    continue;
                }
                Stats stats = ally.definition().stats();
                if (stats == null || stats.healthCap() <= 0) {
                    continue;
                }
                EntityCombatState state = combatStateStore.get(ally.instanceId());
                int missing = stats.healthCap() - state.currentHealth();
                if (missing > maxMissing) {
                    maxMissing = missing;
                    selected = new UnitPosition(ownerPlayerId, lane, battlefield.lane(lane).locate(ally.instanceId(), lane).row(), ally);
                }
            }
            if (selected != null) {
                break;
            }
        }
        return Optional.ofNullable(selected);
    }
}
