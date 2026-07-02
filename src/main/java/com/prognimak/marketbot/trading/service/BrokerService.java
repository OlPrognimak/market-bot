package com.prognimak.marketbot.trading.service;

import com.prognimak.marketbot.trading.model.*;

import java.util.List;
import java.util.Optional;

public interface BrokerService {
    TradeOrderPreview previewOrder(Long userId, TradeOrderRequest request);

    TradeOrderResult placeOrder(Long userId, TradeOrderRequest request);

    List<PositionDto> getPositions(Long userId);

    Optional<PositionDto> getPosition(Long userId, String symbol);

    TradeOrderResult closePosition(Long userId, String symbol);

    boolean isConnected();

    BrokerType brokerType();
}
