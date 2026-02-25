package com.haloce.tcg.game;

import com.haloce.tcg.card.runtime.CardInstance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BattlefieldState {
    private final Map<Lane, LaneState> lanes = new EnumMap<>(Lane.class);

    public BattlefieldState(List<String> playerIds) {
        for (Lane lane : Lane.values()) {
            lanes.put(lane, new LaneState(playerIds));
        }
    }

    public LaneState lane(Lane lane) {
        return lanes.get(lane);
    }

    public void deploy(String playerId, Lane lane, GameRow row, CardInstance cardInstance) {
        lane(lane).deploy(playerId, row, cardInstance);
    }

    public boolean hasSpace(Lane lane, String playerId, GameRow row) {
        return lane(lane).hasSpace(playerId, row);
    }

    public Optional<CardInstance> removeUnit(String instanceId) {
        for (LaneState laneState : lanes.values()) {
            Optional<CardInstance> removed = laneState.removeByInstanceId(instanceId);
            if (removed.isPresent()) {
                return removed;
            }
        }
        return Optional.empty();
    }

    public UnitPosition locateUnit(String instanceId) {
        for (Lane lane : Lane.values()) {
            UnitPosition position = lane(lane).locate(instanceId, lane);
            if (position != null) {
                return position;
            }
        }
        return null;
    }

    public int laneUnitCount(Lane lane, List<String> playerIds) {
        return lane(lane).totalCountForPlayers(playerIds);
    }

    public int laneFrontlineCount(Lane lane, List<String> playerIds) {
        return lane(lane).frontlineCountForPlayers(playerIds);
    }

    public List<CardInstance> unitsOfPlayer(String playerId) {
        List<CardInstance> units = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            units.addAll(lane(lane).unitsOf(playerId));
        }
        return List.copyOf(units);
    }

    public List<CardInstance> unitsOfPlayers(List<String> playerIds) {
        List<CardInstance> units = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            units.addAll(lane(lane).unitsOfPlayers(playerIds));
        }
        return List.copyOf(units);
    }

    public int controlledLaneCount(String playerId) {
        int count = 0;
        for (Lane lane : Lane.values()) {
            if (lane(lane).isControlledBy(playerId)) {
                count++;
            }
        }
        return count;
    }
}
