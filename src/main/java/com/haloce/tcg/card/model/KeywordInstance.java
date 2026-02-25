package com.haloce.tcg.card.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeywordInstance(
        String name,
        Integer value
) {
}
