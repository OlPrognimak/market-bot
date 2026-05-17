package com.prognimak.marketbot.util;

import com.prognimak.marketbot.model.Quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class Utils {

    public static double roundDouble(final double value, final int scale) {

        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     *
     * @param quotes
     * @return rolling quotas changes
     */
    public static double calculateRollingChanges(List<Quote> quotes) {
        double rollingChange = 0.0;
        for(int i = 0; i < quotes.size()-1; i++) {
            Quote quote = quotes.get(i);
            rollingChange = rollingChange + (quote.percentChange() - quotes.get(i + 1).percentChange())*-1;
        }

        return rollingChange;
    }

}
