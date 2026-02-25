package com.haloce.tcg.deck.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeckDef(
        String deckId,
        String owner,
        List<DeckEntry> cards
) {
}
