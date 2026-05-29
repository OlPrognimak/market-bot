package com.prognimak.marketbot.util;

import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.user.model.UserAlertSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @Test
    void calculateRollingChanges() {
        List<Quote> quotes = List.of(
                Quote.builder().percentChange(10.0).build(),
                Quote.builder().percentChange(-5.00).build(),
                Quote.builder().percentChange(-2.50).build(),
                Quote.builder().percentChange(1.50).build(),
                Quote.builder().percentChange(25.00).build()
        );
        double rolling = Utils.calculateRollingChanges(quotes);
        assertEquals(15, rolling);
    }

    @Test
    void calculateRollingChangesEquals() {
        List<Quote> quotes = List.of(
                Quote.builder().percentChange(1.50).build(),
                Quote.builder().percentChange(1.50).build()
        );
        double rolling = Utils.calculateRollingChanges(quotes);
        assertEquals(0, rolling);
    }

    @Test
    void shouldSendForUser() {
        UserAlertSettings settings = new UserAlertSettings(3.00, 1.00);
        assertTrue(Utils.shouldSendForUser(settings, 1.5, 3.1));
        assertTrue(Utils.shouldSendForUser(settings, -1.5, -3.1));
        assertFalse(Utils.shouldSendForUser(settings, -1.5, 3.1));
        assertFalse(Utils.shouldSendForUser(settings, 1.5, -3.1));
        assertTrue(Utils.shouldSendForUser(settings, 0.08, 3.1));
        assertTrue(Utils.shouldSendForUser(settings, 1.1, 0.5));
        assertFalse(Utils.shouldSendForUser(settings, 0.08, 2.1));


    }
}