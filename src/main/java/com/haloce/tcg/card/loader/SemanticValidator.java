package com.haloce.tcg.card.loader;

import com.haloce.tcg.card.model.Ability;
import com.haloce.tcg.card.model.CardDef;
import com.haloce.tcg.card.model.CardType;
import com.haloce.tcg.card.model.Effect;
import com.haloce.tcg.card.model.KeywordInstance;
import com.haloce.tcg.core.event.EventType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SemanticValidator {
    private static final Set<String> VALID_KEYWORDS = Set.of(
            "SHIELDED", "ARMOR", "EMP", "CAMO", "HIJACK", "PLASMA", "BALLISTIC",
            "HEADSHOT", "INFECT", "DROP_POD", "RANGED", "SENTINEL", "SQUAD", "VEHICLE",
            "ORBITAL_BATTERY", "POINT_DEFENSE", "SHIELD_HARDENING", "MULTI_SECTION", "TARGET_LINK"
    );

    private static final Set<String> VALID_EFFECT_TYPES = Set.of(
            "DEAL_DAMAGE", "HEAL", "DRAW", "ADD_KEYWORD", "REMOVE_KEYWORD", "APPLY_STATUS",
            "SUMMON_TOKEN", "MODIFY_DAMAGE", "MODIFY_ATK", "MODIFY_HP", "MODIFY_SHIELD_CAP",
            "DESTROY", "REDUCE_COST", "ROLL_MISS_CHANCE"
    );

    private static final Set<String> VALID_TRIGGERS = Arrays.stream(EventType.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet());

    public void validate(CardDef def) {
        require(def.id() != null && !def.id().isBlank(), "Card id is required");
        require(def.name() != null && !def.name().isBlank(), "Card name is required for " + def.id());
        require(def.cardType() != null, "Card type is required for " + def.id());
        require(def.cost() != null, "Cost is required for " + def.id());
        require(def.deckLimit() == null || def.deckLimit() >= 0, "Invalid deckLimit for " + def.id());

        if (Boolean.TRUE.equals(def.isLegendary())) {
            int limit = def.deckLimit() == null ? 1 : def.deckLimit();
            require(limit <= 1, "Legendary card must have deckLimit <= 1 for " + def.id());
        }

        if (def.cardType() == CardType.VESSEL) {
            require(def.vesselStats() != null, "vesselStats required for VESSEL " + def.id());
        } else {
            require(def.vesselStats() == null, "vesselStats only allowed for VESSEL " + def.id());
        }

        List<KeywordInstance> keywords = def.keywords();
        if (keywords != null) {
            for (KeywordInstance keyword : keywords) {
                require(keyword != null && keyword.name() != null, "Invalid keyword in " + def.id());
                require(VALID_KEYWORDS.contains(keyword.name()), "Unknown keyword " + keyword.name() + " in " + def.id());
            }
        }

        List<Ability> abilities = def.abilities();
        if (abilities != null) {
            Set<String> abilityIds = new HashSet<>();
            for (Ability ability : abilities) {
                require(ability != null && ability.id() != null && !ability.id().isBlank(), "Ability id required in " + def.id());
                require(abilityIds.add(ability.id()), "Duplicate ability id " + ability.id() + " in " + def.id());
                require(ability.trigger() != null && VALID_TRIGGERS.contains(ability.trigger()),
                        "Unknown trigger " + ability.trigger() + " in " + def.id());

                if (ability.effects() != null) {
                    for (Effect effect : ability.effects()) {
                        require(effect != null && effect.type() != null, "Invalid effect in " + def.id());
                        require(VALID_EFFECT_TYPES.contains(effect.type()),
                                "Unknown effect type " + effect.type() + " in " + def.id());
                    }
                }
            }
        }
    }

    public void validateCrossReferences(CardRepository repository) {
        for (CardDef def : repository.all()) {
            if (def.abilities() == null) {
                continue;
            }

            for (Ability ability : def.abilities()) {
                if (ability.effects() == null) {
                    continue;
                }

                for (Effect effect : ability.effects()) {
                    if (!Objects.equals(effect.type(), "SUMMON_TOKEN") || effect.params() == null) {
                        continue;
                    }

                    Object tokenIdObj = effect.params().get("tokenId");
                    if (!(tokenIdObj instanceof String tokenId)) {
                        throw new IllegalArgumentException("SUMMON_TOKEN missing tokenId in " + def.id());
                    }

                    CardDef tokenDef = repository.get(tokenId);
                    if (tokenDef == null) {
                        throw new IllegalArgumentException("Unknown tokenId " + tokenId + " referenced by " + def.id());
                    }
                    if (tokenDef.cardType() != CardType.TOKEN) {
                        throw new IllegalArgumentException("Referenced tokenId is not TOKEN type: " + tokenId);
                    }
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
