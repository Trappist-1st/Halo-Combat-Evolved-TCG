package com.haloce.tcg.game.campaign;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SpartanHeroManager {
    private static final int DEFAULT_MIA_RECOVERY_TURNS = 4;

    private final Set<String> freeMoveUsedThisTurn = new HashSet<>();
    private final Map<String, Integer> miaRecoverTurnByHero = new HashMap<>();
    private final Map<String, Integer> heroExperience = new HashMap<>();
    private final Set<String> uniqueSpartanOwners = new HashSet<>();

    public boolean canUseFreeMove(String heroInstanceId) {
        return heroInstanceId != null && !freeMoveUsedThisTurn.contains(heroInstanceId);
    }

    public boolean consumeFreeMove(String heroInstanceId) {
        if (!canUseFreeMove(heroInstanceId)) {
            return false;
        }
        freeMoveUsedThisTurn.add(heroInstanceId);
        return true;
    }

    public void resetTurn() {
        freeMoveUsedThisTurn.clear();
    }

    public boolean registerSpartanOwner(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return false;
        }
        if (uniqueSpartanOwners.contains(playerId)) {
            return false;
        }
        uniqueSpartanOwners.add(playerId);
        return true;
    }

    public void releaseSpartanOwner(String playerId) {
        if (playerId == null) {
            return;
        }
        uniqueSpartanOwners.remove(playerId);
    }

    public boolean triggerMia(String heroInstanceId, int currentTurn) {
        if (heroInstanceId == null || heroInstanceId.isBlank()) {
            return false;
        }
        miaRecoverTurnByHero.put(heroInstanceId, currentTurn + DEFAULT_MIA_RECOVERY_TURNS);
        return true;
    }

    public boolean isRecoverableThisTurn(String heroInstanceId, int currentTurn) {
        return miaRecoverTurnByHero.getOrDefault(heroInstanceId, Integer.MAX_VALUE) <= currentTurn;
    }

    public boolean recoverFromMia(String heroInstanceId) {
        if (!miaRecoverTurnByHero.containsKey(heroInstanceId)) {
            return false;
        }
        miaRecoverTurnByHero.remove(heroInstanceId);
        int xp = heroExperience.getOrDefault(heroInstanceId, 0);
        heroExperience.put(heroInstanceId, Math.max(0, xp / 2));
        return true;
    }

    public void addHeroExperience(String heroInstanceId, int value) {
        if (heroInstanceId == null || value <= 0) {
            return;
        }
        heroExperience.merge(heroInstanceId, value, Integer::sum);
    }

    public int heroExperience(String heroInstanceId) {
        return heroExperience.getOrDefault(heroInstanceId, 0);
    }

    public boolean tryVehicleHijack(double successChance) {
        double clamped = Math.max(0.0, Math.min(1.0, successChance));
        return Math.random() < clamped;
    }

    public int lastStandBuffAmount() {
        return 2;
    }

    public int soeivStunDurationTurn() {
        return 1;
    }

    public boolean canIgnoreElectronicJamming(boolean aiPluginAttached) {
        return aiPluginAttached;
    }
}
