package com.haloce.tcg.game;

import com.haloce.tcg.card.runtime.CardInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LaneBoardState {
    private static final int ROW_CAPACITY = 2;

    private final List<CardInstance> frontline = new ArrayList<>();
    private final List<CardInstance> backline = new ArrayList<>();

    public List<CardInstance> frontline() {
        return List.copyOf(frontline);
    }

    public List<CardInstance> backline() {
        return List.copyOf(backline);
    }

    public List<CardInstance> allUnits() {
        List<CardInstance> units = new ArrayList<>(frontline);
        units.addAll(backline);
        return List.copyOf(units);
    }

    public int frontlineCount() {
        return frontline.size();
    }

    public int totalCount() {
        return frontline.size() + backline.size();
    }

    public boolean contains(String instanceId) {
        return frontline.stream().anyMatch(card -> card.instanceId().equals(instanceId))
                || backline.stream().anyMatch(card -> card.instanceId().equals(instanceId));
    }

    public GameRow rowOf(String instanceId) {
        boolean inFrontline = frontline.stream().anyMatch(card -> card.instanceId().equals(instanceId));
        if (inFrontline) {
            return GameRow.FRONTLINE;
        }
        boolean inBackline = backline.stream().anyMatch(card -> card.instanceId().equals(instanceId));
        if (inBackline) {
            return GameRow.BACKLINE;
        }
        return null;
    }

    public CardInstance find(String instanceId) {
        return frontline.stream().filter(card -> card.instanceId().equals(instanceId)).findFirst()
                .or(() -> backline.stream().filter(card -> card.instanceId().equals(instanceId)).findFirst())
                .orElse(null);
    }

    public boolean hasSpace(GameRow row) {
        return switch (row) {
            case FRONTLINE -> frontline.size() < ROW_CAPACITY;
            case BACKLINE -> backline.size() < ROW_CAPACITY;
        };
    }

    public void add(GameRow row, CardInstance cardInstance) {
        if (!hasSpace(row)) {
            throw new IllegalStateException("Row is full: " + row);
        }
        switch (row) {
            case FRONTLINE -> frontline.add(cardInstance);
            case BACKLINE -> backline.add(cardInstance);
        }
    }

    public Optional<CardInstance> removeByInstanceId(String instanceId) {
        for (CardInstance card : new ArrayList<>(frontline)) {
            if (card.instanceId().equals(instanceId)) {
                frontline.remove(card);
                return Optional.of(card);
            }
        }
        for (CardInstance card : new ArrayList<>(backline)) {
            if (card.instanceId().equals(instanceId)) {
                backline.remove(card);
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }
}
