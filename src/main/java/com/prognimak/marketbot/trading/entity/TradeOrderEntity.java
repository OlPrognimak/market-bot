package com.prognimak.marketbot.trading.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import com.prognimak.marketbot.trading.model.BrokerType;
import com.prognimak.marketbot.trading.model.OrderSide;
import com.prognimak.marketbot.trading.model.OrderType;
import com.prognimak.marketbot.trading.model.TradeOrderStatus;
import com.prognimak.marketbot.trading.model.TradingMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_order", indexes = {
        @Index(name = "idx_trade_order_user_symbol_status", columnList = "user_id,symbol,status"),
        @Index(name = "idx_trade_order_preview", columnList = "preview_id")
})
@Getter
@Setter
public class TradeOrderEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BrokerType brokerType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradingMode tradingMode;
    @Column(nullable = false, length = 40)
    private String symbol;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderSide side;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderType orderType;
    @Column(precision = 24, scale = 8)
    private BigDecimal requestedQuantity;
    @Column(precision = 24, scale = 8)
    private BigDecimal requestedAmount;
    @Column(precision = 24, scale = 8)
    private BigDecimal limitPrice;
    @Column(precision = 24, scale = 8)
    private BigDecimal estimatedQuantity;
    @Column(precision = 24, scale = 8)
    private BigDecimal estimatedValue;
    @Column(precision = 24, scale = 8)
    private BigDecimal previewPrice;
    @Column(length = 20)
    private String currency;
    @Column(length = 80)
    private String exchange;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TradeOrderStatus status;
    @Column(length = 80)
    private String brokerOrderId;
    @Column(name = "preview_id", length = 80)
    private String previewId;
    @Column(length = 120)
    private String confirmationTokenHash;
    @Column(length = 240)
    private String requestFingerprint;
    private Instant expiresAt;
    private Instant submittedAt;
    private Instant completedAt;
    @Column(length = 1000)
    private String message;
}
