package com.prognimak.marketbot.model;

public record TwelveDataResponse(

        String symbol,

        String name,

        String exchange,

        String currency,

        String close,

        String previous_close,

        String change,

        String percent_change

) {
}