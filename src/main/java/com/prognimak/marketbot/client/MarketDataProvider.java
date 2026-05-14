package com.prognimak.marketbot.client;

import com.prognimak.marketbot.model.Quote;

/**
 *
 */
public interface MarketDataProvider {
    Quote getQuote(String symbol);
}
