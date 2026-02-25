package com.haloce.tcg.net;

public record RoomSummary(
        String roomId,
        String mode,
        String status,
        String phase,
        int onlinePlayers,
        int seatCount
) {
}
