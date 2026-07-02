package com.prognimak.marketbot.trading.paper;

import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.repository.QuoteRepository;
import com.prognimak.marketbot.trading.config.TradingProperties;
import com.prognimak.marketbot.trading.entity.PaperPositionEntity;
import com.prognimak.marketbot.trading.entity.TradeExecutionEntity;
import com.prognimak.marketbot.trading.entity.TradeOrderEntity;
import com.prognimak.marketbot.trading.model.*;
import com.prognimak.marketbot.trading.repository.PaperPositionRepository;
import com.prognimak.marketbot.trading.repository.TradeExecutionRepository;
import com.prognimak.marketbot.trading.repository.TradeOrderRepository;
import com.prognimak.marketbot.trading.risk.RiskManager;
import com.prognimak.marketbot.trading.service.BrokerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaperBrokerService implements BrokerService {
    private final TradingProperties properties;
    private final QuoteRepository quoteRepository;
    private final TradeOrderRepository orderRepository;
    private final TradeExecutionRepository executionRepository;
    private final PaperPositionRepository positionRepository;
    private final RiskManager riskManager;

    @Override
    @Transactional
    public TradeOrderPreview previewOrder(Long userId, TradeOrderRequest request) {
        String symbol = normalize(request.symbol());
        BigDecimal currentPrice = currentPrice(symbol).orElse(BigDecimal.ZERO);
        BigDecimal estimatedQuantity = estimatedQuantity(userId, request, currentPrice);
        BigDecimal estimatedValue = currentPrice.multiply(estimatedQuantity).setScale(2, RoundingMode.HALF_UP);
        RiskCheckResult risk = riskManager.check(userId, request, currentPrice, estimatedQuantity, estimatedValue, false);

        String previewId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(properties.previewTtlSeconds());
        TradeOrderEntity order = new TradeOrderEntity();
        order.setUserId(userId);
        order.setBrokerType(BrokerType.PAPER);
        order.setTradingMode(TradingMode.PAPER);
        order.setSymbol(symbol);
        order.setSide(request.side());
        order.setOrderType(request.orderType() == null ? OrderType.MARKET : request.orderType());
        order.setRequestedQuantity(request.quantity());
        order.setRequestedAmount(request.amount());
        order.setLimitPrice(request.limitPrice());
        order.setEstimatedQuantity(estimatedQuantity);
        order.setEstimatedValue(estimatedValue);
        order.setPreviewPrice(currentPrice);
        order.setCurrency(blankToDefault(request.currency(), "USD"));
        order.setExchange(request.exchange());
        order.setStatus(risk.allowed() ? TradeOrderStatus.PREVIEWED : TradeOrderStatus.REJECTED);
        order.setPreviewId(previewId);
        order.setConfirmationTokenHash(hash(token));
        order.setRequestFingerprint(fingerprint(request, estimatedQuantity, estimatedValue));
        order.setExpiresAt(expiresAt);
        order.setMessage(String.join(", ", risk.rejectionReasons()));
        orderRepository.save(order);

        return new TradeOrderPreview(previewId, symbol, request.side(), order.getOrderType(), currentPrice, estimatedQuantity,
                estimatedValue, order.getCurrency(), TradingMode.PAPER, risk.allowed(), risk.warnings(), risk.rejectionReasons(), token, expiresAt);
    }

    @Override
    @Transactional
    public TradeOrderResult placeOrder(Long userId, TradeOrderRequest request) {
        TradeOrderEntity order = orderRepository.findByUserIdAndPreviewId(userId, request.previewId())
                .orElseThrow(() -> new IllegalArgumentException("PREVIEW_NOT_FOUND"));
        if (order.getStatus() != TradeOrderStatus.PREVIEWED) {
            order.setStatus(TradeOrderStatus.REJECTED);
            order.setMessage("PREVIEW_NOT_ALLOWED");
            return result(order);
        }
        if (order.getExpiresAt() == null || order.getExpiresAt().isBefore(Instant.now())) {
            order.setStatus(TradeOrderStatus.REJECTED);
            order.setMessage("PREVIEW_EXPIRED");
            return result(order);
        }
        if (!hash(request.confirmationToken()).equals(order.getConfirmationTokenHash())) {
            order.setStatus(TradeOrderStatus.REJECTED);
            order.setMessage("CONFIRMATION_REQUIRED");
            return result(order);
        }
        BigDecimal currentPrice = currentPrice(order.getSymbol()).orElse(BigDecimal.ZERO);
        BigDecimal estimatedQuantity = order.getEstimatedQuantity();
        BigDecimal estimatedValue = currentPrice.multiply(estimatedQuantity).setScale(2, RoundingMode.HALF_UP);
        RiskCheckResult risk = riskManager.check(userId, request, currentPrice, estimatedQuantity, estimatedValue, true);
        if (!risk.allowed()) {
            order.setStatus(TradeOrderStatus.REJECTED);
            order.setMessage(String.join(", ", risk.rejectionReasons()));
            return result(order);
        }
        if (order.getOrderType() == OrderType.LIMIT && !limitSatisfied(order.getSide(), order.getLimitPrice(), currentPrice)) {
            order.setStatus(TradeOrderStatus.SUBMITTED);
            order.setSubmittedAt(Instant.now());
            order.setMessage("LIMIT_ORDER_WAITING_FOR_PRICE");
            return result(order);
        }

        order.setSubmittedAt(Instant.now());
        TradeExecutionEntity execution = new TradeExecutionEntity();
        execution.setOrderId(order.getId());
        execution.setUserId(userId);
        execution.setBrokerExecutionId(UUID.randomUUID().toString());
        execution.setSymbol(order.getSymbol());
        execution.setSide(order.getSide());
        execution.setQuantity(estimatedQuantity);
        execution.setPrice(currentPrice);
        execution.setGrossAmount(estimatedValue);
        execution.setFees(BigDecimal.ZERO);
        execution.setCurrency(order.getCurrency());
        execution.setExecutedAt(Instant.now());
        executionRepository.save(execution);
        applyExecution(userId, order.getSymbol(), order.getSide(), estimatedQuantity, currentPrice, order.getCurrency());

        order.setBrokerOrderId("PAPER-" + order.getId());
        order.setStatus(TradeOrderStatus.FILLED);
        order.setCompletedAt(Instant.now());
        order.setMessage("PAPER_ORDER_FILLED");
        return result(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PositionDto> getPositions(Long userId) {
        return positionRepository.findByUserIdOrderBySymbolAsc(userId).stream()
                .filter(position -> position.getQuantity().signum() > 0)
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PositionDto> getPosition(Long userId, String symbol) {
        return positionRepository.findByUserIdAndSymbolIgnoreCase(userId, normalize(symbol)).map(this::toDto);
    }

    @Override
    @Transactional
    public TradeOrderResult closePosition(Long userId, String symbol) {
        PaperPositionEntity position = positionRepository.findByUserIdAndSymbolIgnoreCase(userId, normalize(symbol))
                .orElseThrow(() -> new IllegalArgumentException("POSITION_NOT_FOUND"));
        return placeOrder(userId, new TradeOrderRequest(null, symbol, OrderSide.SELL, position.getQuantity(), null,
                OrderType.MARKET, null, position.getCurrency(), null, null, null, null));
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.PAPER;
    }

    private void applyExecution(Long userId, String symbol, OrderSide side, BigDecimal quantity, BigDecimal price, String currency) {
        PaperPositionEntity position = positionRepository.findByUserIdAndSymbolIgnoreCase(userId, symbol).orElseGet(() -> {
            PaperPositionEntity created = new PaperPositionEntity();
            created.setUserId(userId);
            created.setSymbol(symbol);
            created.setCurrency(currency);
            return created;
        });
        if (side == OrderSide.BUY) {
            BigDecimal oldValue = position.getQuantity().multiply(position.getAvgPrice());
            BigDecimal buyValue = quantity.multiply(price);
            BigDecimal newQuantity = position.getQuantity().add(quantity);
            position.setQuantity(newQuantity);
            position.setAvgPrice(oldValue.add(buyValue).divide(newQuantity, 8, RoundingMode.HALF_UP));
        } else {
            BigDecimal sellQuantity = quantity.min(position.getQuantity());
            BigDecimal pnl = price.subtract(position.getAvgPrice()).multiply(sellQuantity);
            position.setQuantity(position.getQuantity().subtract(sellQuantity));
            position.setRealizedPnl(position.getRealizedPnl().add(pnl));
            if (position.getQuantity().signum() == 0) {
                position.setAvgPrice(BigDecimal.ZERO);
            }
        }
        positionRepository.save(position);
    }

    private PositionDto toDto(PaperPositionEntity position) {
        BigDecimal current = currentPrice(position.getSymbol()).orElse(BigDecimal.ZERO);
        BigDecimal marketValue = current.multiply(position.getQuantity()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = position.getAvgPrice().multiply(position.getQuantity());
        BigDecimal pnl = marketValue.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pnlPercent = cost.signum() == 0 ? BigDecimal.ZERO : pnl.multiply(BigDecimal.valueOf(100)).divide(cost, 2, RoundingMode.HALF_UP);
        return new PositionDto(position.getSymbol(), position.getQuantity(), position.getAvgPrice(), current, marketValue, pnl, pnlPercent, position.getCurrency(), BrokerType.PAPER);
    }

    private Optional<BigDecimal> currentPrice(String symbol) {
        return quoteRepository.findFirstBySymbolOrderByCreatedDesc(symbol).map(QuoteEntity::getCurrent).map(BigDecimal::valueOf);
    }

    private BigDecimal estimatedQuantity(Long userId, TradeOrderRequest request, BigDecimal price) {
        if (request.quantity() != null && request.quantity().signum() > 0) {
            return request.quantity().setScale(8, RoundingMode.HALF_UP);
        }
        if (request.amount() != null && request.amount().signum() > 0 && price.signum() > 0) {
            return request.amount().divide(price, 8, RoundingMode.DOWN);
        }
        return BigDecimal.ZERO;
    }

    private boolean limitSatisfied(OrderSide side, BigDecimal limitPrice, BigDecimal currentPrice) {
        if (limitPrice == null) {
            return false;
        }
        return side == OrderSide.BUY ? currentPrice.compareTo(limitPrice) <= 0 : currentPrice.compareTo(limitPrice) >= 0;
    }

    private TradeOrderResult result(TradeOrderEntity order) {
        return new TradeOrderResult(order.getId(), order.getBrokerOrderId(), order.getSymbol(), order.getSide(), order.getStatus(), order.getSubmittedAt(), order.getMessage());
    }

    private String fingerprint(TradeOrderRequest request, BigDecimal quantity, BigDecimal value) {
        return hash(normalize(request.symbol()) + "|" + request.side() + "|" + (request.orderType() == null ? OrderType.MARKET : request.orderType())
                + "|" + quantity + "|" + value + "|" + request.limitPrice());
    }

    private String hash(String value) {
        if (value == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash trading token", e);
        }
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
}
