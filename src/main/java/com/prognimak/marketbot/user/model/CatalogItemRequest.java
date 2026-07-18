package com.prognimak.marketbot.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogItemRequest(
        @NotBlank @Size(max = 40) String symbol,
        @Size(max = 160) String name,
        @Size(max = 80) String region,
        @Size(max = 120) String sector,
        @Size(max = 80) String exchange,
        @Size(max = 20) String currency,
        Boolean enabled,
        String priority
) {
}
