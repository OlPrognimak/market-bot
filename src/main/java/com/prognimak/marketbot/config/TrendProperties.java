package com.prognimak.marketbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "market-bot.trend")
public record TrendProperties(
        boolean adaptiveEnabled,
        boolean recalibrateOnStartup,
        String recalibrationCron,
        String recalibrationZone,
        int observationWindow,
        int shortWindow,
        int evaluationHorizon,
        int lookbackDays,
        int minimumTradingDays,
        int minimumWindows,
        double defaultLongWeight,
        double defaultShortWeight,
        double defaultMinimumScorePercent,
        double minimumShortWeight,
        double maximumShortWeight,
        double minimumConfidence,
        double thresholdMultiplier,
        double minimumAdaptiveScorePercent,
        double maximumAdaptiveScorePercent,
        int maximumProfileAgeDays,
        double lowerRangePosition,
        double upperRangePosition,
        double previousCloseTolerance,
        double profileQualityImprovement,
        double maximumWeightChange
) {
    public static final List<TrendCandidate> CANDIDATES = List.of(
            new TrendCandidate("STABLE", 0.70, 0.30),
            new TrendCandidate("SLOW_BALANCED", 0.55, 0.45),
            new TrendCandidate("BALANCED", 0.40, 0.60),
            new TrendCandidate("REACTIVE", 0.25, 0.75)
    );

    public TrendParameters defaults() {
        return new TrendParameters("DEFAULT", normalizedLongWeight(), normalizedShortWeight(),
                positive(defaultMinimumScorePercent, 0.15), 0);
    }

    public int effectiveObservationWindow() {
        return Math.max(observationWindow, 3);
    }

    public int effectiveShortWindow() {
        return Math.min(Math.max(shortWindow, 2), effectiveObservationWindow());
    }

    public int effectiveEvaluationHorizon() {
        return Math.max(evaluationHorizon, 1);
    }

    public int effectiveLookbackDays() {
        return Math.max(lookbackDays, 1);
    }

    public int effectiveMinimumTradingDays() {
        return Math.max(minimumTradingDays, 1);
    }

    public int effectiveMinimumWindows() {
        return Math.max(minimumWindows, 1);
    }

    public double effectiveMinimumConfidence() {
        return clamp(minimumConfidence, 0, 1);
    }

    public double effectiveThresholdMultiplier() {
        return positive(thresholdMultiplier, 2);
    }

    public double effectiveMinimumAdaptiveScorePercent() {
        return positive(minimumAdaptiveScorePercent, 0.05);
    }

    public double effectiveMaximumAdaptiveScorePercent() {
        return Math.max(effectiveMinimumAdaptiveScorePercent(), positive(maximumAdaptiveScorePercent, 1));
    }

    public Duration maximumProfileAge() {
        return Duration.ofDays(Math.max(maximumProfileAgeDays, 1));
    }

    public double effectiveLowerRangePosition() {
        return clamp(lowerRangePosition, 0, 0.49);
    }

    public double effectiveUpperRangePosition() {
        return clamp(upperRangePosition, 0.51, 1);
    }

    public double effectivePreviousCloseTolerance() {
        return positive(previousCloseTolerance, 0.01);
    }

    public double effectiveProfileQualityImprovement() {
        return Math.max(profileQualityImprovement, 0);
    }

    public double effectiveMaximumWeightChange() {
        return clamp(maximumWeightChange, 0.01, 1);
    }

    private double normalizedShortWeight() {
        double total = defaultLongWeight + defaultShortWeight;
        return total <= 0 ? 0.65 : clamp(defaultShortWeight / total, effectiveMinimumShortWeight(), effectiveMaximumShortWeight());
    }

    private double normalizedLongWeight() {
        return 1 - normalizedShortWeight();
    }

    public double effectiveMinimumShortWeight() {
        return clamp(minimumShortWeight, 0.05, 0.95);
    }

    public double effectiveMaximumShortWeight() {
        return clamp(Math.max(maximumShortWeight, effectiveMinimumShortWeight()), effectiveMinimumShortWeight(), 0.95);
    }

    private double positive(double value, double fallback) {
        return value > 0 ? value : fallback;
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record TrendCandidate(String name, double longWeight, double shortWeight) {
    }

    public record TrendParameters(String profileName, double longWeight, double shortWeight,
                                  double minimumScorePercent, double confidence) {
    }
}
