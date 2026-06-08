package com.prognimak.marketbot.news.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class NewsRefreshExecutor {
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("news-refresh-", 0).factory()
    );

    public void execute(Runnable task) {
        executor.execute(task);
    }

    @PreDestroy
    void close() {
        executor.close();
    }
}
