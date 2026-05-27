package com.prognimak.marketbot.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security")
public record AppSecurityProperties(
        @NotBlank String jwtSecret,
        @Positive long jwtExpirationMinutes,
        Admin admin
) {
    public record Admin(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String email,
            @NotBlank String displayName
    ) {
    }
}
