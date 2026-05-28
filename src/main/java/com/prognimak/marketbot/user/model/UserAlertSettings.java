package com.prognimak.marketbot.user.model;

public record UserAlertSettings(
        double rollingThreshold,
        double deltaThreshold
) {
}
