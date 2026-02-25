package com.haloce.tcg.deck;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haloce.tcg.deck.model.DeckDef;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeckLoader {
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public DeckDef load(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            return mapper.readValue(stream, DeckDef.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read deck file: " + path, e);
        }
    }
}
