package com.haloce.tcg.game;

import java.util.HashMap;
import java.util.Map;

public class UnitStatusStore {
    private final Map<String, UnitStatus> statuses = new HashMap<>();

    public UnitStatus getOrCreate(String instanceId) {
        return statuses.computeIfAbsent(instanceId, key -> new UnitStatus());
    }

    public UnitStatus get(String instanceId) {
        return statuses.get(instanceId);
    }

    public void remove(String instanceId) {
        statuses.remove(instanceId);
    }
}
