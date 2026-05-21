package com.prognimak.marketbot.model;

public record WatchlistItem(
        String symbol,
        String name,
        String region,
        String sector,
        String exchange,
        String currency,
        boolean enabled,
        WatchlistPriority priority
) {
    public WatchlistItem {
        if (priority == null) {
            priority = WatchlistPriority.NORMAL;
        }
    }

    public static WatchlistItem simple(String symbol, String name) {
        return new WatchlistItem(
                symbol,
                name,
                null,
                null,
                null,
                null,
                true,
                WatchlistPriority.NORMAL
        );
    }
}
