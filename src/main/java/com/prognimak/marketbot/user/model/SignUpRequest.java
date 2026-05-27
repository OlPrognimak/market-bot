package com.prognimak.marketbot.user.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SignUpRequest(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Size(min = 8, max = 120) String password,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Email @Size(max = 160) String email,
        Map<String, String> metadata,
        @Valid List<UserPropertyRequest> properties
) {
}
