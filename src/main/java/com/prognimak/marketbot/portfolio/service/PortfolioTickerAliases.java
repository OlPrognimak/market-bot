package com.prognimak.marketbot.portfolio.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Defines known symbol differences between Revolut transactions and market-data provider symbols.
 */
final class PortfolioTickerAliases {
    private static final Map<String, List<String>> REVOLUT_TO_MARKET = Map.of(
            "ABJ", List.of("ABBN.SW"),
            "ASME", List.of("ASML"),
            "SGM", List.of("STM"),
            "IRBTQ", List.of("IRBT"),
            "AIR1", List.of("AIR.PA"),
            "ENR1", List.of("ENR.DE"),
            "SEJ1", List.of("SAF.PA"),
            "XFB", List.of("XFAB.PA")
    );
    private static final Map<String, String> TRADE_REPUBLIC_ISIN_TO_MARKET = Map.ofEntries(
            Map.entry("US0378331005", "AAPL"),
            Map.entry("US5951121038", "MU"),
            Map.entry("FR0000121972", "SU.PA"),
            Map.entry("NL0000235190", "AIR.PA"),
            Map.entry("US20825C1045", "COP"),
            Map.entry("US5738741041", "MRVL"),
            Map.entry("US76206K1079", "RNMBY"),
            Map.entry("US82621A2033", "ENR.DE"),
            Map.entry("US0727303028", "BAYRY"),
            Map.entry("US11135F1012", "AVGO"),
            Map.entry("US1667641005", "CVX"),
            Map.entry("US67066G1040", "NVDA"),
            Map.entry("DE0007236101", "SIE.DE"),
            Map.entry("DE0006599905", "MRK.DE"),
            Map.entry("US0605051046", "BAC"),
            Map.entry("US46625H1005", "JPM"),
            Map.entry("US7509171069", "RMBS"),
            Map.entry("US84615Q1031", "SPCX"),
            Map.entry("IE0000ZL1RD2", "C8PX.DE")
    );

    private PortfolioTickerAliases() {
    }

    static List<String> marketCandidates(String revolutTicker) {
        return REVOLUT_TO_MARKET.getOrDefault(normalize(revolutTicker), List.of());
    }

    static List<String> revolutCandidates(String marketTicker) {
        String normalizedMarketTicker = normalize(marketTicker);
        String baseTicker = baseTicker(normalizedMarketTicker);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalizedMarketTicker);
        candidates.add(baseTicker);
        REVOLUT_TO_MARKET.forEach((revolutTicker, marketTickers) -> {
            if (marketTickers.stream().map(PortfolioTickerAliases::normalize)
                    .anyMatch(candidate -> candidate.equals(normalizedMarketTicker)
                            || baseTicker(candidate).equals(baseTicker))) {
                candidates.add(revolutTicker);
            }
        });
        return List.copyOf(candidates);
    }

    static String tradeRepublicMarketSymbol(String isin) {
        String normalizedIsin = normalize(isin);
        return TRADE_REPUBLIC_ISIN_TO_MARKET.getOrDefault(normalizedIsin, normalizedIsin);
    }

    private static String baseTicker(String ticker) {
        int suffixIndex = ticker.indexOf('.');
        return suffixIndex < 0 ? ticker : ticker.substring(0, suffixIndex);
    }

    private static String normalize(String ticker) {
        return ticker.trim().toUpperCase(Locale.ROOT);
    }
}
