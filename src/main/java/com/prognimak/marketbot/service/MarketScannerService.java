package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.FinnhubClient;
import com.prognimak.marketbot.client.TelegramClient;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.model.Quote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MarketScannerService {

    private final FinnhubClient finnhubClient;
    private final TelegramClient telegramClient;
    private final AppProperties properties;

    private final Map<String, Double> lastAlerts = new ConcurrentHashMap<>();

    public MarketScannerService(FinnhubClient finnhubClient, TelegramClient telegramClient, AppProperties properties) {
        this.finnhubClient = finnhubClient;
        this.telegramClient = telegramClient;
        this.properties = properties;
    }

    private final Map<String, Double> lastPercentMap = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${market-bot.poll-interval-ms}")
    public void scanMarket() {
        log.info("Start scanning market...");
        for (String symbol : properties.watchlist()) {

            try {
                Quote quote = finnhubClient.getQuote(symbol);
                double currentPercent = quote.percentChange();
                Double lastPercent = lastPercentMap.get(symbol);

                // first observation
                if (lastPercent == null) {
                    lastPercentMap.put(symbol, currentPercent);
                    System.out.printf("%s initial value saved: %.2f%%%n", symbol, currentPercent);
                    continue;
                }
                // movement since previous saved value
                double delta = currentPercent - lastPercent;
                // save latest value
                lastPercentMap.put(symbol, currentPercent);
                System.out.printf(
                        "%s current: %.2f%% | previous: %.2f%% | delta: %.2f%%%n",
                        symbol, currentPercent, lastPercent, delta
                );

                boolean exceedsMovementThreshold = Math.abs(delta) >= 0.4;

                boolean exceedsAbsoluteThreshold =
                        currentPercent <= properties.dropAlertPercent()
                                || currentPercent >= properties.riseAlertPercent();

                // send alert only if:
                // 1. stock already significantly moved today
                // 2. AND additionally moved strongly since last check
                if (exceedsMovementThreshold && exceedsAbsoluteThreshold) {
                    String message = buildMessage(quote, delta);
                    String dateTime = LocalDateTime.now().toString();
                    telegramClient.sendMessage(dateTime + ": \n" + message);
                    System.out.println("+++++++ Message have sent to telegram");
                }

            } catch (Exception e) {
                System.err.println("Error checking " + symbol + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        log.info("Start scanning market...");
    }

    private String buildMessage(Quote quote, double delta) {
        String icon = delta >= 0 ? "🟢" : "🔴";

        String direction = delta >= 0 ? "UP" : "DOWN";
        return """
            %s %s %s
            
            Current day change: %.2f%%
            Move since last check: %.2f%%
            Price: $%.2f
            Today: $%.2f - $%.2f
            Open: $%.2f
            Prev close: $%.2f
            """.formatted(
                icon, quote.symbol(), direction, quote.percentChange(), delta, quote.current(),
                quote.low(), quote.high(), quote.open(), quote.previousClose()
        );
    }
}
