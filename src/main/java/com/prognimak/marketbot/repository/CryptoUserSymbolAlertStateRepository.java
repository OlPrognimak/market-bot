package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.CryptoUserSymbolAlertStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CryptoUserSymbolAlertStateRepository extends JpaRepository<CryptoUserSymbolAlertStateEntity, Long> {
    Optional<CryptoUserSymbolAlertStateEntity> findByUserIdAndSymbolIgnoreCase(Long userId, String symbol);
}
