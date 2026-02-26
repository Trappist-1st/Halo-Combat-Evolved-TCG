package com.haloce.tcg.game;

import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.model.CardDef;
import com.haloce.tcg.card.model.CardType;
import com.haloce.tcg.card.model.Faction;
import com.haloce.tcg.card.runtime.CardInstance;
import com.haloce.tcg.core.event.DeterministicEventBus;
import com.haloce.tcg.core.event.EventBus;
import com.haloce.tcg.deck.DeckValidator;
import com.haloce.tcg.deck.model.DeckEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class GameEngine {
    private static final int DEFAULT_BASE_HEALTH = 30;

    private final CardRepository cardRepository;
    private final EventBus eventBus;
    private final DeckValidator deckValidator;
    private final Random random;

    public GameEngine(CardRepository cardRepository) {
        this(cardRepository, new DeterministicEventBus(), new DeckValidator(), new Random(20260225L));
    }

    public GameEngine(CardRepository cardRepository, EventBus eventBus, DeckValidator deckValidator, Random random) {
        this.cardRepository = cardRepository;
        this.eventBus = eventBus;
        this.deckValidator = deckValidator;
        this.random = random;
    }

    public GameStateManager initializeMatch(List<PlayerSetup> setups) {
        return initializeMatch(GameMode.DUEL_1V1, setups);
    }

    public GameStateManager initializeMatch(GameMode mode, List<PlayerSetup> setups) {
        if (setups == null || setups.size() < 2) {
            throw new IllegalArgumentException("At least 2 players are required");
        }

        List<PlayerSeat> seats = setups.stream()
                .map(setup -> new PlayerSeat(setup.playerId(), setup.deck(), setup.playerId(), false))
                .collect(Collectors.toList());
        return initializeMatchWithSeats(mode, seats);
    }

    public GameStateManager initializeMatchWithSeats(GameMode mode, List<PlayerSeat> seats) {
        if (seats == null || seats.size() < 2) {
            throw new IllegalArgumentException("At least 2 seats are required");
        }

        validateModeSetup(mode, seats);

        LinkedHashMap<String, PlayerState> playersById = new LinkedHashMap<>();
        Map<String, String> teamByPlayer = new LinkedHashMap<>();
        Map<String, Faction> factionByPlayer = new LinkedHashMap<>();
        for (PlayerSeat seat : seats) {
            if (seat == null || seat.playerId() == null || seat.playerId().isBlank()) {
                throw new IllegalArgumentException("Invalid player setup");
            }
            if (playersById.containsKey(seat.playerId())) {
                throw new IllegalArgumentException("Duplicate playerId: " + seat.playerId());
            }

            deckValidator.validate(seat.deck(), cardRepository);
            List<CardInstance> deckInstances = createDeckInstances(seat.playerId(), seat.deck().cards());
            playersById.put(seat.playerId(), new PlayerState(seat.playerId(), DEFAULT_BASE_HEALTH, deckInstances));
            factionByPlayer.put(seat.playerId(), inferDominantFaction(seat.deck().cards()));

            String teamId = seat.teamId();
            if (teamId == null || teamId.isBlank()) {
                teamId = seat.playerId();
            }
            teamByPlayer.put(seat.playerId(), teamId);
        }

        GameStateManager stateManager = new GameStateManager(
                eventBus,
                cardRepository,
                playersById,
                mode,
                teamByPlayer,
                factionByPlayer
        );
        stateManager.startGame();
        return stateManager;
    }

    private Faction inferDominantFaction(List<DeckEntry> entries) {
        Map<Faction, Integer> countByFaction = new LinkedHashMap<>();
        for (DeckEntry entry : entries) {
            CardDef def = cardRepository.get(entry.id());
            if (def == null || def.faction() == null) {
                continue;
            }
            countByFaction.merge(def.faction(), entry.count(), Integer::sum);
        }
        return countByFaction.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(Faction.NEUTRAL);
    }

    private static void validateModeSetup(GameMode mode, List<PlayerSeat> seats) {
        if (mode == GameMode.DUEL_1V1 && seats.size() != 2) {
            throw new IllegalArgumentException("DUEL_1V1 requires exactly 2 players");
        }
        if (mode == GameMode.TEAM_2V2 && seats.size() != 4) {
            throw new IllegalArgumentException("TEAM_2V2 requires exactly 4 players");
        }

        if (mode == GameMode.TEAM_2V2) {
            Map<String, Long> teamCounts = seats.stream()
                    .collect(Collectors.groupingBy(
                            seat -> seat.teamId() == null || seat.teamId().isBlank() ? "NO_TEAM" : seat.teamId(),
                            Collectors.counting()
                    ));
            if (teamCounts.size() != 2 || teamCounts.values().stream().anyMatch(count -> count != 2L)) {
                throw new IllegalArgumentException("TEAM_2V2 requires exactly 2 teams with 2 players each");
            }
        }
    }

    private List<CardInstance> createDeckInstances(String playerId, List<DeckEntry> entries) {
        List<CardInstance> instances = new ArrayList<>();
        for (DeckEntry entry : entries) {
            CardDef def = cardRepository.get(entry.id());
            if (def == null) {
                throw new IllegalArgumentException("Unknown card in deck: " + entry.id());
            }
            if (def.cardType() == CardType.TOKEN) {
                throw new IllegalArgumentException("TOKEN cannot be in deck: " + entry.id());
            }

            for (int i = 0; i < entry.count(); i++) {
                instances.add(new CardInstance(
                        UUID.randomUUID().toString(),
                        def,
                        playerId,
                        0L,
                        def.id()
                ));
            }
        }

        Collections.shuffle(instances, random);
        return instances;
    }
}
