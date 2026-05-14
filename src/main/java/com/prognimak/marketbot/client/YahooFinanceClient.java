package com.prognimak.marketbot.client;


import com.prognimak.marketbot.model.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class YahooFinanceClient implements MarketDataProvider {

    private final WebClient.Builder builder;


    public Quote getQuote(String symbol) {

        YahooResponse yahooResponse = builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("query1.finance.yahoo.com")
                        .path("/v10/finance/quote")
                        .queryParam("symbols", symbol)
                        .build()
                )
                .retrieve()
                .bodyToMono(YahooResponse.class)
                .block();

        YahooResponse.YahooQuote yahooQuote =
                yahooResponse.quoteResponse()
                        .result()
                        .getFirst();

        return mapYahooQuote(yahooQuote);
    }

    private Quote mapYahooQuote(YahooResponse.YahooQuote yahooQuote) {

        return new Quote(
                yahooQuote.symbol(),
                yahooQuote.regularMarketPrice(),
                yahooQuote.regularMarketChange(),
                yahooQuote.regularMarketChangePercent(),
                yahooQuote.regularMarketDayHigh(),
                yahooQuote.regularMarketDayLow(),
                yahooQuote.regularMarketOpen(),
                yahooQuote.regularMarketPreviousClose()
        );
    }
}
