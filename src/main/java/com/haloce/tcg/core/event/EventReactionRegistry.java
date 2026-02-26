package com.haloce.tcg.core.event;

import com.haloce.tcg.card.model.Faction;
import com.haloce.tcg.game.PlayerState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class EventReactionRegistry {
    private static final int SCHISM_COMMENDATION_THRESHOLD = 15;
    private static final int BIOMASS_ALERT_THRESHOLD = 15;

    private final Supplier<List<String>> playerIdsSupplier;
    private final Function<String, PlayerState> playerStateAccessor;

    private final DiplomacyMatrix diplomacyMatrix = new DiplomacyMatrix();
    private final Map<String, Faction> factionByPlayer = new HashMap<>();
    private final Map<String, Integer> commendationByPlayer = new HashMap<>();
    private final Map<String, Integer> faithByPlayer = new HashMap<>();
    private final Map<String, Integer> biomassByPlayer = new HashMap<>();
    private final Map<String, Integer> betrayerMarkUntilTurn = new HashMap<>();
    private final Set<String> protoGravemindOwners = new HashSet<>();
    private final Map<String, Integer> bonusTurnByPlayer = new HashMap<>();

    private boolean schismActive;
    private boolean survivalProtocolActive;

    public EventReactionRegistry(
            Supplier<List<String>> playerIdsSupplier,
            Function<String, PlayerState> playerStateAccessor,
            Map<String, Faction> initialFactions
    ) {
        this.playerIdsSupplier = playerIdsSupplier;
        this.playerStateAccessor = playerStateAccessor;
        if (initialFactions != null) {
            factionByPlayer.putAll(initialFactions);
        }
    }

    public void setPlayerFaction(String playerId, Faction faction) {
        if (playerId == null || faction == null) {
            return;
        }
        factionByPlayer.put(playerId, faction);
    }

    public DiplomacyRelation relationOf(String playerA, String playerB) {
        return diplomacyMatrix.relationOf(playerA, playerB);
    }

    public boolean canTarget(String sourcePlayerId, String targetPlayerId) {
        if (sourcePlayerId == null || targetPlayerId == null || sourcePlayerId.equals(targetPlayerId)) {
            return false;
        }
        DiplomacyRelation relation = relationOf(sourcePlayerId, targetPlayerId);
        if (relation == DiplomacyRelation.ALLIANCE) {
            return false;
        }
        if (relation == DiplomacyRelation.CIVIL_WAR) {
            return isCovenant(sourcePlayerId) && isCovenant(targetPlayerId);
        }
        return true;
    }

    public boolean isBetrayerMarked(String playerId, int globalTurnIndex) {
        return betrayerMarkUntilTurn.getOrDefault(playerId, -1) >= globalTurnIndex;
    }

    public boolean hasBonusTurn(String playerId) {
        return bonusTurnByPlayer.getOrDefault(playerId, 0) > 0;
    }

    public void consumeBonusTurn(String playerId) {
        bonusTurnByPlayer.computeIfPresent(playerId, (k, v) -> Math.max(0, v - 1));
    }

    public boolean tryCouncilTruce(
            String playerA,
            String playerB,
            int paymentA,
            int paymentB,
            int requiredPayment,
            boolean assassinDeployed,
            EventContext context,
            GameEvent basis
    ) {
        if (!schismActive || paymentA < requiredPayment || paymentB < requiredPayment) {
            return false;
        }
        emitEvent(context, basis, EventType.COVENANT_TRUCE_PROPOSED, Map.of(
                "playerA", playerA,
                "playerB", playerB,
                "requiredPayment", requiredPayment
        ));

        if (assassinDeployed) {
            bonusTurnByPlayer.merge(playerA, 1, Integer::sum);
            emitEvent(context, basis, EventType.COVENANT_TRUCE_BROKEN, Map.of(
                    "playerA", playerA,
                    "playerB", playerB,
                    "reason", "ASSASSIN_DEPLOYED",
                    "bonusTurnPlayer", playerA
            ));
            return false;
        }

        setRelationAndEmit(playerA, playerB, DiplomacyRelation.PEACE, context, basis);
        return true;
    }

    public void resolveSchismTargetLockByOrbit(
            String playerA,
            String playerB,
            int orbitCountA,
            int orbitCountB,
            EventContext context,
            GameEvent basis
    ) {
        if (!schismActive) {
            return;
        }
        if (orbitCountA <= 0 || orbitCountB <= 0) {
            setRelationAndEmit(playerA, playerB, DiplomacyRelation.PEACE, context, basis);
        }
    }

    public void addBiomass(String playerId, int amount, EventContext context, GameEvent basis) {
        if (playerId == null || amount <= 0) {
            return;
        }
        biomassByPlayer.merge(playerId, amount, Integer::sum);
        evaluateSurvivalProtocol(context, basis);
    }

    public void markProtoGravemind(String playerId, boolean present, EventContext context, GameEvent basis) {
        if (playerId == null) {
            return;
        }
        if (present) {
            protoGravemindOwners.add(playerId);
        } else {
            protoGravemindOwners.remove(playerId);
        }
        evaluateSurvivalProtocol(context, basis);
    }

    public void onEvent(GameEvent event, EventContext context) {
        if (event == null) {
            return;
        }
        switch (event.type()) {
            case TURN_STARTED -> {
                processSchism(context, event);
                evaluateSurvivalProtocol(context, event);
                cleanupExpiredBetrayerMark(event.globalTurnIndex());
            }
            case KILL_OCCURRED -> handleKillEvent(event, context);
            case INFECT_TRIGGERED -> {
                String floodPlayer = event.sourcePlayerId();
                if (floodPlayer != null) {
                    addBiomass(floodPlayer, 1, context, event);
                }
            }
            case TURN_ENDED -> {
                if (survivalProtocolActive) {
                    applyResourceAid(context, event);
                }
            }
            case ATTACK_DECLARED -> handleBetrayalIfNeeded(event, context);
            default -> {
            }
        }
    }

    private void handleKillEvent(GameEvent event, EventContext context) {
        String actor = event.sourcePlayerId();
        if (actor == null) {
            return;
        }
        commendationByPlayer.merge(actor, 1, Integer::sum);
        if (isCovenant(actor)) {
            faithByPlayer.merge(actor, 2, Integer::sum);
        }

        if (schismActive) {
            String targetPlayerId = event.targetPlayerId();
            if (targetPlayerId != null
                && isCovenant(actor)
                && isCovenant(targetPlayerId)
                && !actor.equals(targetPlayerId)) {
                faithByPlayer.merge(actor, 2, Integer::sum);
                PlayerState state = safePlayer(actor);
                if (state != null) {
                    state.grantSupply(2);
                }
            }
        }

        processSchism(context, event);
    }

    private void processSchism(EventContext context, GameEvent event) {
        List<String> covenantPlayers = playerIdsSupplier.get().stream()
                .filter(this::isCovenant)
                .collect(Collectors.toList());
        if (covenantPlayers.size() < 2) {
            return;
        }

        int commendationSum = covenantPlayers.stream()
                .mapToInt(id -> commendationByPlayer.getOrDefault(id, 0))
                .sum();

        if (!schismActive && commendationSum > SCHISM_COMMENDATION_THRESHOLD) {
            schismActive = true;
            for (int i = 0; i < covenantPlayers.size(); i++) {
                for (int j = i + 1; j < covenantPlayers.size(); j++) {
                    setRelationAndEmit(covenantPlayers.get(i), covenantPlayers.get(j), DiplomacyRelation.CIVIL_WAR, context, event);
                }
            }
            emitEvent(context, event, EventType.COVENANT_SCHISM_TRIGGERED, Map.of(
                    "players", covenantPlayers,
                    "commendation", commendationSum
            ));
        }
    }

    private void evaluateSurvivalProtocol(EventContext context, GameEvent event) {
        List<String> floodPlayers = playerIdsSupplier.get().stream()
                .filter(id -> factionByPlayer.getOrDefault(id, Faction.NEUTRAL) == Faction.FLOOD)
                .toList();
        if (floodPlayers.isEmpty()) {
            return;
        }

        boolean alert = floodPlayers.stream().anyMatch(id -> biomassByPlayer.getOrDefault(id, 0) > BIOMASS_ALERT_THRESHOLD)
                || floodPlayers.stream().anyMatch(protoGravemindOwners::contains);

        if (alert && !survivalProtocolActive) {
            survivalProtocolActive = true;
            activateSurvivalProtocol(context, event);
            emitEvent(context, event, EventType.SURVIVAL_PROTOCOL_STARTED, Map.of(
                    "floodPlayers", floodPlayers,
                    "reason", "BIOMASS_ALERT"
            ));
        } else if (!alert && survivalProtocolActive) {
            survivalProtocolActive = false;
            deactivateSurvivalProtocol(context, event);
            emitEvent(context, event, EventType.SURVIVAL_PROTOCOL_ENDED, Map.of(
                    "floodPlayers", floodPlayers
            ));
        }
    }

    private void activateSurvivalProtocol(EventContext context, GameEvent basis) {
        List<String> allianceCandidates = playerIdsSupplier.get().stream()
                .filter(id -> {
                    Faction faction = factionByPlayer.getOrDefault(id, Faction.NEUTRAL);
                    return faction == Faction.UNSC || faction == Faction.COVENANT || faction == Faction.FORERUNNER;
                })
                .toList();
        for (int i = 0; i < allianceCandidates.size(); i++) {
            for (int j = i + 1; j < allianceCandidates.size(); j++) {
                setRelationAndEmit(allianceCandidates.get(i), allianceCandidates.get(j), DiplomacyRelation.ALLIANCE, context, basis);
            }
        }
    }

    private void deactivateSurvivalProtocol(EventContext context, GameEvent basis) {
        List<String> players = new ArrayList<>(playerIdsSupplier.get());
        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                setRelationAndEmit(players.get(i), players.get(j), DiplomacyRelation.PEACE, context, basis);
            }
        }
    }

    private void applyResourceAid(EventContext context, GameEvent basis) {
        List<String> players = playerIdsSupplier.get();
        for (String receiverId : players) {
            PlayerState receiver = safePlayer(receiverId);
            if (receiver == null || receiver.currentSupply() >= 3) {
                continue;
            }
            for (String donorId : players) {
                if (donorId.equals(receiverId)) {
                    continue;
                }
                if (relationOf(receiverId, donorId) != DiplomacyRelation.ALLIANCE) {
                    continue;
                }
                PlayerState donor = safePlayer(donorId);
                if (donor == null || donor.currentSupply() <= 3) {
                    continue;
                }

                if (donor.consumeSupply(1)) {
                    receiver.grantSupply(1);
                    emitEvent(context, basis, EventType.RESOURCE_AID_TRANSFERRED, Map.of(
                            "donor", donorId,
                            "receiver", receiverId,
                            "amount", 1
                    ));
                    break;
                }
            }
        }
    }

    private void handleBetrayalIfNeeded(GameEvent event, EventContext context) {
        if (!survivalProtocolActive) {
            return;
        }
        String source = event.sourcePlayerId();
        String target = event.targetPlayerId();
        if (source == null || target == null || source.equals(target)) {
            return;
        }
        if (relationOf(source, target) == DiplomacyRelation.ALLIANCE) {
            betrayerMarkUntilTurn.put(source, event.globalTurnIndex() + 3);
            emitEvent(context, event, EventType.BETRAYER_MARKED, Map.of(
                    "playerId", source,
                    "untilTurn", event.globalTurnIndex() + 3
            ));
        }
    }

    private void cleanupExpiredBetrayerMark(int turn) {
        betrayerMarkUntilTurn.entrySet().removeIf(entry -> entry.getValue() < turn);
    }

    private boolean isCovenant(String playerId) {
        return factionByPlayer.getOrDefault(playerId, Faction.NEUTRAL) == Faction.COVENANT;
    }

    private PlayerState safePlayer(String playerId) {
        try {
            return playerStateAccessor.apply(playerId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setRelationAndEmit(
            String playerA,
            String playerB,
            DiplomacyRelation relation,
            EventContext context,
            GameEvent basis
    ) {
        diplomacyMatrix.setRelation(playerA, playerB, relation);
        emitEvent(context, basis, EventType.DIPLOMACY_RELATION_CHANGED, Map.of(
                "playerA", playerA,
                "playerB", playerB,
                "relation", relation.name()
        ));
    }

    private void emitEvent(EventContext context, GameEvent basis, EventType type, Map<String, Object> payload) {
        context.eventBus().publish(new GameEvent(
                System.nanoTime(),
                type,
                basis.globalTurnIndex(),
                basis.roundIndex(),
                basis.activePlayerId(),
                basis.sourcePlayerId(),
                basis.targetPlayerId(),
                basis.sourceEntityId(),
                basis.targetEntityId(),
                basis.lane(),
                basis.layer(),
                payload
        ));
    }
}
