package com.prognimak.marketbot.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExtendedHoursScannerServiceTest {

    @Test
    void scanIsScheduledWithConfiguredExtendedHoursPollInterval() throws NoSuchMethodException {
        Scheduled scheduled = ExtendedHoursScannerService.class.getMethod("scan").getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("${market-bot.extended-hours.poll-interval-ms}", scheduled.fixedDelayString());
    }
}
