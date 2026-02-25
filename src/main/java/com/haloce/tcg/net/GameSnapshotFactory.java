package com.haloce.tcg.net;

import com.haloce.tcg.game.GameStateManager;
import com.haloce.tcg.game.Lane;
import com.haloce.tcg.game.PlayerState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GameSnapshotFactory {
    public GameSnapshot create(GameStateManager game) {
        List<PlayerPublicState> players = new ArrayList<>();
        for (String playerId : game.playerIds()) {
            PlayerState state = game.player(playerId);
            players.add(new PlayerPublicState(
                    playerId,
                    game.teamIdOf(playerId),
                    game.isPlayerAlive(playerId),
                    state.baseHealth(),
                    state.supplyCap(),
                    state.currentSupply(),
                    state.battery(),
                    state.handSize(),
                    state.librarySize(),
                    state.discardSize(),
                    state.controlledLaneCount(),
                    state.fullControlStreak()
            ));
        }

        List<LanePublicState> lanes = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            Map<String, Integer> totalMap = new LinkedHashMap<>();
            Map<String, Integer> frontlineMap = new LinkedHashMap<>();
            for (String playerId : game.playerIds()) {
                totalMap.put(playerId, game.battlefield().lane(lane).totalCount(playerId));
                frontlineMap.put(playerId, game.battlefield().lane(lane).frontlineCount(playerId));
            }
            lanes.add(new LanePublicState(lane.name(), totalMap, frontlineMap));
        }

        return new GameSnapshot(
                game.gameMode().name(),
                game.status().name(),
                game.phase().name(),
                game.roundIndex(),
                game.globalTurnIndex(),
                game.activePlayerId(),
                game.winnerPlayerId(),
                game.winnerTeamId(),
                players,
                lanes
        );
    }
}
