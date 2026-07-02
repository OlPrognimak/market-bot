package com.prognimak.marketbot.trading.ibkr;

import com.prognimak.marketbot.trading.config.TradingProperties;
import com.prognimak.marketbot.trading.model.IbkrConnectionStatusResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class IbkrConnectionManager implements ApplicationRunner {
    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final TradingProperties properties;
    private Socket socket;
    private String state = "DISCONNECTED";
    private String message = "IBKR connection has not been opened.";
    private Instant connectedAt;
    private Instant disconnectedAt;

    @Override
    public synchronized void run(ApplicationArguments args) {
        if (properties.ibkr().enabled() && properties.ibkr().connectOnStartup()) {
            connect();
        }
    }

    public synchronized IbkrConnectionStatusResponse connect() {
        if (!properties.ibkr().enabled()) {
            state = "DISABLED";
            message = "IBKR integration is disabled.";
            return status();
        }
        if (isConnected()) {
            return status();
        }

        closeQuietly();
        try {
            Socket nextSocket = new Socket();
            nextSocket.connect(new InetSocketAddress(properties.ibkr().host(), properties.ibkr().port()), CONNECT_TIMEOUT_MS);
            socket = nextSocket;
            state = "CONNECTED";
            connectedAt = Instant.now();
            message = "Socket connection to TWS / IB Gateway is open.";
            log.info("Connected to IBKR socket at {}:{} with client id {}.",
                    properties.ibkr().host(), properties.ibkr().port(), properties.ibkr().clientId());
        } catch (IOException e) {
            state = "FAILED";
            disconnectedAt = Instant.now();
            message = "Could not connect to TWS / IB Gateway: " + e.getMessage();
            log.warn("Could not connect to IBKR socket at {}:{}: {}",
                    properties.ibkr().host(), properties.ibkr().port(), e.getMessage());
        }
        return status();
    }

    public synchronized IbkrConnectionStatusResponse disconnect() {
        closeQuietly();
        state = properties.ibkr().enabled() ? "DISCONNECTED" : "DISABLED";
        disconnectedAt = Instant.now();
        message = "IBKR socket connection is closed.";
        return status();
    }

    public synchronized IbkrConnectionStatusResponse status() {
        boolean connected = isConnected();
        String effectiveState = properties.ibkr().enabled() ? state : "DISABLED";
        if (properties.ibkr().enabled() && !connected && "CONNECTED".equals(state)) {
            effectiveState = "DISCONNECTED";
            message = "IBKR socket is no longer connected.";
            disconnectedAt = Instant.now();
        }
        return new IbkrConnectionStatusResponse(
                properties.ibkr().enabled(),
                properties.ibkr().host(),
                properties.ibkr().port(),
                properties.ibkr().clientId(),
                properties.ibkr().accountId(),
                properties.ibkr().paperAccountOnly(),
                connected,
                effectiveState,
                message,
                connectedAt,
                disconnectedAt
        );
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @PreDestroy
    public synchronized void shutdown() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing during shutdown or reconnect should not mask the requested action.
        } finally {
            socket = null;
        }
    }
}
