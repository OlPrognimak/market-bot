package com.prognimak.marketbot.util;

import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.user.model.UserAlertSettings;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
public final class Utils {
    public static final String TEXT_COLOR_RED = "\u001B[31m";
    public static final String TEXT_COLOR_GREEN = "\u001B[32m";
    public static final String TEXT_COLOR_NON = "\u001B[0m";

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

    public static boolean shouldSendForUser(UserAlertSettings settings, double delta, double rollingDeltaSum) {
        log.info("Should Send ForUser message, delta: {}, rolling: {}", delta, rollingDeltaSum);
        return (Math.abs(delta) >= settings.deltaThreshold()
                || Math.abs(Utils.roundDouble(rollingDeltaSum, 2)) >= settings.rollingThreshold()) &&
                ((delta > 0 && rollingDeltaSum >0) || (delta < 0 && rollingDeltaSum <0));
    }



    public static String directionLabel(double value) {
        if (value > 0) {
            return "UP";
        }
        if (value < 0) {
            return "DOWN";
        }
        return "FLAT";
    }

    public static String colorFor(double value) {
        if (value > 0) {
            return TEXT_COLOR_GREEN;
        }
        if (value < 0) {
            return TEXT_COLOR_RED;
        }
        return "";
    }


    public static boolean hasQuoteChanged(Quote quote, QuoteEntity latestPersistedChange, double quoteChangeEpsilon) {
        return changed(quote.current(), latestPersistedChange.getCurrent(), quoteChangeEpsilon)
                || changed(quote.percentChange(), latestPersistedChange.getPercentChange(), quoteChangeEpsilon)
                || changed(quote.change(), latestPersistedChange.getChange(), quoteChangeEpsilon)
                || changed(quote.high(), latestPersistedChange.getHigh(), quoteChangeEpsilon)
                || changed(quote.low(), latestPersistedChange.getLow(), quoteChangeEpsilon)
                || changed(quote.open(), latestPersistedChange.getOpen(), quoteChangeEpsilon)
                || changed(quote.previousClose(), latestPersistedChange.getPreviousClose(), quoteChangeEpsilon);
    }

    public static boolean sameQuoteBaseline(QuoteEntity persisted, Quote quote, double quoteChangeEpsilon) {
        return changed(persisted.getPreviousClose(), quote.previousClose(), quoteChangeEpsilon) == false;
    }

    private static boolean changed(double left, double right, double quoteChangeEpsilon) {
        return Math.abs(left - right) >= quoteChangeEpsilon;
    }
}
