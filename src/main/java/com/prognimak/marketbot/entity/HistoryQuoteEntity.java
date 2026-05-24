package com.prognimak.marketbot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Data
@Table(uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_history_quote_symbol_date_interval",
                columnNames = {"symbol", "trading_date", "interval_type"}
        )
})
public class HistoryQuoteEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String sourceSymbol;
    private String source;
    @Column(name = "interval_type")
    private String intervalType;
    @Column(name = "trading_date")
    private LocalDate tradingDate;

    private double current;
    private double change;
    private double percentChange;
    private double high;
    private double low;
    private double open;
    private double previousClose;
    private double delta;
    private boolean send;
    private boolean validated;
}
