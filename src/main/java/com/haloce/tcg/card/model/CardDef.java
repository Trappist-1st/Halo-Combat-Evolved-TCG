package com.haloce.tcg.card.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CardDef(
        String id,
        String name,
        String version,
        Faction faction,
        CardType cardType,
        Rarity rarity,
        Integer deckLimit,
        Boolean isLegendary,
        Cost cost,
        List<String> tags,
        List<KeywordInstance> keywords,
        Stats stats,
        List<Ability> abilities,
        VesselStats vesselStats,
        String tokenTemplate
) {
}
