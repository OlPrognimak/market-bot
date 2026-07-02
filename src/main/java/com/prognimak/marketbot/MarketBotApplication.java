package com.prognimak.marketbot;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.config.HistoryBackfillProperties;
import com.prognimak.marketbot.config.TrendProperties;
import com.prognimak.marketbot.dashboard.config.MarketDashboardProperties;
import com.prognimak.marketbot.security.AppSecurityProperties;
import com.prognimak.marketbot.trading.config.TradingProperties;
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
        AppSecurityProperties.class,
        TradingProperties.class
})
public class MarketBotApplication {

    public static void main(String[] args) {
        System.setProperty(
                "java.net.preferIPv6Addresses",
                System.getProperty("java.net.preferIPv6Addresses", "true")
        );
        SpringApplication.run(MarketBotApplication.class, args);
    }

}
