package com.haloce.tcg.net;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RemotePlayerRegistry {
    private final Map<String, ClientSession> sessionsByPlayerId = new ConcurrentHashMap<>();

    public void bind(String playerId, ClientSession session) {
        sessionsByPlayerId.put(playerId, session);
    }

    public void unbind(ClientSession session) {
        sessionsByPlayerId.entrySet().removeIf(entry -> entry.getValue().equals(session));
    }

    public boolean isOnline(String playerId) {
        return sessionsByPlayerId.containsKey(playerId);
    }

    public Optional<ClientSession> sessionOf(String playerId) {
        return Optional.ofNullable(sessionsByPlayerId.get(playerId));
    }

    public Set<String> onlinePlayers() {
        return Set.copyOf(sessionsByPlayerId.keySet());
    }
}
