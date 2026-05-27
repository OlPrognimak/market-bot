package com.prognimak.marketbot.user.model;

import com.prognimak.marketbot.security.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        String email,
        UserRole role,
        boolean enabled,
        Map<String, String> metadata,
        List<UserPropertyResponse> properties,
        Instant created,
        Instant modified
) {
}
