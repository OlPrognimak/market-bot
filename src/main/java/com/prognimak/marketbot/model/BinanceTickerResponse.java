package com.prognimak.marketbot.model;

public record BinanceTickerResponse(
        String symbol,
        String quoteVolume
) {
}
