package com.prognimak.marketbot.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(indexes = @Index(name = "idx_quote_entity_symbol_created", columnList = "symbol,created"))
@Data
public class QuoteEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String symbol;
    private double current;
    private double change;
    private double percentChange;
    private double high;
    private double low;
    private double open;
    private double previousClose;
    private double delta;
    private boolean send;
}
