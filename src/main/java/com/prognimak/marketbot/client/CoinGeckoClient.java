package com.prognimak.marketbot.client;

import com.prognimak.marketbot.model.CoinGeckoCoinResponse;
import com.prognimak.marketbot.model.CoinGeckoSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CoinGeckoClient {
    private static final String BASE_URL = "https://api.coingecko.com/api/v3";

    private final WebClient.Builder builder;

    public List<CoinGeckoCoinResponse> coins() {
        return builder.build()
                .get()
                .uri(BASE_URL + "/coins/list")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "market-bot")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<CoinGeckoCoinResponse>>() {
                })
                .block();
    }

    public CoinGeckoSearchResponse search(String query) {
        return builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.coingecko.com")
                        .path("/api/v3/search")
                        .queryParam("query", query)
                        .build()
                )
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "market-bot")
                .retrieve()
                .bodyToMono(CoinGeckoSearchResponse.class)
                .block();
    }
}
