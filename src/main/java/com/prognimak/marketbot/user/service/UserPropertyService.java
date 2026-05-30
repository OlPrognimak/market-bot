package com.prognimak.marketbot.user.service;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.entity.AppUserPropertyEntity;
import com.prognimak.marketbot.repository.AppUserPropertyRepository;
import com.prognimak.marketbot.user.model.DashboardSettings;
import com.prognimak.marketbot.user.model.UserAlertSettings;
import com.prognimak.marketbot.user.model.UserMessengerSettings;
import com.prognimak.marketbot.user.model.UserPropertyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserPropertyService {
    public static final String ALERT_ROLLING_THRESHOLD = "alert-rolling-threshold";
    public static final String ALERT_DELTA_THRESHOLD = "alert-delta-threshold";
    public static final String TELEGRAM_ENABLED = "telegram-enabled";
    public static final String TELEGRAM_CHAT_ID = "telegram-chat-id";
    public static final String TELEGRAM_BOT_TOKEN = "telegram-bot-token";
    public static final String WHATSAPP_ENABLED = "whatsapp-enabled";
    public static final String WHATSAPP_CHAT_ID = "whatsapp-chat-id";
    public static final String WHATSAPP_BOT_TOKEN = "whatsapp-bot-token";
    public static final String DASHBOARD_MAX_RESULTS = "dashboard-max-results";

    private final AppUserPropertyRepository propertyRepository;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<AppUserPropertyEntity> findUsersWatchingSymbol(String symbol) {
        return propertyRepository.findByPropertyTypeAndPropertyNameIgnoreCaseAndEnabledTrue(
                UserPropertyType.WATCHLIST,
                symbol
        );
    }

    @Transactional(readOnly = true)
    public List<AppUserPropertyEntity> findUsersWatchingCryptoCoin(String symbol) {
        return propertyRepository.findByPropertyTypeAndPropertyNameIgnoreCaseAndEnabledTrue(
                UserPropertyType.CRYPTO_COIN,
                normalizeCryptoSymbol(symbol)
        );
    }

    @Transactional(readOnly = true)
    public Set<String> loadEnabledStockSymbols(Long userId) {
        return propertyRepository.findByUserIdAndPropertyTypeAndEnabledTrue(userId, UserPropertyType.WATCHLIST)
                .stream()
                .map(AppUserPropertyEntity::getPropertyName)
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public Optional<Set<String>> loadUserStockSymbols(Long userId) {
        List<AppUserPropertyEntity> properties = propertyRepository.findByUserIdAndPropertyType(
                userId,
                UserPropertyType.WATCHLIST
        );
        if (properties.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(properties.stream()
                .filter(AppUserPropertyEntity::isEnabled)
                .map(AppUserPropertyEntity::getPropertyName)
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Transactional(readOnly = true)
    public Optional<Set<String>> loadUserCryptoSymbols(Long userId) {
        List<AppUserPropertyEntity> properties = propertyRepository.findByUserIdAndPropertyType(
                userId,
                UserPropertyType.CRYPTO_COIN
        );
        if (properties.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(properties.stream()
                .filter(AppUserPropertyEntity::isEnabled)
                .map(AppUserPropertyEntity::getPropertyName)
                .map(this::normalizeCryptoSymbol)
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Transactional(readOnly = true)
    public UserAlertSettings loadAlertSettings(Long userId) {
        return new UserAlertSettings(
                doubleProperty(userId, UserPropertyType.ALERT_SETTING, ALERT_ROLLING_THRESHOLD)
                        .orElse(appProperties.alert().maximalRollingPrice()),
                doubleProperty(userId, UserPropertyType.ALERT_SETTING, ALERT_DELTA_THRESHOLD)
                        .orElse(Math.abs(appProperties.alert().riseAlertPercent()))
        );
    }

    @Transactional(readOnly = true)
    public UserMessengerSettings loadMessengerSettings(Long userId) {
        return new UserMessengerSettings(
                booleanProperty(userId, UserPropertyType.BOT, TELEGRAM_ENABLED).orElse(false),
                stringProperty(userId, UserPropertyType.BOT, TELEGRAM_CHAT_ID).orElse(appProperties.messageSender().telegramChatId()),
                stringProperty(userId, UserPropertyType.BOT, TELEGRAM_BOT_TOKEN).orElse(appProperties.messageSender().telegramBotToken()),
                booleanProperty(userId, UserPropertyType.BOT, WHATSAPP_ENABLED).orElse(false),
                stringProperty(userId, UserPropertyType.BOT, WHATSAPP_CHAT_ID).orElse(null),
                stringProperty(userId, UserPropertyType.BOT, WHATSAPP_BOT_TOKEN).orElse(null)
        );
    }

    @Transactional(readOnly = true)
    public DashboardSettings loadDashboardSettings(Long userId, int fallbackMaxResults) {
        return new DashboardSettings(
                intProperty(userId, UserPropertyType.DASHBOARD_SETTING, DASHBOARD_MAX_RESULTS)
                        .orElse(fallbackMaxResults)
        );
    }

    private Optional<String> stringProperty(Long userId, UserPropertyType type, String name) {
        return propertyRepository.findByUserIdAndPropertyTypeAndPropertyNameIgnoreCase(userId, type, name)
                .filter(AppUserPropertyEntity::isEnabled)
                .map(AppUserPropertyEntity::getPropertyValue)
                .filter(value -> value != null && !value.isBlank());
    }

    private Optional<Boolean> booleanProperty(Long userId, UserPropertyType type, String name) {
        return stringProperty(userId, type, name).map(Boolean::parseBoolean);
    }

    private Optional<Double> doubleProperty(Long userId, UserPropertyType type, String name) {
        return stringProperty(userId, type, name).map(Double::parseDouble);
    }

    private Optional<Integer> intProperty(Long userId, UserPropertyType type, String name) {
        return stringProperty(userId, type, name).map(Integer::parseInt);
    }

    private String normalizeCryptoSymbol(String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return normalized.endsWith("USDT") ? normalized.substring(0, normalized.length() - 4) : normalized;
    }
}
