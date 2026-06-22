package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.config.TrendProperties;
import com.prognimak.marketbot.config.TrendProperties.TrendParameters;
import com.prognimak.marketbot.entity.StockTrendProfileEntity;
import com.prognimak.marketbot.repository.StockTrendProfileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains an in-memory lookup of valid adaptive trend profiles for enabled shares.
 *
 * <p>Invalid, stale, low-confidence, or insufficiently sampled profiles are excluded so callers
 * transparently receive the configured global fallback parameters.</p>
 */
@Service
@RequiredArgsConstructor
public class StockTrendProfileService {
    private final StockTrendProfileRepository repository;
    private final TrendProperties properties;
    private final Map<String, TrendParameters> validProfiles = new ConcurrentHashMap<>();

    /**
     * Reloads valid persisted profiles into the runtime cache.
     *
     * <p>This is called after construction and after each recalibration run.</p>
     */
    @PostConstruct
    public void refreshCache() {
        validProfiles.clear();
        if (!properties.adaptiveEnabled()) {
            return;
        }
        Instant now = Instant.now();
        repository.findAllByStockEnabledTrue().stream()
                .filter(profile -> valid(profile, now))
                .forEach(profile -> validProfiles.put(normalize(profile.getStock().getSymbol()), parameters(profile)));
    }

    /**
     * Resolves the effective live trend parameters for a share.
     *
     * @param symbol provider share symbol
     * @return valid adaptive parameters, or global fallback parameters
     */
    public TrendParameters parametersFor(String symbol) {
        if (!properties.adaptiveEnabled() || symbol == null) {
            return properties.defaults();
        }
        return validProfiles.getOrDefault(normalize(symbol), properties.defaults());
    }

    private boolean valid(StockTrendProfileEntity profile, Instant now) {
        return profile.getValidUntil() != null
                && profile.getValidUntil().isAfter(now)
                && profile.getCalculatedAt() != null
                && profile.getCalculatedAt().plus(properties.maximumProfileAge()).isAfter(now)
                && profile.getSampleSize() >= properties.effectiveMinimumWindows()
                && profile.getTradingDays() >= properties.effectiveMinimumTradingDays()
                && profile.getConfidence() >= properties.effectiveMinimumConfidence()
                && profile.getLongWeight() >= 0
                && profile.getShortWeight() >= 0
                && Math.abs(profile.getLongWeight() + profile.getShortWeight() - 1) < 0.0001;
    }

    private TrendParameters parameters(StockTrendProfileEntity profile) {
        return new TrendParameters(
                profile.getProfileName(),
                profile.getLongWeight(),
                profile.getShortWeight(),
                profile.getMinimumScorePercent(),
                profile.getConfidence()
        );
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
