package com.prognimak.marketbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

@Configuration
public class NotificationExecutorConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService notificationExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("notification-", 0)
                .factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }

    @Bean
    public Semaphore notificationSendSemaphore(AppProperties properties) {
        return new Semaphore(Math.max(1, properties.messageSender().maxConcurrentSends()));
    }
}
