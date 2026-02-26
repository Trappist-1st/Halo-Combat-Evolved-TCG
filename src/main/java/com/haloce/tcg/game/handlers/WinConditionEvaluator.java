package com.haloce.tcg.game.handlers;

import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.core.event.EventBus;
import com.haloce.tcg.core.event.EventType;
import com.haloce.tcg.core.event.GameEvent;
import com.haloce.tcg.game.BattlefieldState;
import com.haloce.tcg.game.GameMode;
import com.haloce.tcg.game.Lane;
import com.haloce.tcg.game.PlayerState;
import com.haloce.tcg.game.VictoryReason;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates and enforces win conditions
 */
public class WinConditionEvaluator {
    private final EventBus eventBus;
    private final BattlefieldState battlefield;
    private final GameMode gameMode;
    private final Map<String, String> teamByPlayer;
    private final Map<String, Integer> teamControlStreak;
    private final Set<String> eliminatedPlayers;

    public WinConditionEvaluator(
            EventBus eventBus,
            BattlefieldState battlefield,
            GameMode gameMode,
            Map<String, String> teamByPlayer
    ) {
        this.eventBus = eventBus;
        this.battlefield = battlefield;
        this.gameMode = gameMode;
        this.teamByPlayer = teamByPlayer;
        this.teamControlStreak = new HashMap<>();
        this.eliminatedPlayers = new HashSet<>();
        initializeTeamStreaks();
    }

    public WinResult evaluateWinConditions(
            String currentPlayerId,
            List<String> turnOrder,
            Map<String, PlayerState> playersById,
            int globalTurnIndex,
            int roundIndex,
            long eventSequence
    ) {
        refreshEliminationByBaseHealth(playersById);

        // Priority 1: Elimination (last player or last team standing)
        if (gameMode == GameMode.TEAM_2V2) {
            WinResult teamResult = evaluateTeamVictoryByElimination(turnOrder, globalTurnIndex, roundIndex, currentPlayerId, eventSequence);
            if (teamResult != null) {
                return teamResult;
            }
        } else {
            WinResult soloResult = evaluateSingleWinnerByElimination(turnOrder, globalTurnIndex, roundIndex, currentPlayerId, eventSequence);
            if (soloResult != null) {
                return soloResult;
            }
        }

        // Priority 2: Full lane control streak
        if (gameMode == GameMode.TEAM_2V2) {
            String teamId = teamOf(currentPlayerId);
            int controlled = controlledLaneCountForTeam(teamId, turnOrder);
            int streak = controlled == Lane.values().length
                    ? teamControlStreak.compute(teamId, (k, v) -> v == null ? 1 : v + 1)
                    : teamControlStreak.compute(teamId, (k, v) -> 0);

            emit(EventType.LANE_CONTROL_UPDATED, currentPlayerId, globalTurnIndex, roundIndex, currentPlayerId, eventSequence, Map.of(
                    "teamId", teamId,
                    "controlledLaneCount", controlled,
                    "fullControlStreak", streak
            ));

            if (streak >= 2) {
                return finishGame(currentPlayerId, teamId, VictoryReason.TEAM_FULL_CONTROL_STREAK, globalTurnIndex, roundIndex, eventSequence);
            }
        } else {
            PlayerState current = playersById.get(currentPlayerId);
            int controlled = controlledLaneCountForPlayer(currentPlayerId, turnOrder);
            current.setLaneControl(controlled);

            emit(EventType.LANE_CONTROL_UPDATED, currentPlayerId, globalTurnIndex, roundIndex, currentPlayerId, eventSequence, Map.of(
                    "controlledLaneCount", controlled,
                    "fullControlStreak", current.fullControlStreak()
            ));

            if (current.fullControlStreak() >= 2) {
                return finishGame(currentPlayerId, teamOf(currentPlayerId), VictoryReason.FULL_CONTROL_STREAK, globalTurnIndex, roundIndex, eventSequence);
            }
        }

        return null; // Game continues
    }

