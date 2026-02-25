package com.haloce.tcg.deck;

import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.model.CardDef;
import com.haloce.tcg.card.model.CardType;
import com.haloce.tcg.deck.model.DeckDef;
import com.haloce.tcg.deck.model.DeckEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeckValidator {
    private final int requiredDeckSize;
    private final int defaultNameLimit;
    private final int legendaryLimit;

    public DeckValidator() {
        this(40, 3, 1);
    }

    public DeckValidator(int requiredDeckSize, int defaultNameLimit, int legendaryLimit) {
        this.requiredDeckSize = requiredDeckSize;
        this.defaultNameLimit = defaultNameLimit;
        this.legendaryLimit = legendaryLimit;
    }

    public void validate(DeckDef deck, CardRepository repository) {
        List<String> errors = new ArrayList<>();

        if (deck == null) {
            throw new DeckValidationException(List.of("Deck is null"));
        }
        if (deck.deckId() == null || deck.deckId().isBlank()) {
            errors.add("deckId is required");
        }
        if (deck.cards() == null || deck.cards().isEmpty()) {
            errors.add("Deck cards is empty");
            throwIfAny(errors);
            return;
        }

        int totalCards = 0;
        Set<String> seen = new HashSet<>();

        for (DeckEntry entry : deck.cards()) {
            if (entry == null || entry.id() == null || entry.id().isBlank()) {
                errors.add("Deck has entry with missing card id");
                continue;
            }
            if (entry.count() <= 0) {
                errors.add("Card " + entry.id() + " count must be > 0");
                continue;
            }
            if (!seen.add(entry.id())) {
                errors.add("Duplicate deck entry for card id: " + entry.id());
            }

            CardDef def = repository.get(entry.id());
            if (def == null) {
                errors.add("Unknown card id in deck: " + entry.id());
                continue;
            }

            if (def.cardType() == CardType.TOKEN) {
                errors.add("TOKEN card cannot be included in deck: " + entry.id());
                continue;
            }

            int nameLimit = def.deckLimit() == null ? defaultNameLimit : def.deckLimit();
            if (entry.count() > nameLimit) {
                errors.add("Card " + entry.id() + " exceeds per-name limit. count=" + entry.count() + ", limit=" + nameLimit);
            }

            if (Boolean.TRUE.equals(def.isLegendary()) && entry.count() > legendaryLimit) {
                errors.add("Legendary card " + entry.id() + " exceeds limit " + legendaryLimit);
            }

            totalCards += entry.count();
        }

        if (totalCards != requiredDeckSize) {
            errors.add("Deck must contain exactly " + requiredDeckSize + " cards, actual=" + totalCards);
        }

        throwIfAny(errors);
    }

    private static void throwIfAny(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new DeckValidationException(errors);
        }
    }
}
