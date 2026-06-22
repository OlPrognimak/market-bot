package com.prognimak.marketbot.system.model;

import com.prognimak.marketbot.system.entity.SystemCredentialType;

public record SystemApiCredentialResponse(
        Long id,
        SystemCredentialType credentialType,
        String providerName,
        String displayName,
        String maskedSecret,
        boolean hasSecret,
        boolean enabled,
        boolean active,
        String description
) {
}
