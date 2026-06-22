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
    void mapsRevolutSafranTickerToParis() {
        assertEquals(List.of("SAF.PA"), PortfolioTickerAliases.marketCandidates("SEJ1"));
        assertEquals("SAF.PA", PortfolioTickerAliases.revolutMarketSymbol("SEJ1"));
        assertTrue(PortfolioTickerAliases.revolutCandidates("SAF.PA").contains("SEJ1"));
    }

    @Test
    void mapsTradeRepublicIsinsToYahooSymbols() {
        assertEquals("AAPL", PortfolioTickerAliases.tradeRepublicMarketSymbol("US0378331005"));
        assertEquals("MU", PortfolioTickerAliases.tradeRepublicMarketSymbol("US5951121038"));
        assertEquals("AIR.PA", PortfolioTickerAliases.tradeRepublicMarketSymbol("NL0000235190"));
        assertEquals("ENR.DE", PortfolioTickerAliases.tradeRepublicMarketSymbol("US82621A2033"));
        assertEquals("C8PX.DE", PortfolioTickerAliases.tradeRepublicMarketSymbol("IE0000ZL1RD2"));
        assertEquals("MRK.DE", PortfolioTickerAliases.tradeRepublicMarketSymbol("DE0006599905"));
        assertEquals("BSP.DE", PortfolioTickerAliases.tradeRepublicMarketSymbol("GB0002634946"));
        assertEquals("LSPD.TO", PortfolioTickerAliases.tradeRepublicMarketSymbol("CA53680V1076"));
        assertEquals("AXP", PortfolioTickerAliases.tradeRepublicMarketSymbol("US0258161092"));
        assertEquals("BAC", PortfolioTickerAliases.tradeRepublicMarketSymbol("US0605051046"));
        assertEquals("JPM", PortfolioTickerAliases.tradeRepublicMarketSymbol("US46625H1005"));
        assertEquals("PENG", PortfolioTickerAliases.tradeRepublicMarketSymbol("US7069151055"));
        assertEquals("RMBS", PortfolioTickerAliases.tradeRepublicMarketSymbol("US7509171069"));
        assertEquals("SPCX", PortfolioTickerAliases.tradeRepublicMarketSymbol("US84615Q1031"));
    }
}
