package com.haloce.tcg.card.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Cost(
        int supply,
        int battery
) {
}
