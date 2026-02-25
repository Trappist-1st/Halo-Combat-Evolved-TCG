package com.haloce.tcg.card.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Effect(
        String type,
        String target,
        Map<String, Object> params
) {
}
