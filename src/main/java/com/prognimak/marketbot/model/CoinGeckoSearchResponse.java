package com.prognimak.marketbot.model;

import java.util.List;

public record CoinGeckoSearchResponse(
        List<Coin> coins
) {
    public record Coin(
            String id,
            String name,
            String symbol
    ) {
    }
}
