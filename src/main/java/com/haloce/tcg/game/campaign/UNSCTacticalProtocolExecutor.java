package com.haloce.tcg.game.campaign;

import com.haloce.tcg.game.Lane;

import java.util.HashMap;
import java.util.Map;

public class UNSCTacticalProtocolExecutor {
    private final Map<String, TacticalPreset> activePresetByPlayer = new HashMap<>();

    public void setPreset(String playerId, TacticalPreset preset) {
        if (playerId == null || preset == null) {
            return;
        }
        activePresetByPlayer.put(playerId, preset);
    }

    public TacticalPreset presetOf(String playerId) {
        return activePresetByPlayer.getOrDefault(playerId, TacticalPreset.BALANCED);
    }

    public TacticalDecision decideForLane(String playerId, Lane lane) {
        TacticalPreset preset = presetOf(playerId);
        return switch (preset) {
            case BALANCED -> new TacticalDecision(lane, true, true, false, 0.0);
            case FORTIFY -> new TacticalDecision(lane, false, true, true, 0.0);
            case SPEARHEAD -> new TacticalDecision(lane, true, false, false, 0.20);
            case SCORCHED_EARTH -> new TacticalDecision(lane, false, true, true, 0.10);
        };
    }

    public enum TacticalPreset {
        BALANCED,
        FORTIFY,
        SPEARHEAD,
        SCORCHED_EARTH
    }

    public record TacticalDecision(
            Lane focusLane,
            boolean prioritizeDropPod,
            boolean prioritizeRepair,
            boolean coleProtocolFallback,
            double macBoost
    ) {
    }
}
