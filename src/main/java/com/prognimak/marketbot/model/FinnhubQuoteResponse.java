package com.prognimak.marketbot.model;

public record FinnhubQuoteResponse(
        double c,  // current price
        double d,  // change
        double dp, // percent change
        double h,  // high
        double l,  // low
        double o,  // open
        double pc  // previous close
) {}
