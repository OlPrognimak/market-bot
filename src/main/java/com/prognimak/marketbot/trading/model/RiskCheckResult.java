package com.prognimak.marketbot.trading.model;

import java.util.List;

public record RiskCheckResult(
        boolean allowed,
        List<String> warnings,
        List<String> rejectionReasons
) {
    public static RiskCheckResult allowed(List<String> warnings) {
        return new RiskCheckResult(true, warnings, List.of());
    }

    public static RiskCheckResult rejected(List<String> warnings, List<String> rejectionReasons) {
        return new RiskCheckResult(false, warnings, rejectionReasons);
    }
}
