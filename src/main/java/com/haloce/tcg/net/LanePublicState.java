package com.haloce.tcg.net;

import java.util.Map;

public record LanePublicState(
        String lane,
        Map<String, Integer> totalUnitsByPlayer,
        Map<String, Integer> frontlineUnitsByPlayer
) {
}
