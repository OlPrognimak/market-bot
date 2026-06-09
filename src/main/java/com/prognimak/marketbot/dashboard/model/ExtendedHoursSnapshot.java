package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;
import java.util.List;

public record ExtendedHoursSnapshot(Instant lastScanAt, List<ExtendedHoursResult> results) {
}
