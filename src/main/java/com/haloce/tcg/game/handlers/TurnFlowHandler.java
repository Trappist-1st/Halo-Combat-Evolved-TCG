package com.haloce.tcg.game.handlers;

import com.haloce.tcg.card.model.Stats;
import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.combat.EntityCombatState;
import com.haloce.tcg.combat.InMemoryCombatStateStore;
import com.haloce.tcg.core.event.EventBus;
import com.haloce.tcg.core.event.EventType;
import com.haloce.tcg.core.event.GameEvent;
import com.haloce.tcg.game.BattlefieldState;
import com.haloce.tcg.game.PlayerState;
import com.haloce.tcg.game.UnitStatus;
import com.haloce.tcg.game.UnitStatusStore;
import com.haloce.tcg.game.campaign.CampaignManager;

import java.util.List;
import java.util.Map;

/**
 * Handles turn flow, phase transitions, and turn-based triggers
 */
public class TurnFlowHandler {
    private final EventBus eventBus;
    private final BattlefieldState battlefield;
    private final InMemoryCombatStateStore combatStateStore;
    private final UnitStatusStore unitStatusStore;
    private final CampaignManager campaignManager;

    public TurnFlowHandler(
            EventBus eventBus,
            BattlefieldState battlefield,
            InMemoryCombatStateStore combatStateStore,
            UnitStatusStore unitStatusStore,
            CampaignManager campaignManager
    ) {
        this.eventBus = eventBus;
        this.battlefield = battlefield;
        this.combatStateStore = combatStateStore;
        this.unitStatusStore = unitStatusStore;
        this.campaignManager = campaignManager;
    }

    public void onTurnStart(
            String activePlayerId,
            PlayerState activePlayer,
            int globalTurnIndex,
            int roundIndex,
            long eventSequence,
            boolean skipDraw,
            int activePlayerCursor
    ) {
        refreshTurnStatuses(activePlayerId);
        rechargeShieldsAtTurnStart(activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence);

        activePlayer.startTurnResourceStep();

        emit(EventType.TURN_STARTED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "globalTurnIndex", globalTurnIndex,
                "roundIndex", roundIndex
        ));

        if (!skipDraw) {
            List<CardInstance> drawn = activePlayer.draw(1);
            if (!drawn.isEmpty()) {
                CardInstance card = drawn.get(0);
                emit(EventType.CARD_DRAWN, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                        "cardId", card.definition().id(),
                        "cardInstanceId", card.instanceId()
                ));
            }
        }

        emit(EventType.SUPPLY_CAP_INCREASED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of("supplyCap", activePlayer.supplyCap()));
        emit(EventType.SUPPLY_REFILLED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of("currentSupply", activePlayer.currentSupply()));

        // Campaign integration: Check for Spartan MIA recovery
        List<String> recoveredSpartans = campaignManager.spartanHero().checkMIARecovery(activePlayerId, globalTurnIndex);
        for (String spartanId : recoveredSpartans) {
            emit(EventType.SPARTAN_MIA_RECOVERED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                    "spartanInstanceId", spartanId
            ));
        }

        // Campaign integration: Covenant Deploy mass deploy check
        campaignManager.covenantDeploy().checkMassDeploy(activePlayerId, battlefield, globalTurnIndex);

        // Campaign integration: UNSC Hangar queue processing
        campaignManager.unscDropPod().processHangarQueue(activePlayerId, globalTurnIndex);
    }

    public void onTurnEnd(
            String endingPlayerId,
            int globalTurnIndex,
            int roundIndex,
            long eventSequence
    ) {
        resolveOnTurnEndedTriggers(endingPlayerId, globalTurnIndex, roundIndex, eventSequence);
        emit(EventType.TURN_ENDED, endingPlayerId, globalTurnIndex, roundIndex, endingPlayerId, eventSequence, Map.of("globalTurnIndex", globalTurnIndex));

        // Campaign integration: Covenant Faith ritual check
        campaignManager.covenantFaith().checkRitualOverdrive(endingPlayerId, globalTurnIndex);

        // Campaign integration: UNSC Salvage refit check
        campaignManager.unscSalvage().processRefitCooldowns(endingPlayerId, globalTurnIndex);
    }

    public void onRoundStart(int roundIndex, String activePlayerId, int globalTurnIndex, long eventSequence) {
        emit(EventType.ROUND_STARTED, activePlayerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of("roundIndex", roundIndex));

        // Campaign integration: Forerunner automation
        // Process automated Sentinel spawns if any
    }

    public void onRoundEnd(String endingPlayerId, int roundIndex, int globalTurnIndex, long eventSequence) {
        emit(EventType.ROUND_ENDED, endingPlayerId, globalTurnIndex, roundIndex, endingPlayerId, eventSequence, Map.of("roundIndex", roundIndex));

        // Campaign integration: Covenant Orbital Dominance glassing check
        campaignManager.covenantOrbital().processGlassingMarks(endingPlayerId, globalTurnIndex);
    }

    private void refreshTurnStatuses(String activePlayerId) {
        for (CardInstance unit : battlefield.unitsOfPlayer(activePlayerId)) {
            UnitStatus status = unitStatusStore.getOrCreate(unit.instanceId());
            status.setHasCamoThisTurn(HandlerUtils.hasKeyword(unit, "CAMO"));
        }
    }

    private void rechargeShieldsAtTurnStart(String activePlayerId, int globalTurnIndex, int roundIndex, String activePlayer, long eventSequence) {
        for (CardInstance unit : battlefield.unitsOfPlayer(activePlayerId)) {
            Stats stats = unit.definition().stats();
            if (stats == null || stats.shieldCap() <= 0) {
                continue;
            }

            UnitStatus status = unitStatusStore.getOrCreate(unit.instanceId());
            if (status.damagedLastOpponentTurn(activePlayerId, globalTurnIndex)) {
                continue;
            }

            EntityCombatState state = combatStateStore.get(unit.instanceId());
            int before = state.currentShield();

            // Campaign integration: UNSC morale multiplier
            double rechargeMultiplier = campaignManager.unscTactical().getMoraleRechargeMultiplier(activePlayerId);
            int rechargeAmount = (int) Math.ceil(stats.shieldCap() * rechargeMultiplier);

            state.rechargeShieldTo(Math.min(stats.shieldCap(), rechargeAmount));
            
            if (state.currentShield() != before) {
                emit(EventType.STATUS_REFRESHED, activePlayerId, globalTurnIndex, roundIndex, activePlayer, eventSequence, Map.of(
                        "targetInstanceId", unit.instanceId(),
                        "status", "SHIELD_RECHARGE",
                        "from", before,
                        "to", state.currentShield()
                ));
            }
        }
    }

    private void resolveOnTurnEndedTriggers(String endingPlayerId, int globalTurnIndex, int roundIndex, long eventSequence) {
        emit(EventType.STATUS_EXPIRED, endingPlayerId, globalTurnIndex, roundIndex, endingPlayerId, eventSequence, Map.of(
                "scope", "TURN_END"
        ));
    }

    private void emit(EventType type, String sourcePlayerId, int globalTurnIndex, int roundIndex, String activePlayerId, long eventSequence, Map<String, Object> payload) {
        GameEvent event = new GameEvent(
                eventSequence,
                type,
                globalTurnIndex,
                roundIndex,
                activePlayerId,
                sourcePlayerId,
                null,
                null,
                null,
                null,
                null,
                payload
        );
        eventBus.publish(event);
        eventBus.processQueue();
    }
}
