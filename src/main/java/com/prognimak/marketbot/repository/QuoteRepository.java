package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.QuoteEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<QuoteEntity, Long> {

    List<QuoteEntity> findBySymbolAndSendIsFalseOrderByCreatedDesc(String symbol, Pageable pageable);

    List<QuoteEntity> findBySymbolOrderByCreatedDesc(String symbol, Pageable pageable);

    Optional<QuoteEntity> findFirstBySymbolOrderByCreatedDesc(String symbol);

    List<QuoteEntity> findBySymbolAndCreatedGreaterThanEqualOrderByCreatedAsc(String symbol, Instant created);

    List<QuoteEntity> findBySymbolAndCreatedGreaterThanEqualAndCreatedLessThanOrderByCreatedAsc(
            String symbol,
            Instant from,
            Instant to
    );
}
