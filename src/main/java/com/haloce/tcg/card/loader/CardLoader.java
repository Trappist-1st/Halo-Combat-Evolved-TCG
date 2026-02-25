package com.haloce.tcg.card.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haloce.tcg.card.model.CardDef;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CardLoader {
    private final ObjectMapper mapper;
    private final SemanticValidator semanticValidator;

    public CardLoader(SemanticValidator semanticValidator) {
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.semanticValidator = semanticValidator;
    }

    public CardRepository loadFromResourceDir(Path resourceDir) {
        Path cardsDir = resourceDir.resolve("cards");
        Path manifestPath = cardsDir.resolve("manifest.json");

        Manifest manifest = readManifest(manifestPath);
        if (manifest.cardFiles() == null || manifest.cardFiles().isEmpty()) {
            throw new IllegalStateException("Manifest cardFiles is empty");
        }

        CardRepository repository = new CardRepository();
        for (String fileName : manifest.cardFiles()) {
            Path filePath = cardsDir.resolve(fileName);
            List<CardDef> defs = readCardDefs(filePath);
            for (CardDef def : defs) {
                semanticValidator.validate(def);
                repository.put(def);
            }
        }

        semanticValidator.validateCrossReferences(repository);
        return repository;
    }

    private Manifest readManifest(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            return mapper.readValue(stream, Manifest.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read manifest: " + path, e);
        }
    }

    private List<CardDef> readCardDefs(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            return mapper.readValue(stream, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to read card file: " + path, e);
        }
    }
}
