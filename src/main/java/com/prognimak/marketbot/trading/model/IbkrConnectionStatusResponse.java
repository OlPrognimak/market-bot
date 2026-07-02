package com.prognimak.marketbot.trading.model;

import java.time.Instant;

public record IbkrConnectionStatusResponse(
        boolean enabled,
        String host,
        int port,
        int clientId,
        String accountId,
        boolean paperAccountOnly,
        boolean connected,
        String state,
        String message,
        Instant connectedAt,
        Instant disconnectedAt
) {
}
