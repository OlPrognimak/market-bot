package com.prognimak.marketbot.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserPropertyRequest(
        @NotNull UserPropertyType propertyType,
        @NotBlank @Size(max = 160) String propertyName,
        @Size(max = 2000) String propertyValue,
        @Size(max = 500) String description,
        @NotNull UserPropertyValueType propertyValueType
) {
}
