package com.prognimak.marketbot.user.model;

public record UserMessengerSettings(
        boolean telegramEnabled,
        String telegramChatId,
        String telegramBotToken,
        boolean whatsAppEnabled,
        String whatsAppChatId,
        String whatsAppBotToken
) {
    public boolean hasAnyMessengerEnabled() {
        return telegramEnabled || whatsAppEnabled;
    }
}
