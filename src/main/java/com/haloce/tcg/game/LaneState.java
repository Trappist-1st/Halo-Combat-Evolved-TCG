package com.haloce.tcg.game;

import com.haloce.tcg.card.runtime.CardInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LaneState {
    private final Map<String, LaneBoardState> sidesByPlayer = new LinkedHashMap<>();

    public LaneState(List<String> playerIds) {
        for (String playerId : playerIds) {
            sidesByPlayer.put(playerId, new LaneBoardState());
        }
    }

    public LaneBoardState side(String playerId) {
        LaneBoardState state = sidesByPlayer.get(playerId);
        if (state == null) {
            throw new IllegalArgumentException("Unknown player for lane: " + playerId);
        }
        return state;
    }

    public void deploy(String playerId, GameRow row, CardInstance cardInstance) {
        side(playerId).add(row, cardInstance);
    }

    public boolean hasSpace(String playerId, GameRow row) {
        return side(playerId).hasSpace(row);
    }

    public Optional<CardInstance> removeByInstanceId(String instanceId) {
        for (LaneBoardState state : sidesByPlayer.values()) {
            Optional<CardInstance> removed = state.removeByInstanceId(instanceId);
            if (removed.isPresent()) {
                return removed;
            }
        }
        return Optional.empty();
    }

    public int totalCount(String playerId) {
        return side(playerId).totalCount();
    }

    public int frontlineCount(String playerId) {
        return side(playerId).frontlineCount();
    }

    public UnitPosition locate(String instanceId, Lane lane) {
        for (Map.Entry<String, LaneBoardState> entry : sidesByPlayer.entrySet()) {
            String playerId = entry.getKey();
            LaneBoardState board = entry.getValue();
            if (board.contains(instanceId)) {
                GameRow row = board.rowOf(instanceId);
                return new UnitPosition(playerId, lane, row, board.find(instanceId));
            }
        }
        return null;
    }

    public int totalCountForPlayers(List<String> playerIds) {
        int total = 0;
        for (String playerId : playerIds) {
            if (sidesByPlayer.containsKey(playerId)) {
                total += totalCount(playerId);
            }
        }
        return total;
    }

    public int frontlineCountForPlayers(List<String> playerIds) {
        int total = 0;
        for (String playerId : playerIds) {
            if (sidesByPlayer.containsKey(playerId)) {
                total += frontlineCount(playerId);
            }
        }
        return total;
    }

    public List<CardInstance> unitsOf(String playerId) {
        LaneBoardState side = side(playerId);
        return side.allUnits();
    }

    public List<CardInstance> unitsOfPlayers(List<String> playerIds) {
        List<CardInstance> units = new ArrayList<>();
        for (String playerId : playerIds) {
            if (sidesByPlayer.containsKey(playerId)) {
                units.addAll(unitsOf(playerId));
            }
        }
        return List.copyOf(units);
    }

    public boolean isControlledBy(String playerId) {
        if (!sidesByPlayer.containsKey(playerId) || sidesByPlayer.size() != 2) {
            return false;
        }

        String opponentId = sidesByPlayer.keySet().stream()
                .filter(id -> !id.equals(playerId))
                .findFirst()
                .orElseThrow();

        int ownTotal = totalCount(playerId);
        int opponentTotal = totalCount(opponentId);
        int opponentFrontline = frontlineCount(opponentId);

        return ownTotal > opponentTotal && opponentFrontline == 0;
    }
}
