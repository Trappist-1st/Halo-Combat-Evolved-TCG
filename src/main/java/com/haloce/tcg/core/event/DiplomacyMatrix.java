package com.haloce.tcg.core.event;

import java.util.HashMap;
import java.util.Map;

public class DiplomacyMatrix {
    private final Map<String, DiplomacyRelation> relationByPair = new HashMap<>();

    public DiplomacyRelation relationOf(String playerA, String playerB) {
        if (playerA == null || playerB == null || playerA.equals(playerB)) {
            return DiplomacyRelation.PEACE;
        }
        return relationByPair.getOrDefault(pairKey(playerA, playerB), DiplomacyRelation.PEACE);
    }

    public void setRelation(String playerA, String playerB, DiplomacyRelation relation) {
        if (playerA == null || playerB == null || playerA.equals(playerB)) {
            return;
        }
        relationByPair.put(pairKey(playerA, playerB), relation);
    }

    private static String pairKey(String playerA, String playerB) {
        return playerA.compareTo(playerB) < 0
                ? playerA + "|" + playerB
                : playerB + "|" + playerA;
    }
}
