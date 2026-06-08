package com.prognimak.marketbot.news.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NewsResearchRequest(
        @NotNull InstrumentType instrumentType,
        @NotBlank String symbol,
        @NotNull NewsResearchLayer layer
) {
}
