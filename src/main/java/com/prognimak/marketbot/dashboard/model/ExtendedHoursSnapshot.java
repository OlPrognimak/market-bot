package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExtendedHoursSnapshot(
        Instant lastScanAt,
        LocalDate dataDate,
        boolean fallback,
        List<ExtendedHoursResult> results
) {
}
