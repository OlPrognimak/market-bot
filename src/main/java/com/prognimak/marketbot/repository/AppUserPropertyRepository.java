package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.AppUserPropertyEntity;
import com.prognimak.marketbot.user.model.UserPropertyType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserPropertyRepository extends JpaRepository<AppUserPropertyEntity, Long> {
    List<AppUserPropertyEntity> findByPropertyTypeAndPropertyNameIgnoreCaseAndEnabledTrue(
            UserPropertyType propertyType,
            String propertyName
    );

    List<AppUserPropertyEntity> findByUserIdAndPropertyTypeAndEnabledTrue(Long userId, UserPropertyType propertyType);

    List<AppUserPropertyEntity> findByUserIdAndPropertyType(Long userId, UserPropertyType propertyType);

    Optional<AppUserPropertyEntity> findByUserIdAndPropertyTypeAndPropertyNameIgnoreCase(
            Long userId,
            UserPropertyType propertyType,
            String propertyName
    );
}
