package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.UserSymbolAlertStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSymbolAlertStateRepository extends JpaRepository<UserSymbolAlertStateEntity, Long> {
    Optional<UserSymbolAlertStateEntity> findByUserIdAndSymbolIgnoreCase(Long userId, String symbol);
}
