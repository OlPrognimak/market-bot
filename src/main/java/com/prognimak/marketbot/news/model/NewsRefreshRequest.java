package com.prognimak.marketbot.news.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NewsRefreshRequest(
        @NotNull InstrumentType instrumentType,
        @NotBlank String symbol
) {
}
