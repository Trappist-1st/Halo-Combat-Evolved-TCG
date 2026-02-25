package com.haloce.tcg;

import com.haloce.tcg.card.loader.CardLoader;
import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.card.loader.SemanticValidator;
import com.haloce.tcg.deck.DeckLoader;
import com.haloce.tcg.deck.DeckValidator;
import com.haloce.tcg.deck.model.DeckDef;
import com.haloce.tcg.game.GameMode;
import com.haloce.tcg.net.NetworkGameServer;
import com.haloce.tcg.net.RoomManager;

import java.nio.file.Path;
import java.util.List;

public class App {
    public static void main(String[] args) {
        CardLoader cardLoader = new CardLoader(new SemanticValidator());
        CardRepository repository = cardLoader.loadFromResourceDir(Path.of("src/main/resources"));

        DeckLoader deckLoader = new DeckLoader();
        DeckDef deckDef = deckLoader.load(Path.of("src/main/resources/decks/p1_demo_deck.v1.json"));
        new DeckValidator().validate(deckDef, repository);

        System.out.println("Halo CE TCG initialized. Loaded cards: " + repository.size()
            + ", validated deck: " + deckDef.deckId());

        RoomManager roomManager = new RoomManager(repository, deckDef);
        roomManager.createRoom("default", GameMode.DUEL_1V1, List.of("P1", "P2"), null);
        System.out.println("Default room created: default (DUEL_1V1, players=P1,P2)");

        if (args.length > 0 && "--server".equalsIgnoreCase(args[0])) {
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 19110;
            NetworkGameServer server = new NetworkGameServer(port, roomManager);
            server.start();
            System.out.println("Network game server started at port " + port + ". Press Ctrl+C to stop.");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        } else {
            System.out.println("Use '--server [port]' to start room-based network service.");
        }
    }
}
