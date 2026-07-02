package com.prognimak.marketbot.trading.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import com.prognimak.marketbot.trading.model.OrderSide;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_execution", indexes = {
        @Index(name = "idx_trade_execution_user_symbol", columnList = "user_id,symbol,executed_at")
})
@Getter
@Setter
public class TradeExecutionEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id")
    private Long orderId;
    @Column(name = "user_id")
    private Long userId;
    @Column(length = 80)
    private String brokerExecutionId;
    @Column(nullable = false, length = 40)
    private String symbol;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderSide side;
    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;
    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal price;
    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal grossAmount;
    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal fees = BigDecimal.ZERO;
    @Column(length = 20)
    private String currency;
    @Column(name = "executed_at")
    private Instant executedAt;
}
