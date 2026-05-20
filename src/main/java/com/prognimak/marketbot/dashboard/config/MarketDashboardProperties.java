package com.prognimak.marketbot.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-bot.dashboard")
public record MarketDashboardProperties(
        boolean enabled,
        boolean websocketEnabled,
        ScanTriggerMode scanTriggerMode,
        int maxResults
) {
    public MarketDashboardProperties {
        if (scanTriggerMode == null) {
            scanTriggerMode = ScanTriggerMode.BACKEND_SCHEDULED;
        }
        if (maxResults <= 0) {
            maxResults = 100;
        }
    }

    public boolean manualScanEnabled() {
        return scanTriggerMode == ScanTriggerMode.FRONTEND_TRIGGERED
                || scanTriggerMode == ScanTriggerMode.BOTH;
    }
}
