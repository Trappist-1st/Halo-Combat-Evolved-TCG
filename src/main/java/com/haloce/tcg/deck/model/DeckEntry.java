package com.haloce.tcg.deck.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeckEntry(
        String id,
        int count
) {
}
