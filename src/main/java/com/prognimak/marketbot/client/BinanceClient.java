package com.prognimak.marketbot.client;

import com.prognimak.marketbot.model.BinanceExchangeInfoResponse;
import com.prognimak.marketbot.model.BinanceTickerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BinanceClient {
    private static final String BASE_URL = "https://api.binance.com";

    private final WebClient.Builder builder;

    public BinanceExchangeInfoResponse exchangeInfo() {
        return builder.build()
                .get()
                .uri(BASE_URL + "/api/v3/exchangeInfo")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "market-bot")
                .retrieve()
                .bodyToMono(BinanceExchangeInfoResponse.class)
                .block();
    }

    public BinanceExchangeInfoResponse exchangeInfo(String symbol) {
        return builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.binance.com")
                        .path("/api/v3/exchangeInfo")
                        .queryParam("symbol", symbol)
                        .build()
                )
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "market-bot")
                .retrieve()
                .bodyToMono(BinanceExchangeInfoResponse.class)
                .block();
    }

    public List<BinanceTickerResponse> tickers24h() {
        return builder.build()
                .get()
                .uri(BASE_URL + "/api/v3/ticker/24hr")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "market-bot")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<BinanceTickerResponse>>() {
                })
                .block();
    }

    public BinanceTickerResponse ticker24h(String symbol) {
        return builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.binance.com")
                        .path("/api/v3/ticker/24hr")
                        .queryParam("symbol", symbol)
                        .build()
                )
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "market-bot")
                .retrieve()
                .bodyToMono(BinanceTickerResponse.class)
                .block();
    }

    public List<List<Object>> klines(String symbol, String interval, int limit) {
        return builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.binance.com")
                        .path("/api/v3/klines")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("limit", limit)
                        .build()
                )
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "market-bot")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<List<Object>>>() {
                })
                .block();
    }

    public List<List<Object>> klines(String symbol, String interval, long startTime, int limit) {
        return builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.binance.com")
                        .path("/api/v3/klines")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("startTime", startTime)
                        .queryParam("limit", limit)
                        .build()
                )
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "market-bot")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<List<Object>>>() {
                })
                .block();
    }
}
