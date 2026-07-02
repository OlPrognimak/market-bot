package com.prognimak.marketbot.trading.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "trade_risk_event", indexes = {
        @Index(name = "idx_trade_risk_event_user_symbol", columnList = "user_id,symbol")
})
@Getter
@Setter
public class TradeRiskEventEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "order_id")
    private Long orderId;
    @Column(length = 40)
    private String symbol;
    private boolean allowed;
    @Column(length = 2000)
    private String warnings;
    @Column(length = 2000)
    private String rejectionReasons;
}
