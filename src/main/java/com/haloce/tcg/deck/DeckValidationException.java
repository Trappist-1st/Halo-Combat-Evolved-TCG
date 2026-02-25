package com.haloce.tcg.deck;

import java.util.List;

public class DeckValidationException extends RuntimeException {
    private final List<String> errors;

    public DeckValidationException(List<String> errors) {
        super("Deck validation failed: " + String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> errors() {
        return errors;
    }
}
