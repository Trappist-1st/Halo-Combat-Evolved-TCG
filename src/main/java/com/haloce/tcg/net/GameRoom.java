package com.haloce.tcg.net;

import com.haloce.tcg.game.GameStateManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GameRoom {
    private final String roomId;
    private final GameStateManager game;
    private final RemotePlayerRegistry remotePlayers;
    private final Instant createdAt;
    private final Map<String, LinkedHashMap<Long, NetResponse>> responseCacheByPlayer = new ConcurrentHashMap<>();

    public GameRoom(String roomId, GameStateManager game) {
        this.roomId = roomId;
        this.game = game;
        this.remotePlayers = new RemotePlayerRegistry();
        this.createdAt = Instant.now();
    }

    public String roomId() {
        return roomId;
    }

    public GameStateManager game() {
        return game;
    }

    public RemotePlayerRegistry remotePlayers() {
        return remotePlayers;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<NetResponse> cachedResponse(String playerId, Long seq) {
        if (playerId == null || seq == null) {
            return Optional.empty();
        }
        LinkedHashMap<Long, NetResponse> playerCache = responseCacheByPlayer.get(playerId);
        if (playerCache == null) {
            return Optional.empty();
        }
        synchronized (playerCache) {
            return Optional.ofNullable(playerCache.get(seq));
        }
    }

    public void rememberResponse(String playerId, Long seq, NetResponse response) {
        if (playerId == null || seq == null || response == null) {
            return;
        }
        LinkedHashMap<Long, NetResponse> playerCache = responseCacheByPlayer.computeIfAbsent(playerId,
                ignored -> new LinkedHashMap<>());
        synchronized (playerCache) {
            playerCache.put(seq, response);
            if (playerCache.size() > 128) {
                Long firstKey = playerCache.keySet().iterator().next();
                playerCache.remove(firstKey);
            }
        }
    }
}
