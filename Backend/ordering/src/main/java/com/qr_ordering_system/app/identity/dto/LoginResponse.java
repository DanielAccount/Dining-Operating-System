package com.qr_ordering_system.app.identity.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        MeResponse user
) {
    public LoginResponse(String accessToken, long expiresIn, MeResponse user) {
        this(accessToken, "Bearer", expiresIn, user);
    }
}
