package com.prognimak.marketbot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "user_symbol_alert_state",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_symbol_alert_state_user_symbol",
                        columnNames = {"user_id", "symbol"}
                )
        }
)
@Getter
@Setter
public class UserSymbolAlertStateEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @Column(nullable = false, length = 80)
    private String symbol;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_sent_quote_id", nullable = false)
    private QuoteEntity lastSentQuote;

    @Column(nullable = false)
    private Instant lastSentAt;
}
