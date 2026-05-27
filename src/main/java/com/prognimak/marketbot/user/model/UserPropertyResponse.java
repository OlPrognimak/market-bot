package com.prognimak.marketbot.user.model;

public record UserPropertyResponse(
        Long id,
        Long userId,
        UserPropertyType propertyType,
        String propertyName,
        String propertyValue,
        boolean enabled,
        String description,
        UserPropertyValueType propertyValueType
) {
}
