package com.prognimak.marketbot.user.model;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
