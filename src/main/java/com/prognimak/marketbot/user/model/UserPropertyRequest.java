package com.prognimak.marketbot.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserPropertyRequest(
        Long id,
        @NotNull UserPropertyType propertyType,
        @NotBlank @Size(max = 160) String propertyName,
        @Size(max = 255) String propertyValue,
        Boolean enabled,
        @Size(max = 255) String description,
        @NotNull UserPropertyValueType propertyValueType
) {
}
