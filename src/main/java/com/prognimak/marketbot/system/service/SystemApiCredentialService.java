package com.prognimak.marketbot.system.service;

import com.prognimak.marketbot.system.entity.SystemApiCredentialEntity;
import com.prognimak.marketbot.system.entity.SystemCredentialType;
import com.prognimak.marketbot.system.model.SystemApiCredentialRequest;
import com.prognimak.marketbot.system.model.SystemApiCredentialResponse;
import com.prognimak.marketbot.system.repository.SystemApiCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SystemApiCredentialService {
    private final SystemApiCredentialRepository repository;

    @Transactional(readOnly = true)
    public List<SystemApiCredentialResponse> list() {
        return repository.findAllByOrderByCredentialTypeAscProviderNameAscDisplayNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String activeSecret(SystemCredentialType type) {
        return repository.findFirstByCredentialTypeAndActiveTrueAndEnabledTrue(type)
                .map(SystemApiCredentialEntity::getSecretValue)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public String activeSecret(SystemCredentialType type, String providerName) {
        return repository.findFirstByCredentialTypeAndProviderNameIgnoreCaseAndActiveTrueAndEnabledTrue(type, providerName)
                .map(SystemApiCredentialEntity::getSecretValue)
                .orElse(null);
    }

    @Transactional
    public SystemApiCredentialResponse save(SystemApiCredentialRequest request) {
        SystemApiCredentialEntity entity = new SystemApiCredentialEntity();
        apply(entity, request);
        if (entity.isActive()) {
            deactivateOtherCredentials(entity.getCredentialType(), entity.getProviderName(), null);
        }
        return toResponse(repository.save(entity));
    }

    @Transactional
    public SystemApiCredentialResponse update(Long id, SystemApiCredentialRequest request) {
        SystemApiCredentialEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));
        apply(entity, request);
        if (entity.isActive()) {
            deactivateOtherCredentials(entity.getCredentialType(), entity.getProviderName(), entity.getId());
        }
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found");
        }
        repository.deleteById(id);
    }

    private void apply(SystemApiCredentialEntity entity, SystemApiCredentialRequest request) {
        entity.setCredentialType(request.credentialType());
        entity.setProviderName(normalize(request.providerName()));
        entity.setDisplayName(request.displayName().trim());
        if (request.secretValue() != null && !request.secretValue().isBlank()) {
            entity.setSecretValue(request.secretValue().trim());
        }
        entity.setEnabled(request.enabled());
        entity.setActive(request.active());
        entity.setDescription(blankToNull(request.description()));
    }

    private void deactivateOtherCredentials(SystemCredentialType type, String providerName, Long keepId) {
        List<SystemApiCredentialEntity> candidates = type == SystemCredentialType.AI_PROVIDER
                ? repository.findByCredentialType(type)
                : repository.findByCredentialTypeAndProviderNameIgnoreCase(type, providerName);
        candidates.stream()
                .filter(item -> keepId == null || !item.getId().equals(keepId))
                .filter(SystemApiCredentialEntity::isActive)
                .forEach(item -> {
                    item.setActive(false);
                    repository.save(item);
                });
    }

    private SystemApiCredentialResponse toResponse(SystemApiCredentialEntity entity) {
        return new SystemApiCredentialResponse(
                entity.getId(),
                entity.getCredentialType(),
                entity.getProviderName(),
                entity.getDisplayName(),
                mask(entity.getSecretValue()),
                entity.getSecretValue() != null && !entity.getSecretValue().isBlank(),
                entity.isEnabled(),
                entity.isActive(),
                entity.getDescription()
        );
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
