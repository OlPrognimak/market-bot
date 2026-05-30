package com.prognimak.marketbot.client;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.FinnhubQuoteResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FinnhubClient implements MarketDataProvider {

    private final WebClient webClient;
    private final AppProperties properties;

    public FinnhubClient(WebClient.Builder builder, AppProperties properties) {
        this.webClient = builder
                .baseUrl("https://finnhub.io/api/v1")
                .build();
        this.properties = properties;
    }

    public Quote getQuote(String symbol) {
        FinnhubQuoteResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam("symbol", symbol)
                        .queryParam("token", properties.providers().finnhubApiKey())
                        .build())
                .retrieve()
                .bodyToMono(FinnhubQuoteResponse.class)
                .block();

        if (response == null) {
            throw new IllegalStateException("No quote data for symbol: " + symbol);
        }

        return new Quote(
                symbol,
                response.c(),
                response.d(),
                response.dp(),
                response.h(),
                response.l(),
                response.o(),
                response.pc()
        );
    }
}
