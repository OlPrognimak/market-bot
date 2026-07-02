package com.prognimak.marketbot.trading.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "paper_position",
        uniqueConstraints = @UniqueConstraint(name = "uk_paper_position_user_symbol", columnNames = {"user_id", "symbol"})
)
@Getter
@Setter
public class PaperPositionEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(nullable = false, length = 40)
    private String symbol;
    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity = BigDecimal.ZERO;
    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal avgPrice = BigDecimal.ZERO;
    @Column(length = 20)
    private String currency;
    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal realizedPnl = BigDecimal.ZERO;
}
