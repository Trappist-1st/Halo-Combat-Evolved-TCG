package com.haloce.tcg.game;

import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.model.CardDef;
import com.haloce.tcg.card.model.CardType;
import com.haloce.tcg.card.model.KeywordInstance;
import com.haloce.tcg.card.model.Stats;
import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.combat.DamageContext;
import com.haloce.tcg.combat.DamageResolver;
import com.haloce.tcg.combat.DamageResult;
import com.haloce.tcg.combat.DamageType;
import com.haloce.tcg.combat.EntityCombatState;
import com.haloce.tcg.combat.InMemoryCombatStateStore;
import com.haloce.tcg.combat.listeners.CoverMitigationListener;
import com.haloce.tcg.core.event.EventBus;
import com.haloce.tcg.core.event.EventType;
import com.haloce.tcg.core.event.GameEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GameStateManager {
    private final EventBus eventBus;
    private final CardRepository cardRepository;
    private final GameMode gameMode;
    private final LinkedHashMap<String, PlayerState> playersById;
    private final List<String> turnOrder;
    private final Map<String, String> teamByPlayer;
    private final BattlefieldState battlefield;
    private final InMemoryCombatStateStore combatStateStore;
    private final UnitStatusStore unitStatusStore;
    private final DamageResolver damageResolver;
    private final TurnExecutor turnExecutor;

    private final Set<String> eliminatedPlayers = new HashSet<>();
    private final Set<String> attackersUsedThisTurn = new HashSet<>();
    private final Map<String, Integer> teamControlStreak = new HashMap<>();

    private int globalTurnIndex;
    private int roundIndex;
    private int activePlayerCursor;
    private long eventSequence;
    private GameStatus status;
    private GamePhase phase;
    private String winnerPlayerId;
    private String winnerTeamId;

    public GameStateManager(
            EventBus eventBus,
            CardRepository cardRepository,
            LinkedHashMap<String, PlayerState> playersById,
            GameMode gameMode,
            Map<String, String> teamByPlayer
    ) {
        if (playersById.size() < 2) {
            throw new IllegalArgumentException("At least 2 players are required");
        }
        this.eventBus = eventBus;
        this.cardRepository = cardRepository;
        this.playersById = playersById;
        this.gameMode = gameMode;
        this.turnOrder = List.copyOf(playersById.keySet());
        this.teamByPlayer = new HashMap<>(teamByPlayer);
        this.battlefield = new BattlefieldState(turnOrder);
        this.combatStateStore = new InMemoryCombatStateStore();
        this.unitStatusStore = new UnitStatusStore();
        this.damageResolver = new DamageResolver(eventBus, combatStateStore);
        this.eventBus.register(new CoverMitigationListener(combatStateStore));
        this.turnExecutor = new TurnExecutor(this);

        this.globalTurnIndex = 0;
        this.roundIndex = 1;
        this.activePlayerCursor = 0;
        this.eventSequence = 0;
        this.status = GameStatus.NOT_STARTED;
        this.phase = GamePhase.DRAW_RECHARGE;

        initializeTeamStreaks();
    }

    public GameStatus status() {
        return status;
    }

    public GameMode gameMode() {
        return gameMode;
    }

    public GamePhase phase() {
        return phase;
    }

    public int globalTurnIndex() {
        return globalTurnIndex;
    }

    public int roundIndex() {
        return roundIndex;
    }

    public String activePlayerId() {
        return turnOrder.get(activePlayerCursor);
    }

    public String winnerPlayerId() {
        return winnerPlayerId;
    }

    public String winnerTeamId() {
        return winnerTeamId;
    }

    public BattlefieldState battlefield() {
        return battlefield;
    }

    public PlayerState player(String playerId) {
        PlayerState player = playersById.get(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player: " + playerId);
        }
        return player;
    }

    public List<String> playerIds() {
        return turnOrder;
    }

    public List<String> alivePlayerIds() {
        return turnOrder.stream().filter(this::isAlive).collect(Collectors.toList());
    }

    public Map<String, PlayerState> playersSnapshot() {
        return Map.copyOf(playersById);
    }

    public String teamIdOf(String playerId) {
        return teamOf(playerId);
    }

    public boolean isPlayerAlive(String playerId) {
        return isAlive(playerId);
    }

    public void startGame() {
        ensureStatus(GameStatus.NOT_STARTED);

        for (PlayerState player : playersById.values()) {
            player.draw(5);
        }

        status = GameStatus.RUNNING;
        emit(EventType.GAME_STARTED, null, Map.of("mode", gameMode.name()));
        emit(EventType.ROUND_STARTED, activePlayerId(), Map.of("roundIndex", roundIndex));
        startTurnInternal();
    }

    public void advancePhase() {
        ensureStatus(GameStatus.RUNNING);
        turnExecutor.advancePhase();
    }

    public void deployUnitFromHand(String playerId, String cardInstanceId, Lane lane, GameRow row) {
        ensureStatus(GameStatus.RUNNING);
        ensureActivePlayer(playerId);
        ensurePhase(GamePhase.DEPLOYMENT);

        PlayerState player = player(playerId);
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

        emit(EventType.UNIT_DEPLOYED, playerId, Map.of(
                "cardId", deployed.definition().id(),
                "cardInstanceId", deployed.instanceId(),
                "lane", lane.name(),
                "row", row.name()
        ));

        resolveOnDeployTriggers(deployed, lane);
    }

    public void convertToBattery(String playerId, String cardInstanceId) {
        ensureStatus(GameStatus.RUNNING);
        ensureActivePlayer(playerId);
        if (phase != GamePhase.DEPLOYMENT && phase != GamePhase.SKIRMISH) {
            throw new IllegalStateException("Battery conversion is only allowed in deployment or skirmish phase");
        }

        PlayerState player = player(playerId);
        player.convertHandCardToBattery(cardInstanceId);

        emit(EventType.BATTERY_GENERATED, playerId, Map.of(
                "cardInstanceId", cardInstanceId,
                "battery", player.battery()
        ));
    }

    public DamageResult declareAttack(String attackerInstanceId, String defenderInstanceId) {
        ensureStatus(GameStatus.RUNNING);
        ensurePhase(GamePhase.SKIRMISH);

        UnitPosition attackerPos = requirePosition(attackerInstanceId, "attacker");
        UnitPosition defenderPos = requirePosition(defenderInstanceId, "defender");

        if (!attackerPos.playerId().equals(activePlayerId())) {
            throw new IllegalStateException("Only active player's units can attack");
        }
        if (!areOpponents(attackerPos.playerId(), defenderPos.playerId())) {
            throw new IllegalArgumentException("Target is not an opponent");
        }
        if (attackersUsedThisTurn.contains(attackerInstanceId)) {
            throw new IllegalStateException("This unit already attacked this turn");
        }
        if (attackerPos.lane() != defenderPos.lane()) {
            throw new IllegalArgumentException("Attacker and defender must be in the same lane");
        }

        ensureCanAttack(attackerPos.card());

        boolean attackerRanged = hasKeyword(attackerPos.card(), "RANGED");
        int defenderFrontlineCount = battlefield.lane(defenderPos.lane()).frontlineCount(defenderPos.playerId());
        if (defenderPos.row() == GameRow.BACKLINE && defenderFrontlineCount > 0 && !attackerRanged) {
            throw new IllegalStateException("Must target frontline first unless attacker has RANGED");
        }

        int baseDamage = attackValue(attackerPos.card());
        baseDamage += squadBonus(attackerPos);
        if (baseDamage <= 0) {
            throw new IllegalStateException("Attacker has no valid attack value");
        }

        emit(EventType.ATTACK_DECLARED, activePlayerId(), Map.of(
                "attackerInstanceId", attackerInstanceId,
                "defenderInstanceId", defenderInstanceId,
                "lane", attackerPos.lane().name()
        ));
        emit(EventType.TARGET_LOCKED, activePlayerId(), Map.of(
                "attackerInstanceId", attackerInstanceId,
                "defenderInstanceId", defenderInstanceId
        ));

        DamageType damageType = inferDamageType(attackerPos.card());
        int finalDamageMultiplier = 1;
        UnitStatus defenderStatus = unitStatusStore.getOrCreate(defenderInstanceId);
        EntityCombatState defenderCombatState = combatStateStore.get(defenderInstanceId);

        if (hasKeyword(attackerPos.card(), "HEADSHOT") && defenderCombatState.currentShield() <= 0) {
            finalDamageMultiplier *= 2;
            emit(EventType.HEADSHOT_TRIGGERED, activePlayerId(), Map.of(
                    "attackerInstanceId", attackerInstanceId,
                    "defenderInstanceId", defenderInstanceId
            ));
        }

        if (damageType == DamageType.BALLISTIC
            && defenderStatus.plasmaTaggedTurnIndex() == globalTurnIndex
            && defenderStatus.noobComboTriggeredTurnIndex() != globalTurnIndex) {
            finalDamageMultiplier *= 2;
            defenderStatus.setNoobComboTriggeredTurnIndex(globalTurnIndex);
        }

        boolean ignoreShield = hasKeyword(attackerPos.card(), "SENTINEL");

        DamageResult result = damageResolver.resolve(
            new DamageContext(attackerInstanceId, defenderInstanceId, baseDamage, damageType, false, false, finalDamageMultiplier, ignoreShield),
                globalTurnIndex,
                roundIndex,
                activePlayerId()
        );

        UnitStatus attackerStatus = unitStatusStore.getOrCreate(attackerInstanceId);
        attackerStatus.setAttackedTurnIndex(globalTurnIndex);
        attackerStatus.setHasCamoThisTurn(false);

        attackersUsedThisTurn.add(attackerInstanceId);

        if (result.shieldDamage() > 0 || result.healthDamage() > 0) {
            defenderStatus.markDamaged(globalTurnIndex, activePlayerId());
            if (damageType == DamageType.PLASMA) {
            defenderStatus.setPlasmaTaggedTurnIndex(globalTurnIndex);
            emit(EventType.PLASMA_TAG_APPLIED, activePlayerId(), Map.of(
                "targetInstanceId", defenderInstanceId,
                "turnIndex", globalTurnIndex
            ));
            }
        }

        if (result.shieldDamage() > 0 || result.healthDamage() > 0) {
            applyEmpIfNeeded(attackerPos.card(), defenderPos.card(), defenderInstanceId);
        }

        if (result.lethal()) {
            CardInstance dead = battlefield.removeUnit(defenderInstanceId)
                    .orElseThrow(() -> new IllegalStateException("Failed to remove defeated unit"));
            combatStateStore.remove(defenderInstanceId);
            unitStatusStore.remove(defenderInstanceId);
            player(dead.ownerPlayerId()).putToDiscard(dead);
            resolveOnKillTriggers(attackerPos.card(), dead, attackerPos.lane());
        }

        evaluateWinConditions(activePlayerId());
        return result;
    }

    public void attackBase(String attackerInstanceId, String targetPlayerId) {
        ensureStatus(GameStatus.RUNNING);
        ensurePhase(GamePhase.SKIRMISH);

        UnitPosition attackerPos = requirePosition(attackerInstanceId, "attacker");
        if (!attackerPos.playerId().equals(activePlayerId())) {
            throw new IllegalStateException("Only active player's units can attack");
        }
        if (!areOpponents(attackerPos.playerId(), targetPlayerId)) {
            throw new IllegalArgumentException("Target player is not an opponent");
        }
        if (attackersUsedThisTurn.contains(attackerInstanceId)) {
            throw new IllegalStateException("This unit already attacked this turn");
        }

        ensureCanAttack(attackerPos.card());

        List<String> defenderSide = alliesOf(targetPlayerId);
        int blockers = battlefield.laneUnitCount(attackerPos.lane(), defenderSide);
        if (blockers > 0) {
            throw new IllegalStateException("Cannot attack base while defenders remain in lane");
        }

        int damage = attackValue(attackerPos.card());
        if (damage <= 0) {
            throw new IllegalStateException("Attacker has no valid attack value");
        }

        emit(EventType.ATTACK_DECLARED, activePlayerId(), Map.of(
                "attackerInstanceId", attackerInstanceId,
                "targetPlayerId", targetPlayerId,
                "target", "BASE"
        ));

        damageBase(targetPlayerId, damage);
        UnitStatus attackerStatus = unitStatusStore.getOrCreate(attackerInstanceId);
        attackerStatus.setAttackedTurnIndex(globalTurnIndex);
        attackerStatus.setHasCamoThisTurn(false);
        attackersUsedThisTurn.add(attackerInstanceId);
    }

    public void hijackVehicle(String hijackerInstanceId, String targetVehicleInstanceId) {
        ensureStatus(GameStatus.RUNNING);
        if (phase != GamePhase.DEPLOYMENT && phase != GamePhase.SKIRMISH) {
            throw new IllegalStateException("Hijack is only allowed in deployment or skirmish phase");
        }

        UnitPosition hijackerPos = requirePosition(hijackerInstanceId, "hijacker");
        UnitPosition targetPos = requirePosition(targetVehicleInstanceId, "targetVehicle");

        if (!hijackerPos.playerId().equals(activePlayerId())) {
            throw new IllegalStateException("Only active player's units can execute hijack");
        }
        if (!areOpponents(hijackerPos.playerId(), targetPos.playerId())) {
            throw new IllegalArgumentException("Target is not an opponent vehicle");
        }
        if (hijackerPos.lane() != targetPos.lane()) {
            throw new IllegalArgumentException("Hijacker and target vehicle must be in the same lane");
        }
        if (!hasKeyword(hijackerPos.card(), "HIJACK")) {
            throw new IllegalStateException("Hijacker does not have HIJACK keyword");
        }
        if (!isVehicle(targetPos.card())) {
            throw new IllegalArgumentException("Target is not a vehicle");
        }

        if (!battlefield.hasSpace(hijackerPos.lane(), hijackerPos.playerId(), targetPos.row())) {
            throw new IllegalStateException("No space to seize target vehicle at this row");
        }

        PlayerState active = player(hijackerPos.playerId());
        if (!active.spendResources(2, 0)) {
            throw new IllegalStateException("Insufficient supply to execute hijack");
        }

        CardInstance removed = battlefield.removeUnit(targetVehicleInstanceId)
                .orElseThrow(() -> new IllegalStateException("Failed to remove target vehicle for hijack"));
        CardInstance seized = new CardInstance(
                removed.instanceId(),
                removed.definition(),
                hijackerPos.playerId(),
                removed.sourceEventSequence(),
                removed.sourceCardId()
        );
        battlefield.deploy(hijackerPos.playerId(), hijackerPos.lane(), targetPos.row(), seized);

        emit(EventType.HIJACK_EXECUTED, hijackerPos.playerId(), Map.of(
                "hijackerInstanceId", hijackerInstanceId,
                "targetVehicleInstanceId", targetVehicleInstanceId,
                "lane", hijackerPos.lane().name(),
                "row", targetPos.row().name()
        ));
    }

    public void damageBase(String playerId, int damage) {
        ensureStatus(GameStatus.RUNNING);

        PlayerState target = player(playerId);
        target.applyBaseDamage(damage);

        emit(EventType.BASE_DAMAGED, activePlayerId(), Map.of(
                "targetPlayerId", playerId,
                "damage", damage,
                "remainingBaseHealth", target.baseHealth()
        ));

        evaluateWinConditions(activePlayerId());
    }

    public void endTurn() {
        ensureStatus(GameStatus.RUNNING);
        ensurePhase(GamePhase.ENDSTEP);
        advancePhaseInternal();
    }

    void advancePhaseInternal() {
        ensureStatus(GameStatus.RUNNING);

        String currentPlayerId = activePlayerId();
        switch (phase) {
            case DRAW_RECHARGE -> throw new IllegalStateException("Draw/recharge is auto-resolved at turn start");
            case DEPLOYMENT -> {
                emit(EventType.PHASE_DEPLOYMENT_ENDED, currentPlayerId, Map.of());
                phase = GamePhase.SKIRMISH;
                emit(EventType.PHASE_SKIRMISH_STARTED, currentPlayerId, Map.of());
            }
            case SKIRMISH -> {
                emit(EventType.PHASE_SKIRMISH_ENDED, currentPlayerId, Map.of());
                phase = GamePhase.ENDSTEP;
                emit(EventType.PHASE_ENDSTEP_STARTED, currentPlayerId, Map.of());
            }
            case ENDSTEP -> {
                emit(EventType.PHASE_ENDSTEP_ENDED, currentPlayerId, Map.of());
                finishCurrentTurnAndRotate();
            }
        }
    }

    private void finishCurrentTurnAndRotate() {
        String endingPlayerId = activePlayerId();
        resolveOnTurnEndedTriggers(endingPlayerId);
        emit(EventType.TURN_ENDED, endingPlayerId, Map.of("globalTurnIndex", globalTurnIndex));

        evaluateWinConditions(endingPlayerId);
        if (status == GameStatus.FINISHED) {
            return;
        }

        int previousCursor = activePlayerCursor;
        int nextCursor = findNextAliveCursor((activePlayerCursor + 1) % turnOrder.size());
        if (nextCursor < 0) {
            finishGame(null, null, VictoryReason.NO_ALIVE_PLAYERS);
            return;
        }

        activePlayerCursor = nextCursor;

        boolean wrappedRound = activePlayerCursor <= previousCursor;
        if (wrappedRound) {
            emit(EventType.ROUND_ENDED, endingPlayerId, Map.of("roundIndex", roundIndex));
            roundIndex += 1;
            emit(EventType.ROUND_STARTED, activePlayerId(), Map.of("roundIndex", roundIndex));
        }

        startTurnInternal();
    }

    private void startTurnInternal() {
        globalTurnIndex += 1;
        attackersUsedThisTurn.clear();

        String currentPlayerId = activePlayerId();
        PlayerState active = player(currentPlayerId);
        refreshTurnStatuses(currentPlayerId);
        rechargeShieldsAtTurnStart(currentPlayerId);

        active.startTurnResourceStep();

        emit(EventType.TURN_STARTED, currentPlayerId, Map.of(
                "globalTurnIndex", globalTurnIndex,
                "roundIndex", roundIndex
        ));

        phase = GamePhase.DRAW_RECHARGE;
        emit(EventType.PHASE_DRAW_RECHARGE_STARTED, currentPlayerId, Map.of());

        boolean skipDraw = globalTurnIndex == 1 && activePlayerCursor == 0;
        if (!skipDraw) {
            List<CardInstance> drawn = active.draw(1);
            if (!drawn.isEmpty()) {
                CardInstance card = drawn.get(0);
                emit(EventType.CARD_DRAWN, currentPlayerId, Map.of(
                        "cardId", card.definition().id(),
                        "cardInstanceId", card.instanceId()
                ));
            }
        }

        emit(EventType.SUPPLY_CAP_INCREASED, currentPlayerId, Map.of("supplyCap", active.supplyCap()));
        emit(EventType.SUPPLY_REFILLED, currentPlayerId, Map.of("currentSupply", active.currentSupply()));

        emit(EventType.PHASE_DRAW_RECHARGE_ENDED, currentPlayerId, Map.of());
        phase = GamePhase.DEPLOYMENT;
        emit(EventType.PHASE_DEPLOYMENT_STARTED, currentPlayerId, Map.of());
    }

    /**
     * Evaluates win conditions in <b>fixed priority order</b> (see {@link VictoryReason}):
     * <ol>
     *   <li>Elimination — last player/team standing</li>
     *   <li>Full lane control — 3 lanes controlled for 2 consecutive end-steps</li>
     * </ol>
     * Only the first satisfied condition ends the game.
     */
    private void evaluateWinConditions(String currentPlayerId) {
        refreshEliminationByBaseHealth();

        // Priority 1: Elimination (last player or last team standing)
        if (gameMode == GameMode.TEAM_2V2) {
            evaluateTeamVictoryByElimination();
        } else {
            evaluateSingleWinnerByElimination();
        }

        if (status == GameStatus.FINISHED) {
            return;
        }

        // Priority 2: Full lane control streak (only if elimination did not already finish the game)
        if (gameMode == GameMode.TEAM_2V2) {
            String teamId = teamOf(currentPlayerId);
            int controlled = controlledLaneCountForTeam(teamId);
            int streak = controlled == Lane.values().length
                    ? teamControlStreak.compute(teamId, (k, v) -> v == null ? 1 : v + 1)
                    : teamControlStreak.compute(teamId, (k, v) -> 0);

            emit(EventType.LANE_CONTROL_UPDATED, currentPlayerId, Map.of(
                    "teamId", teamId,
                    "controlledLaneCount", controlled,
                    "fullControlStreak", streak
            ));

            if (streak >= 2) {
                finishGame(currentPlayerId, teamId, VictoryReason.TEAM_FULL_CONTROL_STREAK);
            }
            return;
        }

        PlayerState current = player(currentPlayerId);
        int controlled = controlledLaneCountForPlayer(currentPlayerId);
        current.setLaneControl(controlled);

        emit(EventType.LANE_CONTROL_UPDATED, currentPlayerId, Map.of(
                "controlledLaneCount", controlled,
                "fullControlStreak", current.fullControlStreak()
        ));

        if (current.fullControlStreak() >= 2) {
            finishGame(currentPlayerId, teamOf(currentPlayerId), VictoryReason.FULL_CONTROL_STREAK);
        }
    }

    private void refreshEliminationByBaseHealth() {
        for (PlayerState player : playersById.values()) {
            if (player.baseHealth() <= 0) {
                eliminatedPlayers.add(player.playerId());
            }
        }
    }

    private void evaluateSingleWinnerByElimination() {
        List<String> alive = alivePlayerIds();
        if (alive.size() == 1) {
            String winner = alive.get(0);
            finishGame(winner, teamOf(winner), VictoryReason.LAST_PLAYER_STANDING);
        }
    }

    private void evaluateTeamVictoryByElimination() {
        Set<String> aliveTeams = alivePlayerIds().stream()
                .map(this::teamOf)
                .collect(Collectors.toSet());
        if (aliveTeams.size() == 1) {
            String winnerTeam = aliveTeams.iterator().next();
            String winnerPlayer = alivePlayerIds().stream().findFirst().orElse(null);
            finishGame(winnerPlayer, winnerTeam, VictoryReason.LAST_TEAM_STANDING);
        }
    }

    private int controlledLaneCountForPlayer(String playerId) {
        int count = 0;
        List<String> own = List.of(playerId);
        List<String> opponents = opponentsOf(playerId);
        for (Lane lane : Lane.values()) {
            int ownTotal = battlefield.laneUnitCount(lane, own);
            int opponentTotal = battlefield.laneUnitCount(lane, opponents);
            int opponentFrontline = battlefield.laneFrontlineCount(lane, opponents);
            if (ownTotal > opponentTotal && opponentFrontline == 0) {
                count++;
            }
        }
        return count;
    }

    private int controlledLaneCountForTeam(String teamId) {
        int count = 0;
        List<String> own = alliesOfTeam(teamId);
        List<String> opponents = opponentsOfTeam(teamId);
        for (Lane lane : Lane.values()) {
            int ownTotal = battlefield.laneUnitCount(lane, own);
            int opponentTotal = battlefield.laneUnitCount(lane, opponents);
            int opponentFrontline = battlefield.laneFrontlineCount(lane, opponents);
            if (ownTotal > opponentTotal && opponentFrontline == 0) {
                count++;
            }
        }
        return count;
    }

    private void finishGame(String winnerPlayer, String teamId, VictoryReason reason) {
        if (status == GameStatus.FINISHED) {
            return;
        }
        winnerPlayerId = winnerPlayer;
        winnerTeamId = teamId;
        status = GameStatus.FINISHED;
        String reasonName = reason.name();
        emit(EventType.WIN_CONDITION_MET, winnerPlayer, Map.of(
                "reason", reasonName,
                "winnerPlayerId", winnerPlayer,
                "winnerTeamId", teamId
        ));
        emit(EventType.GAME_ENDED, winnerPlayer, Map.of(
                "winnerPlayerId", winnerPlayer,
                "winnerTeamId", teamId,
                "reason", reasonName
        ));
    }
 
     private void emit(EventType type, String sourcePlayerId, Map<String, Object> payload) {
         GameEvent event = new GameEvent(
                 ++eventSequence,
                type,
                globalTurnIndex,
                roundIndex,
                activePlayerId(),
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

    private UnitPosition requirePosition(String instanceId, String role) {
        UnitPosition position = battlefield.locateUnit(instanceId);
        if (position == null) {
            throw new IllegalArgumentException("Cannot find " + role + " unit: " + instanceId);
        }
        return position;
    }

    private DamageType inferDamageType(CardInstance card) {
        if (hasKeyword(card, "PLASMA")) {
            return DamageType.PLASMA;
        }
        if (hasKeyword(card, "BALLISTIC")) {
            return DamageType.BALLISTIC;
        }
        return DamageType.TRUE;
    }

    private static boolean hasKeyword(CardInstance card, String keyword) {
        if (card.definition().keywords() == null) {
            return false;
        }
        return card.definition().keywords().stream()
                .map(KeywordInstance::name)
                .anyMatch(keyword::equalsIgnoreCase);
    }

    private static int attackValue(CardInstance card) {
        Stats stats = card.definition().stats();
        if (stats == null) {
            return 0;
        }
        return stats.attack();
    }

    private int squadBonus(UnitPosition attackerPos) {
        if (!hasKeyword(attackerPos.card(), "SQUAD") || !isInfantry(attackerPos.card())) {
            return 0;
        }
        List<CardInstance> alliesInLane = battlefield.lane(attackerPos.lane()).unitsOf(attackerPos.playerId());
        long otherInfantry = alliesInLane.stream()
                .filter(unit -> !unit.instanceId().equals(attackerPos.card().instanceId()))
                .filter(this::isInfantry)
                .count();
        return Math.min(2, (int) otherInfantry);
    }

    private void ensureCanAttack(CardInstance attacker) {
        UnitStatus status = unitStatusStore.getOrCreate(attacker.instanceId());
        if (status.summonedTurnIndex() == globalTurnIndex && !hasKeyword(attacker, "DROP_POD")) {
            throw new IllegalStateException("Unit has summoning sickness this turn");
        }
        if (status.cannotAttackUntilTurn() >= globalTurnIndex) {
            throw new IllegalStateException("Unit cannot attack at this turn");
        }
    }

    private void refreshTurnStatuses(String activePlayerId) {
        for (CardInstance unit : battlefield.unitsOfPlayer(activePlayerId)) {
            UnitStatus status = unitStatusStore.getOrCreate(unit.instanceId());
            status.setHasCamoThisTurn(hasKeyword(unit, "CAMO"));
        }
    }

    private void rechargeShieldsAtTurnStart(String activePlayerId) {
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
            state.rechargeShieldTo(stats.shieldCap());
            if (state.currentShield() != before) {
                emit(EventType.STATUS_REFRESHED, activePlayerId, Map.of(
                        "targetInstanceId", unit.instanceId(),
                        "status", "SHIELD_RECHARGE",
                        "from", before,
                        "to", state.currentShield()
                ));
            }
        }
    }

    private void applyEmpIfNeeded(CardInstance attacker, CardInstance defender, String defenderInstanceId) {
        if (!hasKeyword(attacker, "EMP") || !isVehicle(defender)) {
            return;
        }
        UnitStatus defenderStatus = unitStatusStore.getOrCreate(defenderInstanceId);
        defenderStatus.setCannotAttackUntilTurn(globalTurnIndex + 1);
        defenderStatus.setCannotMoveUntilTurn(globalTurnIndex + 1);

        emit(EventType.EMP_APPLIED, activePlayerId(), Map.of(
                "targetInstanceId", defenderInstanceId,
                "cannotAttackUntilTurn", defenderStatus.cannotAttackUntilTurn(),
                "cannotMoveUntilTurn", defenderStatus.cannotMoveUntilTurn()
        ));
    }

    private void resolveOnKillTriggers(CardInstance attacker, CardInstance deadDefender, Lane lane) {
        if (!hasKeyword(attacker, "INFECT") || isVehicle(deadDefender)) {
            return;
        }

        if (!battlefield.hasSpace(lane, attacker.ownerPlayerId(), GameRow.BACKLINE)) {
            return;
        }

        Optional<CardDef> tokenOpt = Optional.ofNullable(cardRepository.get("TOKEN-COMBAT-FORM"));
        if (tokenOpt.isEmpty()) {
            return;
        }

        CardDef tokenDef = tokenOpt.get();
        CardInstance token = new CardInstance(
                UUID.randomUUID().toString(),
                tokenDef,
                attacker.ownerPlayerId(),
                eventSequence,
                attacker.definition().id()
        );
        battlefield.deploy(attacker.ownerPlayerId(), lane, GameRow.BACKLINE, token);

        Stats stats = token.definition().stats();
        if (stats != null) {
            combatStateStore.put(token.instanceId(), new EntityCombatState(stats.shieldCap(), stats.healthCap()));
        }
        UnitStatus status = unitStatusStore.getOrCreate(token.instanceId());
        status.setSummonedTurnIndex(globalTurnIndex);
        status.setHasCamoThisTurn(hasKeyword(token, "CAMO"));

        emit(EventType.INFECT_TRIGGERED, attacker.ownerPlayerId(), Map.of(
                "sourceInstanceId", attacker.instanceId(),
                "tokenId", token.definition().id(),
                "tokenInstanceId", token.instanceId(),
                "lane", lane.name(),
                "row", GameRow.BACKLINE.name()
        ));
    }

    private void resolveOnDeployTriggers(CardInstance deployed, Lane lane) {
        if (!"UNSC-003".equals(deployed.definition().id())) {
            return;
        }

        Optional<UnitPosition> allyOpt = findMostDamagedAlly(deployed.ownerPlayerId(), lane, deployed.instanceId());
        if (allyOpt.isEmpty()) {
            return;
        }

        UnitPosition ally = allyOpt.get();
        Stats stats = ally.card().definition().stats();
        if (stats == null) {
            return;
        }
        EntityCombatState combat = combatStateStore.get(ally.card().instanceId());
        int healed = combat.healHealth(2, stats.healthCap());
        if (healed <= 0) {
            return;
        }

        emit(EventType.STATUS_APPLIED, deployed.ownerPlayerId(), Map.of(
                "sourceInstanceId", deployed.instanceId(),
                "targetInstanceId", ally.card().instanceId(),
                "status", "HEAL",
                "amount", healed
        ));
    }

    private Optional<UnitPosition> findMostDamagedAlly(String ownerPlayerId, Lane preferredLane, String excludeInstanceId) {
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

    private void resolveOnTurnEndedTriggers(String endingPlayerId) {
        emit(EventType.STATUS_EXPIRED, endingPlayerId, Map.of(
                "scope", "TURN_END"
        ));
    }

    private static boolean hasTag(CardInstance card, String tag) {
        if (card.definition().tags() == null) {
            return false;
        }
        return card.definition().tags().stream().anyMatch(tag::equalsIgnoreCase);
    }

    private boolean isVehicle(CardInstance card) {
        return card.definition().cardType() == CardType.VESSEL
                || hasKeyword(card, "VEHICLE")
                || hasTag(card, "VEHICLE")
                || hasTag(card, "VESSEL");
    }

    private boolean isInfantry(CardInstance card) {
        return card.definition().cardType() == CardType.UNIT
                && !isVehicle(card)
                && (hasTag(card, "INFANTRY") || !hasTag(card, "VEHICLE"));
    }

    private void initializeTeamStreaks() {
        for (String teamId : teamByPlayer.values()) {
            teamControlStreak.put(teamId, 0);
        }
    }

    private String teamOf(String playerId) {
        return teamByPlayer.getOrDefault(playerId, playerId);
    }

    private List<String> alliesOf(String playerId) {
        String teamId = teamOf(playerId);
        return alliesOfTeam(teamId);
    }

    private List<String> alliesOfTeam(String teamId) {
        return turnOrder.stream()
                .filter(id -> teamOf(id).equals(teamId))
                .collect(Collectors.toList());
    }

    private List<String> opponentsOf(String playerId) {
        return turnOrder.stream()
                .filter(id -> areOpponents(playerId, id))
                .collect(Collectors.toList());
    }

    private List<String> opponentsOfTeam(String teamId) {
        return turnOrder.stream()
                .filter(id -> !teamOf(id).equals(teamId))
                .collect(Collectors.toList());
    }

    private boolean areOpponents(String playerA, String playerB) {
        if (playerA.equals(playerB)) {
            return false;
        }
        if (gameMode == GameMode.TEAM_2V2) {
            return !teamOf(playerA).equals(teamOf(playerB));
        }
        return true;
    }

    private boolean isAlive(String playerId) {
        return !eliminatedPlayers.contains(playerId) && player(playerId).baseHealth() > 0;
    }

    private int findNextAliveCursor(int startCursor) {
        for (int offset = 0; offset < turnOrder.size(); offset++) {
            int cursor = (startCursor + offset) % turnOrder.size();
            if (isAlive(turnOrder.get(cursor))) {
                return cursor;
            }
        }
        return -1;
    }
 
     private void ensureActivePlayer(String playerId) {
         if (!activePlayerId().equals(playerId)) {
             throw new IllegalStateException("Not active player turn: " + playerId);
         }
     }

    private void ensurePhase(GamePhase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Illegal phase. expected=" + expected + ", actual=" + phase);
        }
    }
 
     private void ensureStatus(GameStatus expected) {
         if (status != expected) {
             throw new IllegalStateException("Illegal game status. expected=" + expected + ", actual=" + status);
         }
     }
 }
