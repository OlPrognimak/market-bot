package com.prognimak.marketbot.system.model;

import com.prognimak.marketbot.system.entity.SystemCredentialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SystemApiCredentialRequest(
        @NotNull SystemCredentialType credentialType,
        @NotBlank String providerName,
        @NotBlank String displayName,
        String secretValue,
        boolean enabled,
        boolean active,
        String description
) {
}
