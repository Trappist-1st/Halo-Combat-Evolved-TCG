package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

public class CovenantZealotryManager {
    private static final double DEFAULT_SCHISM_CHANCE = 0.10;
    private static final double DEFAULT_DAMAGE_TAKEN_MULTIPLIER = 1.20;
    private final Random random;

    public CovenantZealotryManager(Random random) {
        this.random = Objects.requireNonNull(random);
    }

    public CovenantZealotryManager() {
        this(new Random());
    }

    public CovenantRank rankOf(CardInstance unit) {
        if (unit == null || unit.definition() == null || unit.definition().tags() == null) {
            return CovenantRank.NON_COVENANT;
        }
        List<String> tags = unit.definition().tags().stream()
                .map(tag -> tag == null ? "" : tag.toUpperCase(Locale.ROOT))
                .toList();

        if (tags.contains("LEADER") || tags.contains("PROPHET") || tags.contains("HIERARCH")) {
            return CovenantRank.LEADER;
        }
        if (tags.contains("ELITE") || tags.contains("BRUTE") || tags.contains("MAJOR") || tags.contains("ULTRA")) {
            return CovenantRank.MAJOR;
        }
        if (tags.contains("GRUNT") || tags.contains("JACKAL") || tags.contains("MINOR")) {
            return CovenantRank.MINOR;
        }
        return CovenantRank.NON_COVENANT;
    }

    public boolean hasMajorOrLeaderInLane(List<CardInstance> laneAllies) {
        return laneAllies.stream()
                .map(this::rankOf)
                .anyMatch(rank -> rank == CovenantRank.MAJOR || rank == CovenantRank.LEADER);
    }

    public int zealotryAttackBonus(CardInstance unit, List<CardInstance> laneAllies) {
        return rankOf(unit) == CovenantRank.MINOR && hasMajorOrLeaderInLane(laneAllies) ? 1 : 0;
    }

    public double zealotryDamageTakenMultiplier(CardInstance unit, List<CardInstance> laneAllies) {
        return rankOf(unit) == CovenantRank.MINOR && hasMajorOrLeaderInLane(laneAllies)
                ? DEFAULT_DAMAGE_TAKEN_MULTIPLIER
                : 1.0;
    }

    public boolean shouldCollapse(CardInstance unit, boolean leaderDiedThisTurn) {
        return leaderDiedThisTurn && rankOf(unit) == CovenantRank.MINOR;
    }

    public String rollGreatSchismFriendlyFire(List<CardInstance> covenantUnits) {
        Map<String, List<CardInstance>> byRace = covenantUnits.stream()
                .collect(Collectors.groupingBy(this::raceKey));

        boolean hasElite = byRace.containsKey("ELITE");
        boolean hasBrute = byRace.containsKey("BRUTE");
        if (!(hasElite && hasBrute)) {
            return null;
        }
        if (random.nextDouble() >= DEFAULT_SCHISM_CHANCE) {
            return null;
        }
        int pick = random.nextInt(covenantUnits.size());
        return covenantUnits.get(pick).instanceId();
    }

    private String raceKey(CardInstance unit) {
        List<String> tags = unit.definition().tags();
        if (tags == null) {
            return "OTHER";
        }
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String normalized = tag.toUpperCase(Locale.ROOT);
            if (normalized.contains("ELITE")) {
                return "ELITE";
            }
            if (normalized.contains("BRUTE")) {
                return "BRUTE";
            }
        }
        return "OTHER";
    }
}
