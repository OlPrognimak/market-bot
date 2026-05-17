package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.QuoteEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteRepository extends JpaRepository<QuoteEntity, Long> {

    List<QuoteEntity> findBySymbolAndSendIsFalseOrderByIdDesc(String symbol, Pageable pageable);
}
