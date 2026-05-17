package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.FinnhubClient;
import com.prognimak.marketbot.client.TelegramClient;
import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.mapper.QuoteMapper;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.prognimak.marketbot.util.Utils.calculateRollingChanges;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MarketScannerService {
    private final QuoteRepository quoteRepository;
    private final FinnhubClient finnhubClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final TelegramClient telegramClient;
    private final AppProperties properties;
    private final QuoteMapper quoteMapper;

    private static final String TEXT_COLOR_RED = "\u001B[31m";
    private static final String TEXT_COLOR_BLUE = "\u001B[34m";
    private static final String TEXT_COLOR_NON = "\u001B[0m";

    private static final int MAX_ROLLING_SIZE = 5;

private static final Set<String> YAHOO_EU_SUFFIXES = Set.of(
            ".AS", ".AT", ".BE", ".BR", ".CO", ".DE", ".DU", ".F",
            ".HE", ".HM", ".IC", ".IR", ".L", ".LS", ".MC", ".MI",
            ".MU", ".OL", ".PA", ".PR", ".SG", ".ST", ".SW", ".VI",
            ".WA"
    );

    private final Map<String, Double> lastPercentMap = new ConcurrentHashMap<>();
    private final Map<String, Deque<Double>> deltaHistoryMap = new ConcurrentHashMap<>();

    /*That uses in case of using two providers.*/
    @SneakyThrows
    private  void getQuote(String symbol, AtomicReference<Quote>  atomicQuote) {
        try {
            Quote  quote = getQuoteForProvider(symbol);
            atomicQuote.set(quote);
        }catch(Exception e) {
            //if (e.getMessage().contains("529")) {
                log.error("Error getting quote for symbol {}", symbol, e);
                Thread.currentThread().sleep(10000);
                getQuote(symbol, atomicQuote);
            //}
        }
    }

    @Scheduled(fixedDelayString = "${market-bot.poll-interval-ms}")
    public void scanMarket() {
        log.info("Start scanning market...");
        for (Map.Entry<String, String> entry : properties.watchlist().entrySet()) {

            String symbol = entry.getKey();
            String companyName = entry.getValue();

            try {
                AtomicReference<Quote>  atomicQuote = new AtomicReference<>();
                getQuote(symbol, atomicQuote);
                Quote quote = atomicQuote.get();

                double currentPercent = quote.percentChange();
                Double lastPercent = lastPercentMap.get(symbol);

                if (lastPercent == null) {
                    lastPercentMap.put(symbol, currentPercent);
                    System.out.printf("%s (%s) initial value saved: %.2f%%%n", companyName, symbol, currentPercent);
                    QuoteEntity entity = quoteMapper.toEntity(quote);
                    entity.setDelta(quote.percentChange());
                    quoteRepository.save(entity);
                    continue;
                }

                lastPercentMap.put(symbol, currentPercent);

                Deque<Double> history = deltaHistoryMap.computeIfAbsent(
                        symbol,
                        key -> new ArrayDeque<>()
                );


                List<QuoteEntity> lastPersistedChanges =
                        quoteRepository.findBySymbolAndSendIsFalseOrderByIdDesc(
                                symbol, PageRequest.of(0, MAX_ROLLING_SIZE));
                if (lastPersistedChanges.isEmpty()) {
                    QuoteEntity entity = quoteMapper.toEntity(quote);
                    entity.setDelta(quote.percentChange());
                    quoteRepository.save(entity);
                    continue;
                }

                QuoteEntity latestPersistedChange = lastPersistedChanges.getFirst();

                List<Quote> quotes = quoteMapper.toQuotes(lastPersistedChanges);
                Collections.reverse(quotes);
                quotes.add(quote);

                double rollingDeltaSum = calculateRollingChanges(quotes);


                String color = rollingDeltaSum < 0?TEXT_COLOR_RED :TEXT_COLOR_NON;
                if(!color.equals(TEXT_COLOR_RED)) {
                    color = rollingDeltaSum > 0 ? TEXT_COLOR_BLUE : TEXT_COLOR_NON;
                }
                boolean exceedsMovementThreshold = Math.abs(rollingDeltaSum) >= properties.maximalDeltaPrice();


                QuoteEntity quoteEntity = quoteMapper.toEntity(quote);
                double delta = quote.percentChange() - latestPersistedChange.getPercentChange();
                quoteEntity.setDelta(delta);
                quoteRepository.save(quoteEntity);

                boolean haveSendFlag = false;
                if (exceedsMovementThreshold /*exceedsMovementThreshold && exceedsAbsoluteThreshold*/) {
                    System.out.printf(
                            color +
                                    "%s (%s) current: %.2f%% | previous: %.2f%% | delta: %.2f%% | rolling 5: %.2f%%"
                                    + TEXT_COLOR_NON
                                    + "%n",
                            companyName,
                            symbol,
                            currentPercent,
                            lastPercent,
                            delta,
                            rollingDeltaSum
                    );

                    telegramClient.sendMessage(
                            buildMessage(quote, companyName, delta, rollingDeltaSum)
                    );
                    quoteEntity.setSend(true);
                    lastPersistedChanges.forEach(q -> q.setSend(true));
                    haveSendFlag = true;
                    if (!history.isEmpty()) {
                        history.clear();
                    }
                }
                if(!haveSendFlag && lastPersistedChanges.size() >=  MAX_ROLLING_SIZE) {
                    //Can be set to true afater send message
                    lastPersistedChanges.stream().filter(
                            q ->q.isSend()==false).forEach(q -> q.setSend(true));
                }

               // Thread.sleep(500);
            } catch (Exception e) {
                System.err.println("Error checking " + companyName + " (" + symbol + "): " + e.getMessage());
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
        String icon = rollingDelta >= 0 ? "🟢" : "🔴";
        String direction = rollingDelta >= 0 ? "UP" : "DOWN";

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

    private Quote getQuoteForProvider(String symbol) {
        if (isYahooEuropeSymbol(symbol)) {
            return yahooFinanceClient.getQuote(symbol);
        }

        return yahooFinanceClient.getQuote(symbol);
    }

    private boolean isYahooEuropeSymbol(String symbol) {
        return YAHOO_EU_SUFFIXES.stream().anyMatch(symbol::endsWith);
    }
}
