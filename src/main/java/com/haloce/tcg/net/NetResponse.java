package com.haloce.tcg.net;

public record NetResponse(
        String type,
        boolean ok,
        String message,
        Object data
) {
    public static NetResponse ok(String type, Object data) {
        return new NetResponse(type, true, null, data);
    }

    public static NetResponse error(String type, String message) {
        return new NetResponse(type, false, message, null);
    }
}
