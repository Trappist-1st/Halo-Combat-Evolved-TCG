package com.haloce.tcg.card.runtime;

import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.model.CardDef;
import com.haloce.tcg.card.model.CardType;

import java.util.UUID;

public class TokenFactory {
    private final CardRepository cardRepository;

    public TokenFactory(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public CardInstance createToken(String tokenId, String ownerPlayerId, long sourceEventSequence, String sourceCardId) {
        CardDef tokenDef = cardRepository.get(tokenId);
        if (tokenDef == null) {
            throw new IllegalArgumentException("Unknown token id: " + tokenId);
        }
        if (tokenDef.cardType() != CardType.TOKEN) {
            throw new IllegalArgumentException("Card is not TOKEN type: " + tokenId);
        }

        return new CardInstance(
                UUID.randomUUID().toString(),
                tokenDef,
                ownerPlayerId,
                sourceEventSequence,
                sourceCardId
        );
    }
}
