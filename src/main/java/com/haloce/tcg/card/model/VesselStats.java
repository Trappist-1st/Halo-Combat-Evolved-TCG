package com.haloce.tcg.card.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VesselStats(
        String tonnageClass,
        int hangars,
        String payloadType,
        int shieldHardening,
        int pointDefense,
        boolean multiSection,
        List<VesselSection> sections
) {
}
