package com.prognimak.marketbot;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.config.HistoryBackfillProperties;
import com.prognimak.marketbot.dashboard.config.MarketDashboardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, MarketDashboardProperties.class, HistoryBackfillProperties.class})
public class MarketBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketBotApplication.class, args);
    }

}
