package com.haloce.tcg.card.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Manifest(
        String cardDataVersion,
        String schemaVersion,
        List<String> cardFiles
) {
}
