package com.prognimak.marketbot;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.config.HistoryBackfillProperties;
import com.prognimak.marketbot.config.TrendProperties;
import com.prognimak.marketbot.dashboard.config.MarketDashboardProperties;
import com.prognimak.marketbot.security.AppSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        AppProperties.class,
        MarketDashboardProperties.class,
        HistoryBackfillProperties.class,
        TrendProperties.class,
        AppSecurityProperties.class
})
public class MarketBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketBotApplication.class, args);
    }

}
