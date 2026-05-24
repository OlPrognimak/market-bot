package com.prognimak.marketbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("history-backfill")
@RequiredArgsConstructor
public class HistoryBackfillRunner implements CommandLineRunner {
    private final HistoryBackfillService historyBackfillService;
    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) {
        historyBackfillService.backfill();
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }
}
