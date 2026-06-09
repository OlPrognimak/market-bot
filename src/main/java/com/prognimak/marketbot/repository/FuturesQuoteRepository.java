package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.FuturesQuoteEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FuturesQuoteRepository extends JpaRepository<FuturesQuoteEntity, Long> {
    Optional<FuturesQuoteEntity> findFirstBySymbolOrderByProviderTimestampDesc(String symbol);
    List<FuturesQuoteEntity> findBySymbolOrderByProviderTimestampDesc(String symbol, Pageable pageable);
    List<FuturesQuoteEntity> findBySymbolAndProviderTimestampGreaterThanEqualOrderByProviderTimestampAsc(String symbol, Instant from);
}
