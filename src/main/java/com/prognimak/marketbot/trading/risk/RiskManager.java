package com.prognimak.marketbot.trading.risk;

import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.repository.QuoteRepository;
import com.prognimak.marketbot.service.WatchlistService;
import com.prognimak.marketbot.trading.config.TradingProperties;
import com.prognimak.marketbot.trading.entity.PaperPositionEntity;
import com.prognimak.marketbot.trading.model.*;
import com.prognimak.marketbot.trading.repository.PaperPositionRepository;
import com.prognimak.marketbot.trading.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RiskManager {
    private static final List<TradeOrderStatus> PENDING_STATUSES = List.of(TradeOrderStatus.PREVIEWED, TradeOrderStatus.SUBMITTED, TradeOrderStatus.PARTIALLY_FILLED);

    private final TradingProperties properties;
    private final WatchlistService watchlistService;
    private final QuoteRepository quoteRepository;
    private final PaperPositionRepository positionRepository;
    private final TradeOrderRepository orderRepository;

    public RiskCheckResult check(Long userId, TradeOrderRequest request, BigDecimal currentPrice, BigDecimal estimatedQuantity, BigDecimal estimatedValue, boolean finalSubmit) {
        List<String> warnings = new ArrayList<>();
        List<String> rejections = new ArrayList<>();

        if (!properties.enabled()) {
            rejections.add("TRADING_DISABLED");
        }
        if (properties.mode() == TradingMode.LIVE && !properties.liveEnabled()) {
            rejections.add("LIVE_TRADING_DISABLED");
        }
        if (request == null) {
            rejections.add("EMPTY_ORDER_REQUEST");
            return RiskCheckResult.rejected(warnings, rejections);
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            rejections.add("SYMBOL_REQUIRED");
        }
        if (request.side() == null) {
            rejections.add("SIDE_REQUIRED");
        }
        OrderType orderType = request.orderType() == null ? OrderType.MARKET : request.orderType();
        if (!properties.allowedOrderTypes().contains(orderType)) {
            rejections.add("UNSUPPORTED_ORDER_TYPE");
        }
        if (orderType == OrderType.LIMIT && (request.limitPrice() == null || request.limitPrice().signum() <= 0)) {
            rejections.add("LIMIT_PRICE_REQUIRED");
        }
        boolean hasQuantity = request.quantity() != null && request.quantity().signum() > 0;
        boolean hasAmount = request.amount() != null && request.amount().signum() > 0;
        if (!hasQuantity && !hasAmount) {
            rejections.add("QUANTITY_OR_AMOUNT_REQUIRED");
        }
        if (hasQuantity && hasAmount) {
            rejections.add("QUANTITY_AND_AMOUNT_CONFLICT");
        }
        if (currentPrice == null || currentPrice.signum() <= 0) {
            rejections.add("NO_CURRENT_QUOTE");
        }
        if (estimatedValue != null && estimatedValue.compareTo(properties.maxOrderValueEur()) > 0) {
            rejections.add("ORDER_VALUE_TOO_HIGH");
        }
        if (request.symbol() != null && !request.symbol().isBlank() && request.side() == OrderSide.BUY) {
            Map<String, WatchlistItem> watchlist = watchlistService.watchlist();
            if (!watchlist.containsKey(normalize(request.symbol()))) {
                rejections.add("SYMBOL_NOT_IN_WATCHLIST");
            }
        }
        if (request.symbol() != null && !request.symbol().isBlank()) {
            boolean hasQuote = quoteRepository.findFirstBySymbolOrderByCreatedDesc(normalize(request.symbol())).isPresent();
            if (!hasQuote) {
                rejections.add("NO_CURRENT_QUOTE");
            }
            long pending = orderRepository.countByUserIdAndSymbolIgnoreCaseAndStatusIn(userId, normalize(request.symbol()), PENDING_STATUSES);
            if (finalSubmit && pending > properties.maxOpenOrdersPerSymbol()) {
                rejections.add("DUPLICATE_PENDING_ORDER");
            }
        }
        if (request.side() == OrderSide.SELL && !properties.allowShortSelling() && request.symbol() != null && estimatedQuantity != null) {
            PaperPositionEntity position = positionRepository.findByUserIdAndSymbolIgnoreCase(userId, normalize(request.symbol())).orElse(null);
            BigDecimal owned = position == null ? BigDecimal.ZERO : position.getQuantity();
            if (estimatedQuantity.compareTo(owned) > 0) {
                rejections.add("SELL_QUANTITY_EXCEEDS_POSITION");
            }
        }
        if (finalSubmit && properties.requireManualConfirmation() && (request.confirmationToken() == null || request.confirmationToken().isBlank())) {
            rejections.add("CONFIRMATION_REQUIRED");
        }
        if (orderType == OrderType.MARKET && properties.mode() == TradingMode.PAPER) {
            warnings.add("PAPER_MARKET_ORDER_EXECUTES_AT_LATEST_QUOTE");
        }

        return rejections.isEmpty() ? RiskCheckResult.allowed(warnings) : RiskCheckResult.rejected(warnings, rejections);
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
