package com.haloce.tcg.net;

import com.haloce.tcg.card.loader.CardRepository;
import com.haloce.tcg.deck.model.DeckDef;
import com.haloce.tcg.game.GameEngine;
import com.haloce.tcg.game.GameMode;
import com.haloce.tcg.game.GameStateManager;
import com.haloce.tcg.game.PlayerSeat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {
    private final CardRepository cardRepository;
    private final DeckDef templateDeck;
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    public RoomManager(CardRepository cardRepository, DeckDef templateDeck) {
        this.cardRepository = cardRepository;
        this.templateDeck = templateDeck;
    }

    public GameRoom createRoom(String roomId, GameMode mode, List<String> playerIds, Map<String, String> teamByPlayer) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId is required");
        }
        if (rooms.containsKey(roomId)) {
            throw new IllegalArgumentException("Room already exists: " + roomId);
        }
        if (playerIds == null || playerIds.size() < 2) {
            throw new IllegalArgumentException("At least 2 players are required to create room");
        }

        Set<String> uniq = Set.copyOf(playerIds);
        if (uniq.size() != playerIds.size()) {
            throw new IllegalArgumentException("playerIds contains duplicate values");
        }

        List<PlayerSeat> seats = new ArrayList<>();
        for (String playerId : playerIds) {
            String teamId = teamByPlayer == null ? playerId : teamByPlayer.getOrDefault(playerId, playerId);
            DeckDef playerDeck = new DeckDef(
                    templateDeck.deckId() + "-" + playerId,
                    playerId,
                    templateDeck.cards()
            );
            seats.add(new PlayerSeat(playerId, playerDeck, teamId, true));
        }

        GameEngine engine = new GameEngine(cardRepository);
        GameStateManager game = engine.initializeMatchWithSeats(mode, seats);
        GameRoom room = new GameRoom(roomId, game);
        rooms.put(roomId, room);
        return room;
    }

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public boolean removeRoom(String roomId) {
        return rooms.remove(roomId) != null;
    }

    public void removeRoomIfEmpty(String roomId) {
        if (roomId == null || roomId.isBlank() || "default".equalsIgnoreCase(roomId)) {
            return;
        }
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        if (room.remotePlayers().onlinePlayers().isEmpty()) {
            rooms.remove(roomId, room);
        }
    }

    public List<RoomSummary> listRooms() {
        List<RoomSummary> list = new ArrayList<>();
        for (GameRoom room : rooms.values()) {
            list.add(new RoomSummary(
                    room.roomId(),
                    room.game().gameMode().name(),
                    room.game().status().name(),
                    room.game().phase().name(),
                    room.remotePlayers().onlinePlayers().size(),
                    room.game().playerIds().size()
            ));
        }
        list.sort((a, b) -> a.roomId().compareToIgnoreCase(b.roomId()));
        return Collections.unmodifiableList(list);
    }

    public Map<String, GameRoom> snapshot() {
        return Map.copyOf(new LinkedHashMap<>(rooms));
    }
}
