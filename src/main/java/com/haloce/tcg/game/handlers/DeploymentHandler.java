package com.haloce.tcg.game.handlers;

import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.model.CardType;
import com.haloce.tcg.card.model.Stats;
import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.combat.EntityCombatState;
import com.haloce.tcg.combat.InMemoryCombatStateStore;
import com.haloce.tcg.core.event.EventBus;
import com.haloce.tcg.core.event.EventType;
import com.haloce.tcg.core.event.GameEvent;
import com.haloce.tcg.game.BattlefieldState;
import com.haloce.tcg.game.GameRow;
import com.haloce.tcg.game.Lane;
import com.haloce.tcg.game.PlayerState;
import com.haloce.tcg.game.UnitStatus;
import com.haloce.tcg.game.UnitStatusStore;
import com.haloce.tcg.game.campaign.CampaignManager;

import java.util.Map;

/**
 * Handles unit deployment logic and integrates faction-specific deployment mechanics
 */
public class DeploymentHandler {
    private final EventBus eventBus;
    @SuppressWarnings("unused")
    private final CardRepository cardRepository;
    private final BattlefieldState battlefield;
    private final InMemoryCombatStateStore combatStateStore;
    private final UnitStatusStore unitStatusStore;
    private final CampaignManager campaignManager;

    public DeploymentHandler(
            EventBus eventBus,
            CardRepository cardRepository,
            BattlefieldState battlefield,
            InMemoryCombatStateStore combatStateStore,
            UnitStatusStore unitStatusStore,
            CampaignManager campaignManager
    ) {
        this.eventBus = eventBus;
        this.cardRepository = cardRepository;
        this.battlefield = battlefield;
        this.combatStateStore = combatStateStore;
        this.unitStatusStore = unitStatusStore;
        this.campaignManager = campaignManager;
    }

    public void deployUnitFromHand(
            String playerId,
            String cardInstanceId,
            Lane lane,
            GameRow row,
            PlayerState player,
            int globalTurnIndex,
            int roundIndex,
            String activePlayerId,
            long eventSequence
    ) {
        CardInstance card = player.findHandCard(cardInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Card is not in hand: " + cardInstanceId));

        if (card.definition().cardType() != CardType.UNIT && card.definition().cardType() != CardType.TOKEN) {
            throw new IllegalArgumentException("Card cannot be deployed to lane: " + card.definition().cardType());
        }

        int supplyCost = card.definition().cost() == null ? 0 : card.definition().cost().supply();
        int batteryCost = card.definition().cost() == null ? 0 : card.definition().cost().battery();

        if (!player.spendResources(supplyCost, batteryCost)) {
            throw new IllegalStateException("Insufficient resources to deploy card");
        }

        CardInstance deployed = player.removeFromHand(cardInstanceId)
                .orElseThrow(() -> new IllegalStateException("Card disappeared from hand: " + cardInstanceId));

        battlefield.deploy(playerId, lane, row, deployed);

        Stats stats = deployed.definition().stats();
        if (stats != null) {
            combatStateStore.put(deployed.instanceId(), new EntityCombatState(stats.shieldCap(), stats.healthCap()));
        }

        UnitStatus status = unitStatusStore.getOrCreate(deployed.instanceId());
        status.setSummonedTurnIndex(globalTurnIndex);
        status.setHasCamoThisTurn(hasKeyword(deployed, "CAMO"));

        emit(EventType.UNIT_DEPLOYED, playerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "cardId", deployed.definition().id(),
                "cardInstanceId", deployed.instanceId(),
                "lane", lane.name(),
                "row", row.name()
        ));

        // Campaign integration: UNSC Drop Pod tracking
        if (hasKeyword(deployed, "DROP_POD")) {
            campaignManager.unscDropPod().recordDropPodDeployment(playerId, deployed.instanceId(), lane);
        }

        // Campaign integration: Covenant Zealotry tracking
        campaignManager.covenantZealotry().recordDeployment(playerId, deployed.definition().id());

        // Campaign integration: Forerunner unit cost tracking for matter reconfiguration
        if (HandlerUtils.hasTag(deployed, "FORERUNNER")) {
            campaignManager.forerunnerVacuumEnergy().recordUnitCost(
                playerId, deployed.instanceId(), supplyCost
            );
        }

        // Campaign integration: Forerunner Sentinel manufactory binding
        if (deployed.definition().id().contains("SENTINEL") && deployed.definition().cardType() == CardType.TOKEN) {
            campaignManager.forerunnerSentinelNetwork().bindSentinelToManufactory(
                deployed.instanceId(), playerId, lane
            );
        }

        resolveOnDeployTriggers(deployed, lane, playerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence);
    }

    public void convertToBattery(String playerId, String cardInstanceId, PlayerState player, int globalTurnIndex, int roundIndex, String activePlayerId, long eventSequence) {
        player.convertHandCardToBattery(cardInstanceId);

        emit(EventType.BATTERY_GENERATED, playerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "cardInstanceId", cardInstanceId,
                "battery", player.battery()
        ));
    }

    private void resolveOnDeployTriggers(CardInstance deployed, Lane lane, String playerId, int globalTurnIndex, int roundIndex, String activePlayerId, long eventSequence) {
        // Existing UNSC-003 Medic logic
        if (!"UNSC-003".equals(deployed.definition().id())) {
            return;
        }

        var allyOpt = HandlerUtils.findMostDamagedAlly(deployed.ownerPlayerId(), lane, deployed.instanceId(), battlefield, combatStateStore);
        if (allyOpt.isEmpty()) {
            return;
        }

        var ally = allyOpt.get();
        Stats stats = ally.card().definition().stats();
        if (stats == null) {
            return;
        }
        EntityCombatState combat = combatStateStore.get(ally.card().instanceId());
        int healed = combat.healHealth(2, stats.healthCap());
        if (healed <= 0) {
            return;
        }

        emit(EventType.STATUS_APPLIED, playerId, globalTurnIndex, roundIndex, activePlayerId, eventSequence, Map.of(
                "sourceInstanceId", deployed.instanceId(),
                "targetInstanceId", ally.card().instanceId(),
                "status", "HEAL",
                "amount", healed
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

    private static boolean hasKeyword(CardInstance card, String keyword) {
        if (card.definition().keywords() == null) {
            return false;
        }
        return card.definition().keywords().stream()
                .map(k -> k.name())
                .anyMatch(keyword::equalsIgnoreCase);
    }
}
