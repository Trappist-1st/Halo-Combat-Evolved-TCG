package com.haloce.tcg.game.campaign;

import com.haloce.tcg.card.runtime.CardInstance;

import java.util.List;

public interface DogmaConstraint {
    List<String> filterLegalTargets(CardInstance source, List<String> candidateTargetInstanceIds);

    boolean canRetreat(CardInstance source, boolean hasLeaderInLane);

    boolean canReposition(CardInstance source, boolean hasLeaderInLane);
}
