package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.ShareSessionQuoteEntity;
import com.prognimak.marketbot.model.MarketSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShareSessionQuoteRepository extends JpaRepository<ShareSessionQuoteEntity, Long> {
    Optional<ShareSessionQuoteEntity> findFirstBySymbolAndSessionTypeOrderByProviderTimestampDesc(String symbol, MarketSession sessionType);
    Optional<ShareSessionQuoteEntity> findFirstBySessionTypeOrderByProviderTimestampDesc(MarketSession sessionType);
    Optional<ShareSessionQuoteEntity> findFirstByOrderByProviderTimestampDesc();
    Optional<ShareSessionQuoteEntity> findFirstBySymbolInAndSessionTypeOrderByProviderTimestampDesc(
            Collection<String> symbols, MarketSession sessionType
    );
    Optional<ShareSessionQuoteEntity> findFirstBySymbolInOrderByProviderTimestampDesc(Collection<String> symbols);
    List<ShareSessionQuoteEntity> findBySymbolAndSessionTypeOrderByProviderTimestampDesc(String symbol, MarketSession sessionType, Pageable pageable);
    List<ShareSessionQuoteEntity> findBySymbolAndProviderTimestampGreaterThanEqualOrderByProviderTimestampAsc(String symbol, Instant from);
    List<ShareSessionQuoteEntity> findBySessionTypeAndProviderTimestampBetweenOrderByProviderTimestampDesc(
            MarketSession sessionType, Instant from, Instant to
    );
    List<ShareSessionQuoteEntity> findByProviderTimestampBetweenOrderByProviderTimestampDesc(Instant from, Instant to);
}
