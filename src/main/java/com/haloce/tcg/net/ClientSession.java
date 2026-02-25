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

public class ClientSession implements Closeable {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final ObjectMapper mapper;
    private volatile String boundPlayerId;
    private volatile String boundRoomId;

    public ClientSession(Socket socket, ObjectMapper mapper) throws IOException {
        this.socket = socket;
        this.mapper = mapper;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public String readLine() throws IOException {
        return reader.readLine();
    }

    public synchronized void send(NetResponse response) throws IOException {
        writer.write(mapper.writeValueAsString(response));
        writer.newLine();
        writer.flush();
    }

    public String boundPlayerId() {
        return boundPlayerId;
    }

    public void bindPlayerId(String playerId) {
        this.boundPlayerId = playerId;
    }

    public String boundRoomId() {
        return boundRoomId;
    }

    public void bindRoomId(String roomId) {
        this.boundRoomId = roomId;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
