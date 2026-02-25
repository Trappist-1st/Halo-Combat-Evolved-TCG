package com.haloce.tcg.card.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Ability(
        String id,
        String trigger,
        Integer priority,
        Integer limitPerTurn,
        List<Condition> conditions,
        List<Effect> effects
) {
}
