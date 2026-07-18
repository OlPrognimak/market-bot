package com.prognimak.marketbot.notification;

import com.prognimak.marketbot.client.TelegramClient;
import com.prognimak.marketbot.user.model.UserMessengerSettings;
import com.prognimak.marketbot.user.service.UserPropertyService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRouterTest {

    @Test
    void telegramFailureDoesNotEscapeRouter() {
        UserPropertyService userPropertyService = mock(UserPropertyService.class);
        TelegramClient telegramClient = mock(TelegramClient.class);
        NotificationRouter router = new NotificationRouter(userPropertyService, telegramClient);
        when(userPropertyService.loadMessengerSettings(1L)).thenReturn(new UserMessengerSettings(
                true,
                "12345",
                "token",
                false,
                null,
                null
        ));
        doThrow(new TelegramClient.TelegramDeliveryException("Telegram send failed: timed out", new RuntimeException()))
                .when(telegramClient).sendMessage("token", "12345", "message");

        boolean sent = router.send(1L, "message");

        assertFalse(sent);
        verify(telegramClient).sendMessage("token", "12345", "message");
    }
}
