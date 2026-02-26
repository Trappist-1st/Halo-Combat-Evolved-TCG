package com.haloce.tcg.game;

import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.model.Faction;
import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.combat.DamageResolver;
import com.haloce.tcg.combat.DamageResult;
import com.haloce.tcg.combat.InMemoryCombatStateStore;
import com.haloce.tcg.combat.listeners.CoverMitigationListener;
import com.haloce.tcg.core.event.DiplomacyListener;
import com.haloce.tcg.core.event.EventBus;
import com.haloce.tcg.core.event.EventReactionRegistry;
import com.haloce.tcg.core.event.EventType;
import com.haloce.tcg.core.event.GameEvent;
import com.haloce.tcg.game.campaign.CampaignManager;
import com.haloce.tcg.game.handlers.CombatHandler;
import com.haloce.tcg.game.handlers.DeploymentHandler;
import com.haloce.tcg.game.handlers.TurnFlowHandler;
import com.haloce.tcg.game.handlers.WinConditionEvaluator;

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
    private final EventReactionRegistry eventReactionRegistry;
    private final CampaignManager campaignManager;
    
    // Handlers - delegate pattern to reduce God class complexity
    private final DeploymentHandler deploymentHandler;
    private final CombatHandler combatHandler;
    private final TurnFlowHandler turnFlowHandler;
    private final WinConditionEvaluator winConditionEvaluator;

    private final Set<String> attackersUsedThisTurn = new HashSet<>();

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
        this(eventBus, cardRepository, playersById, gameMode, teamByPlayer, Map.of());
    }

    public GameStateManager(
            EventBus eventBus,
            CardRepository cardRepository,
            LinkedHashMap<String, PlayerState> playersById,
            GameMode gameMode,
            Map<String, String> teamByPlayer,
            Map<String, Faction> factionByPlayer
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
        this.eventReactionRegistry = new EventReactionRegistry(this::playerIds, this::player, factionByPlayer);
        for (var listener : DiplomacyListener.defaultListeners(eventReactionRegistry)) {
            this.eventBus.register(listener);
        }
        this.turnExecutor = new TurnExecutor(this);
        
        // Initialize Campaign Manager with game state reference
        this.campaignManager = new CampaignManager(this);
        
        // Initialize Handlers - delegate pattern to reduce complexity
        this.deploymentHandler = new DeploymentHandler(
                eventBus, cardRepository, battlefield, combatStateStore, unitStatusStore, campaignManager
        );
        this.combatHandler = new CombatHandler(
                eventBus, cardRepository, battlefield, combatStateStore, unitStatusStore, damageResolver, campaignManager
        );
        this.turnFlowHandler = new TurnFlowHandler(
                eventBus, battlefield, combatStateStore, unitStatusStore, campaignManager
        );
        this.winConditionEvaluator = new WinConditionEvaluator(
                eventBus, battlefield, gameMode, teamByPlayer
        );

        this.globalTurnIndex = 0;
        this.roundIndex = 1;
        this.activePlayerCursor = 0;
        this.eventSequence = 0;
        this.status = GameStatus.NOT_STARTED;
        this.phase = GamePhase.DRAW_RECHARGE;
    }
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

    public EventReactionRegistry eventReactionRegistry() {
        return eventReactionRegistry;
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
        deploymentHandler.deployUnitFromHand(
                playerId, cardInstanceId, lane, row, player,
                globalTurnIndex, roundIndex, activePlayerId(), ++eventSequence
        );
    }

    public void convertToBattery(String playerId, String cardInstanceId) {
        ensureStatus(GameStatus.RUNNING);
        ensureActivePlayer(playerId);
        if (phase != GamePhase.DEPLOYMENT && phase != GamePhase.SKIRMISH) {
            throw new IllegalStateException("Battery conversion is only allowed in deployment or skirmish phase");
        }

        PlayerState player = player(playerId);
        deploymentHandler.convertToBattery(
                playerId, cardInstanceId, player,
                globalTurnIndex, roundIndex, activePlayerId(), ++eventSequence
        );
    }

    public DamageResult declareAttack(String attackerInstanceId, String defenderInstanceId) {
        ensureStatus(GameStatus.RUNNING);
        ensurePhase(GamePhase.SKIRMISH);

        DamageResult result = combatHandler.declareAttack(
                attackerInstanceId, defenderInstanceId, activePlayerId(),
                globalTurnIndex, roundIndex, ++eventSequence,
                attackersUsedThisTurn, this::areOpponents, this::player
        );

        evaluateWinConditions(activePlayerId());
        return result;
    }

    public void attackBase(String attackerInstanceId, String targetPlayerId) {
        ensureStatus(GameStatus.RUNNING);
        ensurePhase(GamePhase.SKIRMISH);

        combatHandler.attackBase(
                attackerInstanceId, targetPlayerId, activePlayerId(),
                globalTurnIndex, roundIndex, ++eventSequence,
                attackersUsedThisTurn, this::areOpponents, this::alliesOf, this::damageBase
        );
    }

    public void hijackVehicle(String hijackerInstanceId, String targetVehicleInstanceId) {
        ensureStatus(GameStatus.RUNNING);
        if (phase != GamePhase.DEPLOYMENT && phase != GamePhase.SKIRMISH) {
            throw new IllegalStateException("Hijack is only allowed in deployment or skirmish phase");
        }

        PlayerState activePlayer = player(activePlayerId());
        combatHandler.hijackVehicle(
                hijackerInstanceId, targetVehicleInstanceId, activePlayerId(),
                globalTurnIndex, roundIndex, ++eventSequence,
                activePlayer, this::areOpponents
        );
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
        turnFlowHandler.onTurnEnd(endingPlayerId, globalTurnIndex, roundIndex, ++eventSequence);

        evaluateWinConditions(endingPlayerId);
        if (status == GameStatus.FINISHED) {
            return;
        }

        int previousCursor = activePlayerCursor;
        int nextCursor = findNextAliveCursor((activePlayerCursor + 1) % turnOrder.size());
        if (nextCursor < 0) {
            // No alive players
            status = GameStatus.FINISHED;
            return;
        }

        activePlayerCursor = nextCursor;

        boolean wrappedRound = activePlayerCursor <= previousCursor;
        if (wrappedRound) {
            turnFlowHandler.onRoundEnd(endingPlayerId, roundIndex, globalTurnIndex, ++eventSequence);
            roundIndex += 1;
            turnFlowHandler.onRoundStart(roundIndex, activePlayerId(), globalTurnIndex, ++eventSequence);
        }

        startTurnInternal();
    }

    private void startTurnInternal() {
        globalTurnIndex += 1;
        attackersUsedThisTurn.clear();

        String currentPlayerId = activePlayerId();
        PlayerState active = player(currentPlayerId);
        
        boolean skipDraw = globalTurnIndex == 1 && activePlayerCursor == 0;
        turnFlowHandler.onTurnStart(
                currentPlayerId, active, globalTurnIndex, roundIndex, ++eventSequence,
                skipDraw, activePlayerCursor
        );

        phase = GamePhase.DRAW_RECHARGE;
        emit(EventType.PHASE_DRAW_RECHARGE_STARTED, currentPlayerId, Map.of());
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
        var result = winConditionEvaluator.evaluateWinConditions(
                currentPlayerId, turnOrder, playersById,
                globalTurnIndex, roundIndex, ++eventSequence
        );
        
        if (result != null) {
            status = GameStatus.FINISHED;
            winnerPlayerId = result.winnerPlayerId();
            winnerTeamId = result.winnerTeamId();
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

    // Team management helpers
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
