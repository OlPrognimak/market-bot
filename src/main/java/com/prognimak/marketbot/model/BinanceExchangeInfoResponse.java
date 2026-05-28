package com.prognimak.marketbot.model;

import java.util.List;

public record BinanceExchangeInfoResponse(
        List<SymbolInfo> symbols
) {
    public record SymbolInfo(
            String symbol,
            String status,
            String baseAsset,
            String quoteAsset,
            Boolean isSpotTradingAllowed
    ) {
    }
}
