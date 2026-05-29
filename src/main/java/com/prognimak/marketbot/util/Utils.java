package com.prognimak.marketbot.util;

import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.user.model.UserAlertSettings;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
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

    public static boolean shouldSendForUser(UserAlertSettings settings, double delta, double rollingDeltaSum) {
        log.info("Should Send ForUser message, delta: {}, rolling: {}", delta, rollingDeltaSum);
        return (Math.abs(delta) >= settings.deltaThreshold()
                || Math.abs(Utils.roundDouble(rollingDeltaSum, 2)) >= settings.rollingThreshold()) &&
                ((delta > 0 && rollingDeltaSum >0) || (delta < 0 && rollingDeltaSum <0));
    }


}
