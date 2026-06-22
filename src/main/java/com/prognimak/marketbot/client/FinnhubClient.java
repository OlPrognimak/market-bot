package com.prognimak.marketbot.client;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.FinnhubQuoteResponse;
import com.prognimak.marketbot.system.entity.SystemCredentialType;
import com.prognimak.marketbot.system.service.SystemApiCredentialService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FinnhubClient implements MarketDataProvider {

    private final WebClient webClient;
    private final AppProperties properties;
    private final SystemApiCredentialService credentialService;

    public FinnhubClient(WebClient.Builder builder, AppProperties properties, SystemApiCredentialService credentialService) {
        this.webClient = builder
                .baseUrl("https://finnhub.io/api/v1")
                .build();
        this.properties = properties;
        this.credentialService = credentialService;
    }

    public Quote getQuote(String symbol) {
        FinnhubQuoteResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam("symbol", symbol)
                        .queryParam("token", apiKey())
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

    private String apiKey() {
        String activeCredential = credentialService.activeSecret(SystemCredentialType.MARKET_DATA_PROVIDER, "FINNHUB");
        return activeCredential == null || activeCredential.isBlank()
                ? properties.providers().finnhubApiKey()
                : activeCredential;
    }
}
