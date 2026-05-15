package com.prognimak.marketbot.client;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.TwelveDataResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class TwellveClient {

    private final WebClient.Builder builder;
    private final AppProperties properties;

    public Quote getQuote(String symbol) {

        TwelveDataResponse response =
                builder.build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("api.twelvedata.com")
                                .path("/quote")
                                .queryParam("symbol", symbol)
                                .queryParam(
                                        "apikey",
                                        properties.twelveDataApiKey()
                                )
                                .build()
                        )
                        .retrieve()
                        .bodyToMono(TwelveDataResponse.class)
                        .block();

        if (response == null || response.close() == null) {
            throw new RuntimeException(
                    "No TwelveData data for symbol: "
                            + symbol
            );
        }

        return map(response);
    }

    private Quote map(TwelveDataResponse r) {
        double current = Double.parseDouble(r.close());
        double previous = Double.parseDouble(r.previous_close());
        double change = Double.parseDouble(r.change());
        double percent = Double.parseDouble(r.percent_change());

        return new Quote(
                r.symbol(),
                current,
                change,
                percent,
                current,
                current,
                current,
                previous
        );
    }
}