package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.FinnhubClient;
import com.prognimak.marketbot.client.TelegramClient;
import com.prognimak.marketbot.client.TwellveClient;
import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.model.Quote;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketScannerService {

    private final FinnhubClient finnhubClient;
    private final TwellveClient twellveClient;
    private final TelegramClient telegramClient;
    private final AppProperties properties;

    private static final double MAX_DELTA = 0.8;

    private final Map<String, Double> lastPercentMap = new ConcurrentHashMap<>();
    private final Map<String, Deque<Double>> deltaHistoryMap = new ConcurrentHashMap<>();

    @SneakyThrows
    private  Quote getQuote(String symbol) {
        try {
            return finnhubClient.getQuote(symbol);
        }catch(Exception e) {
            if (e.getMessage().contains("529"))
            log.error("Error getting quote for symbol {}", symbol, e);
            Thread.currentThread().sleep(5000);
            return getQuote(symbol);
        }
    }

    @Scheduled(fixedDelayString = "${market-bot.poll-interval-ms}")
    public void scanMarket() {
        log.info("Start scanning market...");
        for (Map.Entry<String, String> entry : properties.watchlist().entrySet()) {

            String symbol = entry.getKey();
            String companyName = entry.getValue();

            try {

                Quote quote;
                if(symbol.equals("BMW.DE") || symbol.equals("RHM.DE")) {
                   quote = twellveClient.getQuote(symbol);
                   // continue;
                } else {
                    quote = finnhubClient.getQuote(symbol);
                }

                Thread.currentThread().sleep(500);

                double currentPercent = quote.percentChange();
                Double lastPercent = lastPercentMap.get(symbol);

                if (lastPercent == null) {
                    lastPercentMap.put(symbol, currentPercent);
                    System.out.printf("%s (%s) initial value saved: %.2f%%%n", companyName, symbol, currentPercent);
                    continue;
                }

                double delta = currentPercent - lastPercent;
                lastPercentMap.put(symbol, currentPercent);

                Deque<Double> history = deltaHistoryMap.computeIfAbsent(
                        symbol,
                        key -> new ArrayDeque<>()
                );

                history.addLast(delta);

                if (history.size() > 5) {
                    history.removeFirst();
                }

                double rollingDelta = history.stream()
                        .mapToDouble(Double::doubleValue)
                        .sum();

                String color = "";

                if (delta < 0) {
                    color = "\u001B[31m"; // red
                } else if (delta > 0) {
                    color = "\u001B[34m"; // blue
                }

                String reset = "\u001B[0m";

                boolean exceedsMovementThreshold = Math.abs(rollingDelta) >= MAX_DELTA;
                boolean exceedsDeltaThreshold = Math.abs(delta) >= MAX_DELTA;

                boolean exceedsAbsoluteThreshold =
                        currentPercent <= properties.dropAlertPercent()
                                || currentPercent >= properties.riseAlertPercent();



                if (exceedsMovementThreshold && exceedsAbsoluteThreshold) {
                    telegramClient.sendMessage(
                            buildMessage(quote, companyName, delta, rollingDelta)
                    );
                    System.out.printf(
                            color +
                                    "%s (%s) current: %.2f%% | previous: %.2f%% | delta: %.2f%% | rolling 5: %.2f%%"
                                    + reset
                                    + "%n",
                            companyName,
                            symbol,
                            currentPercent,
                            lastPercent,
                            delta,
                            rollingDelta
                    );
                    if (!history.isEmpty()) {
                        history.clear();
                    }
                }
                Thread.sleep(100);
            } catch (Exception e) {
                System.err.println(
                        "Error checking "
                                + companyName
                                + " ("
                                + symbol
                                + "): "
                                + e.getMessage()
                );

            }
        }
        log.info("End scanning market...");
    }

    private String buildMessage(
            Quote quote,
            String companyName,
            double delta,
            double rollingDelta
    ) {
        String icon = delta >= 0 ? "🟢" : "🔴";
        String direction = delta >= 0 ? "UP" : "DOWN";

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return """
            %s %s
            
            %s (%s) %s
            
            Day change: %.2f%%
            Move since last check: %.2f%%
            Rolling move 5 checks: %.2f%%
            
            Price: $%.2f
            Today range: $%.2f - $%.2f
            Open: $%.2f
            Previous close: $%.2f
            """.formatted(
                timestamp,
                icon,
                companyName,
                quote.symbol(),
                direction,
                quote.percentChange(),
                delta,
                rollingDelta,
                quote.current(),
                quote.low(),
                quote.high(),
                quote.open(),
                quote.previousClose()
        );
    }
}
