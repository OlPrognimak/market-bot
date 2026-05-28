package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.CryptoQuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CryptoQuoteRepository extends JpaRepository<CryptoQuoteEntity, Long> {
    Optional<CryptoQuoteEntity> findFirstBySymbolOrderByCreatedDesc(String symbol);

    List<CryptoQuoteEntity> findBySymbolAndCreatedGreaterThanEqualOrderByCreatedAsc(String symbol, Instant created);

    List<CryptoQuoteEntity> findBySymbolAndCreatedGreaterThanEqualAndCreatedLessThanOrderByCreatedAsc(
            String symbol,
            Instant from,
            Instant to
    );
}
