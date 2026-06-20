package com.prognimak.marketbot.system.repository;

import com.prognimak.marketbot.system.entity.SystemApiCredentialEntity;
import com.prognimak.marketbot.system.entity.SystemCredentialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemApiCredentialRepository extends JpaRepository<SystemApiCredentialEntity, Long> {
    List<SystemApiCredentialEntity> findAllByOrderByCredentialTypeAscProviderNameAscDisplayNameAsc();

    List<SystemApiCredentialEntity> findByCredentialType(SystemCredentialType credentialType);

    List<SystemApiCredentialEntity> findByCredentialTypeAndProviderNameIgnoreCase(
            SystemCredentialType credentialType, String providerName);

    Optional<SystemApiCredentialEntity> findFirstByCredentialTypeAndActiveTrueAndEnabledTrue(SystemCredentialType credentialType);

    Optional<SystemApiCredentialEntity> findFirstByCredentialTypeAndProviderNameIgnoreCaseAndActiveTrueAndEnabledTrue(
            SystemCredentialType credentialType, String providerName);
}
