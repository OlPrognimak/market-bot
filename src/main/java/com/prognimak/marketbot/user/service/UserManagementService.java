package com.prognimak.marketbot.user.service;

import com.prognimak.marketbot.entity.AppUserPropertyEntity;
import com.prognimak.marketbot.entity.AppUserEntity;
import com.prognimak.marketbot.repository.AppUserRepository;
import com.prognimak.marketbot.security.UserRole;
import com.prognimak.marketbot.user.model.CreateUserRequest;
import com.prognimak.marketbot.user.model.SignUpRequest;
import com.prognimak.marketbot.user.model.UpdateUserRequest;
import com.prognimak.marketbot.user.model.UserPropertyRequest;
import com.prognimak.marketbot.user.model.UserPropertyValueType;
import com.prognimak.marketbot.user.model.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserManagementService {
    private static final Set<com.prognimak.marketbot.user.model.UserPropertyType> REPLACED_PROPERTY_TYPES = EnumSet.of(
            com.prognimak.marketbot.user.model.UserPropertyType.WATCHLIST,
            com.prognimak.marketbot.user.model.UserPropertyType.CRYPTO_COIN
    );

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(AppUserEntity::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(userMapper::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        validateUsernameAvailable(request.username(), null);
        validateEmailAvailable(request.email(), null);

        AppUserEntity user = new AppUserEntity();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim());
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        user.setMetadata(normalizeMetadata(request.metadata()));
        replaceProperties(user, request.properties());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public AppUserEntity signUp(SignUpRequest request) {
        validateUsernameAvailable(request.username(), null);
        validateEmailAvailable(request.email(), null);

        AppUserEntity user = new AppUserEntity();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim());
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user.setMetadata(normalizeMetadata(request.metadata()));
        replaceProperties(user, request.properties());
        return userRepository.save(user);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        AppUserEntity user = findUser(id);
        validateUsernameAvailable(request.username(), id);
        validateEmailAvailable(request.email(), id);

        user.setUsername(request.username().trim());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim());
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        ensureAdminRemains(user);
        user.getMetadata().clear();
        user.getMetadata().putAll(normalizeMetadata(request.metadata()));
        replaceProperties(user, request.properties());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateSelf(Long id, UpdateUserRequest request) {
        AppUserEntity user = findUser(id);
        validateUsernameAvailable(request.username(), id);
        validateEmailAvailable(request.email(), id);

        user.setUsername(request.username().trim());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim());
        user.getMetadata().clear();
        user.getMetadata().putAll(normalizeMetadata(request.metadata()));
        replaceProperties(user, request.properties());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        AppUserEntity user = findUser(id);
        if (isLastEnabledAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete the last enabled admin user");
        }
        userRepository.delete(user);
    }

    private AppUserEntity findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void validateUsernameAvailable(String username, Long currentUserId) {
        userRepository.findByUsernameIgnoreCase(username.trim())
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
                });
    }

    private void validateEmailAvailable(String email, Long currentUserId) {
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
                });
    }

    private void ensureAdminRemains(AppUserEntity changedUser) {
        if (isLastEnabledAdmin(changedUser)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove or disable the last enabled admin user");
        }
    }

    private boolean isLastEnabledAdmin(AppUserEntity changedUser) {
        if (changedUser.getRole().name().equals("ADMIN") && changedUser.isEnabled()) {
            return false;
        }

        return userRepository.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(changedUser.getId()))
                .filter(candidate -> candidate.getRole().name().equals("ADMIN"))
                .filter(AppUserEntity::isEnabled)
                .count() == 0;
    }

    private Map<String, String> normalizeMetadata(Map<String, String> metadata) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (metadata == null) {
            return normalized;
        }

        metadata.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                normalized.put(key.trim(), value.trim());
            }
        });
        return normalized;
    }

    private void replaceProperties(AppUserEntity user, List<UserPropertyRequest> properties) {
        if (properties == null) {
            return;
        }

        Map<Long, AppUserPropertyEntity> existingById = user.getProperties().stream()
                .filter(property -> property.getId() != null)
                .collect(LinkedHashMap::new, (map, property) -> map.put(property.getId(), property), Map::putAll);
        Map<String, AppUserPropertyEntity> existingByKey = user.getProperties().stream()
                .collect(LinkedHashMap::new, (map, property) -> map.put(propertyKey(property), property), Map::putAll);

        Set<String> seen = new HashSet<>();
        properties.stream()
                .filter(property -> property.propertyName() != null && !property.propertyName().isBlank())
                .filter(property -> property.propertyType() != null)
                .filter(property -> seen.add(property.propertyType().name() + "\n" + property.propertyName().trim().toLowerCase()))
                .forEach(property -> {
                    AppUserPropertyEntity entity = resolveProperty(user, property, existingById, existingByKey);
                    applyProperty(entity, property);
                });

        user.getProperties().removeIf(property ->
                REPLACED_PROPERTY_TYPES.contains(property.getPropertyType())
                        && !seen.contains(propertyKey(property))
        );
    }

    private AppUserPropertyEntity resolveProperty(
            AppUserEntity user,
            UserPropertyRequest property,
            Map<Long, AppUserPropertyEntity> existingById,
            Map<String, AppUserPropertyEntity> existingByKey
    ) {
        AppUserPropertyEntity entity = property.id() == null ? null : existingById.get(property.id());
        if (entity == null) {
            entity = existingByKey.get(propertyKey(property));
        }
        if (entity != null) {
            return entity;
        }

        AppUserPropertyEntity newEntity = new AppUserPropertyEntity();
        newEntity.setUser(user);
        user.getProperties().add(newEntity);
        return newEntity;
    }

    private void applyProperty(AppUserPropertyEntity entity, UserPropertyRequest property) {
        entity.setPropertyType(property.propertyType());
        entity.setPropertyName(property.propertyName().trim());
        entity.setPropertyValue(trimToNull(property.propertyValue()));
        entity.setEnabled(property.enabled() == null || property.enabled());
        entity.setDescription(trimToNull(property.description()));
        entity.setPropertyValueType(property.propertyValueType() == null ? UserPropertyValueType.TEXT : property.propertyValueType());
    }

    private String propertyKey(AppUserPropertyEntity property) {
        return property.getPropertyType().name() + "\n" + property.getPropertyName().trim().toLowerCase();
    }

    private String propertyKey(UserPropertyRequest property) {
        return property.propertyType().name() + "\n" + property.propertyName().trim().toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
