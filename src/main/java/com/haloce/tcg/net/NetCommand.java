package com.haloce.tcg.net;

import java.util.Map;

public record NetCommand(
        String type,
        Map<String, Object> payload,
        Long seq
) {
}
