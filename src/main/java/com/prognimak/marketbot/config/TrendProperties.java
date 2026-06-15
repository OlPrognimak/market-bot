package com.prognimak.marketbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configures live share-trend detection and scheduled adaptive-profile calibration.
 *
 * <p>The effective-value methods validate unsafe or missing configuration and provide the
 * runtime values used by the scanner and calibration services.</p>
 */
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

    /**
     * Returns the normalized global fallback parameters used when no valid adaptive profile exists.
     */
    public TrendParameters defaults() {
        return new TrendParameters("DEFAULT", normalizedLongWeight(), normalizedShortWeight(),
                positive(defaultMinimumScorePercent, 0.15), 0);
    }

    /** Returns the validated maximum number of recent prices used for live trend detection. */
    public int effectiveObservationWindow() {
        return Math.max(observationWindow, 3);
    }

    /** Returns the validated recent-price window, capped by the observation window. */
    public int effectiveShortWindow() {
        return Math.min(Math.max(shortWindow, 2), effectiveObservationWindow());
    }

    /** Returns the number of future saved quotes used to evaluate historical predictions. */
    public int effectiveEvaluationHorizon() {
        return Math.max(evaluationHorizon, 1);
    }

    /** Returns the validated number of calendar days loaded for profile calibration. */
    public int effectiveLookbackDays() {
        return Math.max(lookbackDays, 1);
    }

    /** Returns the minimum number of distinct trading days required for an adaptive profile. */
    public int effectiveMinimumTradingDays() {
        return Math.max(minimumTradingDays, 1);
    }

    /** Returns the minimum number of historical evaluation windows required for calibration. */
    public int effectiveMinimumWindows() {
        return Math.max(minimumWindows, 1);
    }

    /** Returns the minimum accepted adaptive-profile confidence in the inclusive range {@code [0, 1]}. */
    public double effectiveMinimumConfidence() {
        return clamp(minimumConfidence, 0, 1);
    }

    /** Returns the multiplier used to derive a share-specific threshold from typical movement. */
    public double effectiveThresholdMultiplier() {
        return positive(thresholdMultiplier, 2);
    }

    /** Returns the lower bound for a calibrated minimum trend score. */
    public double effectiveMinimumAdaptiveScorePercent() {
        return positive(minimumAdaptiveScorePercent, 0.05);
    }

    /** Returns the upper bound for a calibrated minimum trend score. */
    public double effectiveMaximumAdaptiveScorePercent() {
        return Math.max(effectiveMinimumAdaptiveScorePercent(), positive(maximumAdaptiveScorePercent, 1));
    }

    /** Returns how long a calculated adaptive profile may remain valid. */
    public Duration maximumProfileAge() {
        return Duration.ofDays(Math.max(maximumProfileAgeDays, 1));
    }

    /** Returns the maximum range position at which a downward trend may be confirmed. */
    public double effectiveLowerRangePosition() {
        return clamp(lowerRangePosition, 0, 0.49);
    }

    /** Returns the minimum range position at which an upward trend may be confirmed. */
    public double effectiveUpperRangePosition() {
        return clamp(upperRangePosition, 0.51, 1);
    }

    /** Returns the allowed previous-close difference when grouping quotes into one sequence. */
    public double effectivePreviousCloseTolerance() {
        return positive(previousCloseTolerance, 0.01);
    }

    /** Returns the quality improvement required before replacing an existing profile's weights. */
    public double effectiveProfileQualityImprovement() {
        return Math.max(profileQualityImprovement, 0);
    }

    /** Returns the maximum short-weight change permitted during one recalibration. */
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

    /** Returns the validated lower bound for candidate short-window weights. */
    public double effectiveMinimumShortWeight() {
        return clamp(minimumShortWeight, 0.05, 0.95);
    }

    /** Returns the validated upper bound for candidate short-window weights. */
    public double effectiveMaximumShortWeight() {
        return clamp(Math.max(maximumShortWeight, effectiveMinimumShortWeight()), effectiveMinimumShortWeight(), 0.95);
    }

    private double positive(double value, double fallback) {
        return value > 0 ? value : fallback;
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Defines a controlled weight combination evaluated during historical calibration.
     */
    public record TrendCandidate(String name, double longWeight, double shortWeight) {
    }

    /**
     * Contains the effective weights, threshold, and confidence used for live trend detection.
     */
    public record TrendParameters(String profileName, double longWeight, double shortWeight,
                                  double minimumScorePercent, double confidence) {
    }
}