    public boolean isPlayerEliminated(String playerId) {
        return eliminatedPlayers.contains(playerId);
    }

    private void refreshEliminationByBaseHealth(Map<String, PlayerState> playersById) {
        for (PlayerState player : playersById.values()) {
            if (player.baseHealth() <= 0) {
                eliminatedPlayers.add(player.playerId());
            }
        }
    }

    private WinResult evaluateSingleWinnerByElimination(List<String> turnOrder, int globalTurnIndex, int roundIndex, String currentPlayerId, long eventSequence) {
        List<String> alive = alivePlayerIds(turnOrder);
        if (alive.size() == 1) {
            String winner = alive.get(0);
            return finishGame(winner, teamOf(winner), VictoryReason.LAST_PLAYER_STANDING, globalTurnIndex, roundIndex, eventSequence);
        }
        return null;
    }

    private WinResult evaluateTeamVictoryByElimination(List<String> turnOrder, int globalTurnIndex, int roundIndex, String currentPlayerId, long eventSequence) {
        Set<String> aliveTeams = alivePlayerIds(turnOrder).stream()
                .map(this::teamOf)
                .collect(Collectors.toSet());
        if (aliveTeams.size() == 1) {
            String winnerTeam = aliveTeams.iterator().next();
            String winnerPlayer = alivePlayerIds(turnOrder).stream().findFirst().orElse(null);
            return finishGame(winnerPlayer, winnerTeam, VictoryReason.LAST_TEAM_STANDING, globalTurnIndex, roundIndex, eventSequence);
        }
        return null;
    }

    private int controlledLaneCountForPlayer(String playerId, List<String> turnOrder) {
        int count = 0;
        List<String> own = List.of(playerId);
        List<String> opponents = opponentsOf(playerId, turnOrder);
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

    private int controlledLaneCountForTeam(String teamId, List<String> turnOrder) {
        int count = 0;
        List<String> own = alliesOfTeam(teamId, turnOrder);
        List<String> opponents = opponentsOfTeam(teamId, turnOrder);
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

    private WinResult finishGame(String winnerPlayer, String teamId, VictoryReason reason, int globalTurnIndex, int roundIndex, long eventSequence) {
        String reasonName = reason.name();
        emit(EventType.WIN_CONDITION_MET, winnerPlayer, globalTurnIndex, roundIndex, winnerPlayer, eventSequence, Map.of(
                "reason", reasonName,
                "winnerPlayerId", winnerPlayer,
                "winnerTeamId", teamId
        ));
        emit(EventType.GAME_ENDED, winnerPlayer, globalTurnIndex, roundIndex, winnerPlayer, eventSequence, Map.of(
                "winnerPlayerId", winnerPlayer,
                "winnerTeamId", teamId,
                "reason", reasonName
        ));
        return new WinResult(winnerPlayer, teamId, reason);
    }

    private void initializeTeamStreaks() {
        for (String teamId : teamByPlayer.values()) {
            teamControlStreak.put(teamId, 0);
        }
    }

    private String teamOf(String playerId) {
        return teamByPlayer.getOrDefault(playerId, playerId);
    }

    private List<String> alliesOfTeam(String teamId, List<String> turnOrder) {
        return turnOrder.stream()
                .filter(id -> teamOf(id).equals(teamId))
                .collect(Collectors.toList());
    }

    private List<String> opponentsOf(String playerId, List<String> turnOrder) {
        return turnOrder.stream()
                .filter(id -> areOpponents(playerId, id))
                .collect(Collectors.toList());
    }

    private List<String> opponentsOfTeam(String teamId, List<String> turnOrder) {
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

    private List<String> alivePlayerIds(List<String> turnOrder) {
        return turnOrder.stream().filter(id -> !eliminatedPlayers.contains(id)).collect(Collectors.toList());
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

    public record WinResult(String winnerPlayerId, String winnerTeamId, VictoryReason reason) {}
}
