package com.haloce.tcg.net;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkGameClient implements Closeable {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong commandSeq = new AtomicLong(1);

    public NetworkGameClient(String host, int port) {
        try {
            this.socket = new Socket(host, port);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            readResponse();
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to " + host + ":" + port, e);
        }
    }

    public NetResponse join(String playerId) {
        return send("JOIN", Map.of("playerId", playerId));
    }

    public NetResponse listRooms() {
        return send("LIST_ROOMS", Map.of());
    }

    public NetResponse createRoom(String roomId, String mode, List<String> playerIds, Map<String, String> teamByPlayer) {
        return send("CREATE_ROOM", Map.of(
                "roomId", roomId,
                "mode", mode,
                "playerIds", playerIds,
                "teamByPlayer", teamByPlayer == null ? Map.of() : teamByPlayer
        ));
    }

    public NetResponse joinRoom(String roomId, String playerId) {
        return send("JOIN_ROOM", Map.of(
                "roomId", roomId,
                "playerId", playerId
        ));
    }

    public NetResponse state() {
        return send("STATE", Map.of());
    }

    public NetResponse hijack(String hijackerInstanceId, String targetVehicleInstanceId) {
        return send("HIJACK", Map.of(
                "hijackerInstanceId", hijackerInstanceId,
                "targetVehicleInstanceId", targetVehicleInstanceId
        ));
    }

    public NetResponse leaveRoom() {
        return send("LEAVE_ROOM", Map.of());
    }

    public NetResponse send(String type, Map<String, Object> payload) {
        try {
            Long seq = "STATE".equalsIgnoreCase(type) || "LIST_ROOMS".equalsIgnoreCase(type) || "PING".equalsIgnoreCase(type)
                    ? null
                    : commandSeq.getAndIncrement();
            writer.write(mapper.writeValueAsString(new NetCommand(type, payload, seq)));
            writer.newLine();
            writer.flush();
            return readResponse();
        } catch (IOException e) {
            throw new RuntimeException("Failed to send command: " + type, e);
        }
    }

    private NetResponse readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Connection closed by server");
        }
        return mapper.readValue(line, NetResponse.class);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
