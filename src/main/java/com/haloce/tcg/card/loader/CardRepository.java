package com.haloce.tcg.card.loader;

import com.haloce.tcg.card.model.CardDef;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CardRepository {
    private final Map<String, CardDef> defsById = new HashMap<>();

    public void put(CardDef def) {
        if (def == null || def.id() == null || def.id().isBlank()) {
            throw new IllegalArgumentException("Card id is required");
        }
        if (defsById.putIfAbsent(def.id(), def) != null) {
            throw new IllegalStateException("Duplicate card id: " + def.id());
        }
    }

    public CardDef get(String id) {
        return defsById.get(id);
    }

    public boolean contains(String id) {
        return defsById.containsKey(id);
    }

    public Collection<CardDef> all() {
        return defsById.values();
    }

    public int size() {
        return defsById.size();
    }
}
