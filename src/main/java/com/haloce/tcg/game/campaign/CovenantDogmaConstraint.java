package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;

import java.util.List;

public class CovenantDogmaConstraint implements DogmaConstraint {
    @Override
    public List<String> filterLegalTargets(CardInstance source, List<String> candidateTargetInstanceIds) {
        return candidateTargetInstanceIds == null ? List.of() : List.copyOf(candidateTargetInstanceIds);
    }

    public List<String> enforceHonorOfSangheili(
            CardInstance source,
            CovenantRank rank,
            List<String> enemyHeroInstanceIds,
            List<String> defaultTargets
    ) {
        if (source == null) {
            return List.of();
        }
        if (rank == CovenantRank.MAJOR && enemyHeroInstanceIds != null && !enemyHeroInstanceIds.isEmpty()) {
            return List.copyOf(enemyHeroInstanceIds);
        }
        return defaultTargets == null ? List.of() : List.copyOf(defaultTargets);
    }

    @Override
    public boolean canRetreat(CardInstance source, boolean hasLeaderInLane) {
        return !hasLeaderInLane;
    }

    @Override
    public boolean canReposition(CardInstance source, boolean hasLeaderInLane) {
        return !hasLeaderInLane;
    }
}
