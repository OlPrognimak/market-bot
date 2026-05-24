package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.HistoryQuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HistoryQuoteRepository extends JpaRepository<HistoryQuoteEntity, Long> {

    boolean existsBySymbolAndTradingDateAndIntervalType(String symbol, LocalDate tradingDate, String intervalType);

    Optional<HistoryQuoteEntity> findBySymbolAndTradingDateAndIntervalType(
            String symbol,
            LocalDate tradingDate,
            String intervalType
    );

    List<HistoryQuoteEntity> findBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
            String symbol,
            LocalDate from,
            LocalDate to
    );
}
