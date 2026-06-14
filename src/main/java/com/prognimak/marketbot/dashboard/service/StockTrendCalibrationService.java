package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.config.TrendProperties;
import com.prognimak.marketbot.config.TrendProperties.TrendCandidate;
import com.prognimak.marketbot.config.TrendProperties.TrendParameters;
import com.prognimak.marketbot.dashboard.model.MarketDirection;
import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.entity.StockCatalogEntity;
import com.prognimak.marketbot.entity.StockTrendProfileEntity;
import com.prognimak.marketbot.repository.QuoteRepository;
import com.prognimak.marketbot.repository.StockCatalogRepository;
import com.prognimak.marketbot.repository.StockTrendProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTrendCalibrationService {
    private final TrendProperties properties;
    private final StockCatalogRepository stockRepository;
    private final StockTrendProfileRepository profileRepository;
    private final QuoteRepository quoteRepository;
    private final MarketTrendService trendService;
    private final StockTrendProfileService profileService;

    @Scheduled(cron = "${market-bot.trend.recalibration-cron:0 15 2 * * *}",
            zone = "${market-bot.trend.recalibration-zone:Europe/Berlin}")
    @Transactional
    public void scheduledRecalibration() {
        if (properties.adaptiveEnabled()) {
            recalibrateAll();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recalibrateOnStartup() {
        if (properties.adaptiveEnabled() && properties.recalibrateOnStartup()) {
            recalibrateAll();
        }
    }

    public CalibrationSummary recalibrateAll() {
        int updated = 0;
        int insufficient = 0;
        int failed = 0;
        for (StockCatalogEntity stock : stockRepository.findByEnabledTrueOrderBySymbolAsc()) {
            try {
                CalibrationResult result = calibrate(stock);
                if (result == null) {
                    saveDefaultIfMissing(stock);
                    insufficient++;
                } else {
                    save(stock, result);
                    updated++;
                }
            } catch (Exception exception) {
                failed++;
                log.warn("Trend profile recalibration failed for {}: {}", stock.getSymbol(), exception.getMessage());
                log.debug("Trend profile recalibration failure for {}", stock.getSymbol(), exception);
            }
        }
        profileService.refreshCache();
        CalibrationSummary summary = new CalibrationSummary(updated, insufficient, failed);
        log.info("Trend profile recalibration completed: {}", summary);
        return summary;
    }

    private void saveDefaultIfMissing(StockCatalogEntity stock) {
        if (profileRepository.findByStockSymbolIgnoreCase(stock.getSymbol()).isPresent()) {
            return;
        }
        TrendParameters defaults = properties.defaults();
        Instant now = Instant.now();
        StockTrendProfileEntity profile = new StockTrendProfileEntity();
        profile.setStock(stock);
        profile.setProfileName(defaults.profileName());
        profile.setLongWeight(defaults.longWeight());
        profile.setShortWeight(defaults.shortWeight());
        profile.setMinimumScorePercent(defaults.minimumScorePercent());
        profile.setConfidence(0);
        profile.setQualityScore(0);
        profile.setSampleSize(0);
        profile.setTradingDays(0);
        profile.setMovementFrequency(0);
        profile.setReversalFrequency(0);
        profile.setContinuationRate(0);
        profile.setTypicalMovementPercent(0);
        profile.setCalculatedAt(now);
        profile.setValidUntil(now.plus(properties.maximumProfileAge()));
        profileRepository.save(profile);
    }

    CalibrationResult calibrate(StockCatalogEntity stock) {
        Instant from = Instant.now().minusSeconds(properties.effectiveLookbackDays() * 24L * 60 * 60);
        List<QuoteEntity> quotes = quoteRepository.findBySymbolAndCreatedGreaterThanEqualOrderByCreatedAsc(stock.getSymbol(), from);
        List<List<QuoteEntity>> sequences = validSequences(quotes);
        Set<LocalDate> tradingDays = new HashSet<>();
        sequences.forEach(sequence -> sequence.forEach(quote -> tradingDays.add(localDate(quote))));

        List<EvaluationWindow> windows = evaluationWindows(sequences);
        if (tradingDays.size() < properties.effectiveMinimumTradingDays()
                || windows.size() < properties.effectiveMinimumWindows()) {
            return null;
        }

        double typicalMovement = median(windows.stream().map(EvaluationWindow::absoluteLatestDelta).sorted().toList());
        double minimumScore = clamp(
                typicalMovement * properties.effectiveThresholdMultiplier(),
                properties.effectiveMinimumAdaptiveScorePercent(),
                properties.effectiveMaximumAdaptiveScorePercent()
        );
        List<CandidateScore> scores = TrendProperties.CANDIDATES.stream()
                .map(candidate -> score(candidate, windows, minimumScore))
                .sorted(Comparator.comparingDouble(CandidateScore::qualityScore).reversed())
                .toList();
        CandidateScore best = scores.getFirst();
        double confidence = Math.max(0, Math.min(1, best.accuracy()));

        int meaningful = 0;
        int continuation = 0;
        int reversal = 0;
        for (EvaluationWindow window : windows) {
            if (Math.abs(window.shortDelta()) < minimumScore || Math.abs(window.futureDelta()) < minimumScore) continue;
            meaningful++;
            if (Math.signum(window.shortDelta()) == Math.signum(window.futureDelta())) continuation++;
            else reversal++;
        }

        return new CalibrationResult(
                best.candidate().name(),
                best.candidate().longWeight(),
                best.candidate().shortWeight(),
                minimumScore,
                confidence,
                best.qualityScore(),
                windows.size(),
                tradingDays.size(),
                (double) meaningful / windows.size(),
                meaningful == 0 ? 0 : (double) reversal / meaningful,
                meaningful == 0 ? 0 : (double) continuation / meaningful,
                typicalMovement
        );
    }

    private CandidateScore score(TrendCandidate candidate, List<EvaluationWindow> windows, double minimumScore) {
        double shortWeight = clamp(candidate.shortWeight(), properties.effectiveMinimumShortWeight(), properties.effectiveMaximumShortWeight());
        TrendCandidate adjustedCandidate = new TrendCandidate(candidate.name(), 1 - shortWeight, shortWeight);
        TrendParameters parameters = new TrendParameters(
                adjustedCandidate.name(),
                adjustedCandidate.longWeight(),
                adjustedCandidate.shortWeight(),
                minimumScore,
                0
        );
        int correct = 0;
        int falseSignals = 0;
        int directional = 0;
        for (EvaluationWindow window : windows) {
            MarketDirection predicted = trendService.detect(window.observationPrices(), parameters, properties.effectiveShortWindow());
            MarketDirection actual = direction(window.futureDelta(), minimumScore);
            if (predicted == MarketDirection.NEUTRAL) continue;
            directional++;
            if (predicted == actual) correct++;
            else falseSignals++;
        }
        double accuracy = directional == 0 ? 0 : (double) correct / directional;
        double falseRate = directional == 0 ? 1 : (double) falseSignals / directional;
        return new CandidateScore(adjustedCandidate, accuracy - falseRate, accuracy);
    }

    private List<List<QuoteEntity>> validSequences(List<QuoteEntity> quotes) {
        List<List<QuoteEntity>> sequences = new ArrayList<>();
        List<QuoteEntity> current = new ArrayList<>();
        QuoteEntity previous = null;
        for (QuoteEntity quote : quotes) {
            if (quote.getCreated() == null || quote.getCurrent() <= 0) continue;
            if (previous != null && (!localDate(previous).equals(localDate(quote))
                    || Math.abs(previous.getPreviousClose() - quote.getPreviousClose()) > properties.effectivePreviousCloseTolerance())) {
                if (!current.isEmpty()) sequences.add(current);
                current = new ArrayList<>();
            }
            current.add(quote);
            previous = quote;
        }
        if (!current.isEmpty()) sequences.add(current);
        return sequences;
    }

    private List<EvaluationWindow> evaluationWindows(List<List<QuoteEntity>> sequences) {
        List<EvaluationWindow> windows = new ArrayList<>();
        int observationWindow = properties.effectiveObservationWindow();
        int horizon = properties.effectiveEvaluationHorizon();
        int shortWindow = properties.effectiveShortWindow();
        for (List<QuoteEntity> sequence : sequences) {
            for (int index = observationWindow - 1; index + horizon < sequence.size(); index++) {
                List<Double> prices = sequence.subList(index - observationWindow + 1, index + 1).stream()
                        .map(QuoteEntity::getCurrent).toList();
                double current = prices.getLast();
                double future = sequence.get(index + horizon).getCurrent();
                double previous = prices.get(prices.size() - 2);
                double shortStart = prices.get(Math.max(0, prices.size() - shortWindow));
                windows.add(new EvaluationWindow(
                        prices,
                        percentChange(shortStart, current),
                        percentChange(current, future),
                        Math.abs(percentChange(previous, current))
                ));
            }
        }
        return windows;
    }

    private void save(StockCatalogEntity stock, CalibrationResult result) {
        StockTrendProfileEntity profile = profileRepository.findByStockSymbolIgnoreCase(stock.getSymbol())
                .orElseGet(StockTrendProfileEntity::new);
        Instant now = Instant.now();
        double shortWeight = result.shortWeight();
        String profileName = result.profileName();
        if (profile.getId() != null) {
            if (result.qualityScore() < profile.getQualityScore() + properties.effectiveProfileQualityImprovement()) {
                shortWeight = profile.getShortWeight();
                profileName = profile.getProfileName();
            } else {
                double minimum = Math.max(0, profile.getShortWeight() - properties.effectiveMaximumWeightChange());
                double maximum = Math.min(1, profile.getShortWeight() + properties.effectiveMaximumWeightChange());
                shortWeight = clamp(shortWeight, minimum, maximum);
                if (Math.abs(shortWeight - result.shortWeight()) > 0.0001) {
                    profileName = result.profileName() + "_LIMITED";
                }
            }
        }
        profile.setStock(stock);
        profile.setProfileName(profileName);
        profile.setLongWeight(1 - shortWeight);
        profile.setShortWeight(shortWeight);
        profile.setMinimumScorePercent(result.minimumScorePercent());
        profile.setConfidence(result.confidence());
        profile.setQualityScore(result.qualityScore());
        profile.setSampleSize(result.sampleSize());
        profile.setTradingDays(result.tradingDays());
        profile.setMovementFrequency(result.movementFrequency());
        profile.setReversalFrequency(result.reversalFrequency());
        profile.setContinuationRate(result.continuationRate());
        profile.setTypicalMovementPercent(result.typicalMovementPercent());
        profile.setCalculatedAt(now);
        profile.setValidUntil(now.plus(properties.maximumProfileAge()));
        profileRepository.save(profile);
    }

    private MarketDirection direction(double movement, double threshold) {
        if (movement >= threshold) return MarketDirection.UP;
        if (movement <= -threshold) return MarketDirection.DOWN;
        return MarketDirection.NEUTRAL;
    }

    private LocalDate localDate(QuoteEntity quote) {
        return quote.getCreated().atZone(ZoneId.of(properties.recalibrationZone())).toLocalDate();
    }

    private double percentChange(double from, double to) {
        return from <= 0 ? 0 : ((to - from) / from) * 100;
    }

    private double median(List<Double> values) {
        if (values.isEmpty()) return properties.defaults().minimumScorePercent();
        int middle = values.size() / 2;
        return values.size() % 2 == 0 ? (values.get(middle - 1) + values.get(middle)) / 2 : values.get(middle);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record EvaluationWindow(List<Double> observationPrices, double shortDelta, double futureDelta, double absoluteLatestDelta) {
    }

    record CandidateScore(TrendCandidate candidate, double qualityScore, double accuracy) {
    }

    record CalibrationResult(String profileName, double longWeight, double shortWeight, double minimumScorePercent,
                             double confidence, double qualityScore, int sampleSize, int tradingDays,
                             double movementFrequency, double reversalFrequency, double continuationRate,
                             double typicalMovementPercent) {
    }

    public record CalibrationSummary(int updated, int insufficient, int failed) {
    }
}
