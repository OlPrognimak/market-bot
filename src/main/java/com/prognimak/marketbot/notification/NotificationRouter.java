package com.prognimak.marketbot.notification;

import com.prognimak.marketbot.client.TelegramClient;
import com.prognimak.marketbot.user.model.UserMessengerSettings;
import com.prognimak.marketbot.user.service.UserPropertyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRouter {
    private final UserPropertyService userPropertyService;
    private final TelegramClient telegramClient;

    public boolean send(Long userId, String messageText) {
        UserMessengerSettings settings = userPropertyService.loadMessengerSettings(userId);
        if (!settings.hasAnyMessengerEnabled()) {
            log.info("No messenger enabled for user {}. Notification skipped.", userId);
            return false;
        }

        boolean sent = false;
        if (settings.telegramEnabled()) {
            if (settings.telegramBotToken() == null || settings.telegramBotToken().isBlank()
                    || settings.telegramChatId() == null || settings.telegramChatId().isBlank()) {
                log.warn("Telegram is enabled for user {} but token or chat id is missing.", userId);
            } else {
                try {
                    telegramClient.sendMessage(settings.telegramBotToken(), settings.telegramChatId(), messageText);
                    sent = true;
                } catch (Exception e) {
                    log.warn("Telegram send failed for user {}: {}", userId, e.getMessage(), e);
                }
            }
        }

        if (settings.whatsAppEnabled()) {
            log.warn("WhatsApp is enabled for user {} but sender implementation is not available yet.", userId);
        }

        return sent;
    }
}
