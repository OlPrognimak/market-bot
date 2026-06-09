package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.ShareSessionQuoteEntity;
import com.prognimak.marketbot.model.MarketSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShareSessionQuoteRepository extends JpaRepository<ShareSessionQuoteEntity, Long> {
    Optional<ShareSessionQuoteEntity> findFirstBySymbolAndSessionTypeOrderByProviderTimestampDesc(String symbol, MarketSession sessionType);
    List<ShareSessionQuoteEntity> findBySymbolAndSessionTypeOrderByProviderTimestampDesc(String symbol, MarketSession sessionType, Pageable pageable);
    List<ShareSessionQuoteEntity> findBySymbolAndProviderTimestampGreaterThanEqualOrderByProviderTimestampAsc(String symbol, Instant from);
}
