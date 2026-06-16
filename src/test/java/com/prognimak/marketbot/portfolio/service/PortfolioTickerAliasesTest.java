package com.prognimak.marketbot.portfolio.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioTickerAliasesTest {

    @Test
    void mapsRevolutXfbToXFabParis() {
        assertEquals(List.of("XFAB.PA"), PortfolioTickerAliases.marketCandidates("XFB"));
    }

    @Test
    void mapsXFabParisChartBackToRevolutXfbTransactions() {
        assertTrue(PortfolioTickerAliases.revolutCandidates("XFAB.PA").contains("XFB"));
    }

    @Test
    void mapsTradeRepublicIsinsToYahooSymbols() {
        assertEquals("AAPL", PortfolioTickerAliases.tradeRepublicMarketSymbol("US0378331005"));
        assertEquals("MU", PortfolioTickerAliases.tradeRepublicMarketSymbol("US5951121038"));
        assertEquals("AIR.PA", PortfolioTickerAliases.tradeRepublicMarketSymbol("NL0000235190"));
        assertEquals("ENR.DE", PortfolioTickerAliases.tradeRepublicMarketSymbol("US82621A2033"));
        assertEquals("C8PX.DE", PortfolioTickerAliases.tradeRepublicMarketSymbol("IE0000ZL1RD2"));
    }
}
