package com.prognimak.marketbot.user.service;

import com.prognimak.marketbot.entity.AppUserPropertyEntity;
import com.prognimak.marketbot.entity.AppUserEntity;
import com.prognimak.marketbot.user.model.UserPropertyResponse;
import com.prognimak.marketbot.user.model.UserResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

@Component
public class UserMapper {
    public UserResponse toResponse(AppUserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                new LinkedHashMap<>(user.getMetadata()),
                toPropertyResponses(user.getProperties()),
                user.getCreated(),
                user.getModified()
        );
    }

    private List<UserPropertyResponse> toPropertyResponses(List<AppUserPropertyEntity> properties) {
        return properties.stream()
                .map(property -> new UserPropertyResponse(
                        property.getId(),
                        property.getUser().getId(),
                        property.getPropertyType(),
                        property.getPropertyName(),
                        property.getPropertyValue(),
                        property.getDescription(),
                        property.getPropertyValueType()
                ))
                .toList();
    }
}
