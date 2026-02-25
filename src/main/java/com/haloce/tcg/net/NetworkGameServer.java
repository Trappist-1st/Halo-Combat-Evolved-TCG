package com.haloce.tcg.net;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haloce.tcg.game.GameMode;
import com.haloce.tcg.game.GameRow;
import com.haloce.tcg.game.Lane;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkGameServer {
    private final int port;
    private final RoomManager roomManager;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GameSnapshotFactory snapshotFactory = new GameSnapshotFactory();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private volatile boolean running;
    private ServerSocket serverSocket;

    public NetworkGameServer(int port, RoomManager roomManager) {
        this.port = port;
        this.roomManager = roomManager;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            serverSocket = new ServerSocket(port);
            running = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start server at port " + port, e);
        }

        pool.submit(this::acceptLoop);
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientSession session = new ClientSession(socket, mapper);
                pool.submit(() -> handleSession(session));
            } catch (IOException e) {
                if (!running) {
                    return;
                }
            }
        }
    }

    private void handleSession(ClientSession session) {
        try (session) {
            session.send(NetResponse.ok("WELCOME", Map.of(
                    "rooms", roomManager.listRooms(),
                    "protocolVersion", "1.1"
            )));

            String line;
            while ((line = session.readLine()) != null) {
                NetResponse response = processCommand(session, line);
                session.send(response);
                if (response.ok()) {
                    broadcastState(session.boundRoomId());
                }
            }
        } catch (Exception ignored) {
        } finally {
            unbindSession(session);
        }
    }

    private NetResponse processCommand(ClientSession session, String line) {
        try {
            NetCommand command = mapper.readValue(line, NetCommand.class);
            String type = normalize(command.type());
            Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();
            Long seq = command.seq();

            return switch (type) {
                case "PING" -> NetResponse.ok("PONG", Map.of("rooms", roomManager.listRooms().size()));
                case "LIST_ROOMS" -> NetResponse.ok("LIST_ROOMS", roomManager.listRooms());
                case "CREATE_ROOM" -> createRoom(payload);
                case "JOIN_ROOM" -> joinRoom(session, payload);
                case "RECONNECT" -> joinRoom(session, payload);
                case "LEAVE_ROOM" -> leaveRoom(session);
                case "JOIN" -> joinDefaultRoom(session, payload);
                case "STATE" -> stateOfBoundRoom(session);
                default -> routeRoomCommand(session, type, payload, seq);
            };
        } catch (Exception e) {
            return NetResponse.error("ERROR", e.getMessage());
        }
    }

    private NetResponse createRoom(Map<String, Object> payload) {
        String roomId = requireString(payload, "roomId");
        String modeRaw = payload.getOrDefault("mode", "DUEL_1V1").toString();
        GameMode mode = GameMode.valueOf(modeRaw.toUpperCase(Locale.ROOT));

        List<String> playerIds = parsePlayerIds(payload.get("playerIds"));
        Map<String, String> teamByPlayer = parseTeamByPlayer(payload.get("teamByPlayer"));

        GameRoom room = roomManager.createRoom(roomId, mode, playerIds, teamByPlayer);
        return NetResponse.ok("CREATE_ROOM", Map.of(
                "roomId", room.roomId(),
                "mode", room.game().gameMode().name(),
                "players", room.game().playerIds()
        ));
    }

    private NetResponse joinRoom(ClientSession session, Map<String, Object> payload) {
        String roomId = requireString(payload, "roomId");
        String playerId = requireString(payload, "playerId");

        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return NetResponse.error("JOIN_ROOM", "Unknown roomId: " + roomId);
        }
        if (!room.game().playerIds().contains(playerId)) {
            return NetResponse.error("JOIN_ROOM", "Player is not seated in this room: " + playerId);
        }

        unbindSession(session);
        session.bindRoomId(roomId);
        session.bindPlayerId(playerId);
        room.remotePlayers().bind(playerId, session);

        return NetResponse.ok("JOIN_ROOM", Map.of(
                "roomId", roomId,
                "playerId", playerId,
                "onlinePlayers", room.remotePlayers().onlinePlayers()
        ));
    }

    private NetResponse joinDefaultRoom(ClientSession session, Map<String, Object> payload) {
        String playerId = requireString(payload, "playerId");
        List<RoomSummary> rooms = roomManager.listRooms();
        if (rooms.isEmpty()) {
            return NetResponse.error("JOIN", "No rooms available. Create one first.");
        }
        String firstRoomId = rooms.get(0).roomId();
        return joinRoom(session, Map.of("roomId", firstRoomId, "playerId", playerId));
    }

    private NetResponse stateOfBoundRoom(ClientSession session) {
        GameRoom room = requireBoundRoom(session);
        return NetResponse.ok("STATE", Map.of(
                "roomId", room.roomId(),
                "snapshot", snapshotFactory.create(room.game())
        ));
    }

    private NetResponse leaveRoom(ClientSession session) {
        String roomId = session.boundRoomId();
        String playerId = session.boundPlayerId();
        if (roomId == null || roomId.isBlank()) {
            return NetResponse.ok("LEAVE_ROOM", Map.of("left", false));
        }

        unbindSession(session);
        return NetResponse.ok("LEAVE_ROOM", Map.of(
                "left", true,
                "roomId", roomId,
                "playerId", playerId
        ));
    }

    private NetResponse routeRoomCommand(ClientSession session, String type, Map<String, Object> payload, Long seq) {
        GameRoom room = requireBoundRoom(session);

        String boundActor = session.boundPlayerId();
        if (seq != null && boundActor != null) {
            var cached = room.cachedResponse(boundActor, seq);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        NetResponse response;
        synchronized (room.game()) {
            response = switch (type) {
                case "ADVANCE_PHASE" -> {
                    room.game().advancePhase();
                    yield NetResponse.ok("ADVANCE_PHASE", roomSnapshot(room));
                }
                case "END_TURN" -> {
                    room.game().endTurn();
                    yield NetResponse.ok("END_TURN", roomSnapshot(room));
                }
                case "DEPLOY" -> {
                    String actor = requireActor(session);
                    String cardInstanceId = requireString(payload, "cardInstanceId");
                    Lane lane = Lane.valueOf(requireString(payload, "lane").toUpperCase(Locale.ROOT));
                    GameRow row = GameRow.valueOf(requireString(payload, "row").toUpperCase(Locale.ROOT));
                    room.game().deployUnitFromHand(actor, cardInstanceId, lane, row);
                    yield NetResponse.ok("DEPLOY", roomSnapshot(room));
                }
                case "CONVERT_BATTERY" -> {
                    String actor = requireActor(session);
                    String cardInstanceId = requireString(payload, "cardInstanceId");
                    room.game().convertToBattery(actor, cardInstanceId);
                    yield NetResponse.ok("CONVERT_BATTERY", roomSnapshot(room));
                }
                case "ATTACK" -> {
                    String attacker = requireString(payload, "attackerInstanceId");
                    String defender = requireString(payload, "defenderInstanceId");
                    var result = room.game().declareAttack(attacker, defender);
                    yield NetResponse.ok("ATTACK", Map.of(
                            "roomId", room.roomId(),
                            "result", result,
                            "snapshot", snapshotFactory.create(room.game())
                    ));
                }
                case "ATTACK_BASE" -> {
                    String attacker = requireString(payload, "attackerInstanceId");
                    String targetPlayerId = requireString(payload, "targetPlayerId");
                    room.game().attackBase(attacker, targetPlayerId);
                    yield NetResponse.ok("ATTACK_BASE", roomSnapshot(room));
                }
                case "HIJACK" -> {
                    String actorId = requireActor(session);
                    String hijacker = requireString(payload, "hijackerInstanceId");
                    String targetVehicle = requireString(payload, "targetVehicleInstanceId");
                    room.game().hijackVehicle(hijacker, targetVehicle);
                    yield NetResponse.ok("HIJACK", Map.of(
                            "roomId", room.roomId(),
                            "actor", actorId,
                            "snapshot", snapshotFactory.create(room.game())
                    ));
                }
                default -> NetResponse.error("ERROR", "Unknown command type: " + type);
            };
        }

        if (seq != null && boundActor != null && response.ok()) {
            room.rememberResponse(boundActor, seq, response);
        }
        return response;
    }

    private Map<String, Object> roomSnapshot(GameRoom room) {
        return Map.of(
                "roomId", room.roomId(),
                "snapshot", snapshotFactory.create(room.game())
        );
    }

    private void broadcastState(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }

        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return;
        }

        NetResponse state = NetResponse.ok("STATE_BROADCAST", roomSnapshot(room));
        for (String playerId : room.remotePlayers().onlinePlayers()) {
            room.remotePlayers().sessionOf(playerId).ifPresent(session -> {
                try {
                    session.send(state);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private void unbindSession(ClientSession session) {
        String roomId = session.boundRoomId();
        if (roomId != null) {
            GameRoom room = roomManager.getRoom(roomId);
            if (room != null) {
                room.remotePlayers().unbind(session);
            }
            roomManager.removeRoomIfEmpty(roomId);
        }
        session.bindRoomId(null);
        session.bindPlayerId(null);
    }

    private GameRoom requireBoundRoom(ClientSession session) {
        String roomId = session.boundRoomId();
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalStateException("Session is not bound to a room. Call JOIN_ROOM first.");
        }
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            throw new IllegalStateException("Bound room no longer exists: " + roomId);
        }
        return room;
    }

    private static String requireActor(ClientSession session) {
        String actor = session.boundPlayerId();
        if (actor == null || actor.isBlank()) {
            throw new IllegalStateException("Session is not bound to a player. Call JOIN_ROOM first.");
        }
        return actor;
    }

    private static String requireString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Missing required string field: " + key);
        }
        return s;
    }

    private static List<String> parsePlayerIds(Object raw) {
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new IllegalArgumentException("playerIds must be a non-empty array");
        }
        List<String> ids = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof String id) || id.isBlank()) {
                throw new IllegalArgumentException("playerIds contains invalid value");
            }
            ids.add(id);
        }
        return ids;
    }

    private static Map<String, String> parseTeamByPlayer(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                continue;
            }
            if (!(entry.getValue() instanceof String value) || value.isBlank()) {
                continue;
            }
            map.put(key, value);
        }
        return map;
    }

    private static String normalize(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }
}
