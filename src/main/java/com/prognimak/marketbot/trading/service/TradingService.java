package com.prognimak.marketbot.trading.service;

import com.prognimak.marketbot.trading.config.TradingProperties;
import com.prognimak.marketbot.trading.ibkr.IbkrConnectionManager;
import com.prognimak.marketbot.trading.model.*;
import com.prognimak.marketbot.trading.paper.PaperBrokerService;
import com.prognimak.marketbot.trading.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingService {
    private final TradingProperties properties;
    private final PaperBrokerService paperBrokerService;
    private final TradeOrderRepository orderRepository;
    private final IbkrConnectionManager ibkrConnectionManager;

    public TradingStatusResponse status() {
        BrokerType brokerType = properties.mode() == TradingMode.PAPER ? BrokerType.PAPER : BrokerType.IBKR;
        return new TradingStatusResponse(properties.enabled(), properties.mode(), brokerType, properties.liveEnabled(),
                properties.ibkr().enabled(), ibkrConnectionManager.isConnected(), properties.requireManualConfirmation());
    }

    public IbkrConnectionStatusResponse ibkrStatus() {
        return ibkrConnectionManager.status();
    }

    public IbkrConnectionStatusResponse connectIbkr() {
        return ibkrConnectionManager.connect();
    }

    public IbkrConnectionStatusResponse disconnectIbkr() {
        return ibkrConnectionManager.disconnect();
    }

    public TradeOrderPreview preview(Long userId, TradeOrderRequest request) {
        return broker().previewOrder(userId, request);
    }

    public TradeOrderResult submit(Long userId, TradeOrderRequest request) {
        return broker().placeOrder(userId, request);
    }

    public List<PositionDto> positions(Long userId) {
        return broker().getPositions(userId);
    }

    public PositionDto position(Long userId, String symbol) {
        return broker().getPosition(userId, symbol).orElseThrow(() -> new IllegalArgumentException("POSITION_NOT_FOUND"));
    }

    public TradeOrderPreview closePreview(Long userId, String symbol, String currency) {
        PositionDto position = position(userId, symbol);
        return preview(userId, new TradeOrderRequest(null, symbol, OrderSide.SELL, position.quantity(), null,
                OrderType.MARKET, null, currency == null ? position.currency() : currency, null, null, null, null));
    }

    @Transactional(readOnly = true)
    public List<TradeOrderResult> orders(Long userId) {
        return orderRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(order -> new TradeOrderResult(order.getId(), order.getBrokerOrderId(), order.getSymbol(), order.getSide(),
                        order.getStatus(), order.getSubmittedAt(), order.getMessage()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TradeOrderResult order(Long userId, Long orderId) {
        var order = orderRepository.findById(orderId)
                .filter(candidate -> candidate.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("ORDER_NOT_FOUND"));
        return new TradeOrderResult(order.getId(), order.getBrokerOrderId(), order.getSymbol(), order.getSide(),
                order.getStatus(), order.getSubmittedAt(), order.getMessage());
    }

    private BrokerService broker() {
        if (properties.mode() == TradingMode.PAPER) {
            return paperBrokerService;
        }
        throw new IllegalStateException("IBKR_EXECUTION_NOT_ENABLED");
    }
}
