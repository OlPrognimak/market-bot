package com.prognimak.marketbot.notification;

import com.prognimak.marketbot.entity.CryptoUserSymbolAlertStateEntity;
import com.prognimak.marketbot.entity.UserSymbolAlertStateEntity;
import com.prognimak.marketbot.repository.AppUserRepository;
import com.prognimak.marketbot.repository.CryptoQuoteRepository;
import com.prognimak.marketbot.repository.CryptoUserSymbolAlertStateRepository;
import com.prognimak.marketbot.repository.QuoteRepository;
import com.prognimak.marketbot.repository.UserSymbolAlertStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncNotificationService {
    private final ExecutorService notificationExecutor;
    private final NotificationRouter notificationRouter;
    private final TransactionTemplate transactionTemplate;
    private final Semaphore notificationSendSemaphore;
    private final AppUserRepository appUserRepository;
    private final QuoteRepository quoteRepository;
    private final CryptoQuoteRepository cryptoQuoteRepository;
    private final UserSymbolAlertStateRepository alertStateRepository;
    private final CryptoUserSymbolAlertStateRepository cryptoAlertStateRepository;

    public boolean sendShareAlert(Long userId, String symbol, Long quoteId, String messageText) {
        return submit("share", userId, symbol, quoteId, () -> {
            if (notificationRouter.send(userId, messageText)) {
                markShareAlertSent(userId, symbol, quoteId);
            }
        });
    }

    public boolean sendCryptoAlert(Long userId, String symbol, Long cryptoQuoteId, String messageText) {
        return submit("crypto", userId, symbol, cryptoQuoteId, () -> {
            if (notificationRouter.send(userId, messageText)) {
                markCryptoAlertSent(userId, symbol, cryptoQuoteId);
            }
        });
    }

    private boolean submit(String type, Long userId, String symbol, Long quoteId, Runnable task) {
        if (userId == null || quoteId == null) {
            log.warn("Cannot queue {} alert for user {} and symbol {} because id data is missing.", type, userId, symbol);
            return false;
        }

        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        execute(type, userId, symbol, task);
                    }
                });
            } else {
                execute(type, userId, symbol, task);
            }
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("Cannot queue {} alert for user {} and symbol {}: {}", type, userId, symbol, e.getMessage(), e);
            return false;
        }
    }

    private void execute(String type, Long userId, String symbol, Runnable task) {
        try {
            notificationExecutor.execute(() -> {
                boolean acquired = false;
                try {
                    notificationSendSemaphore.acquire();
                    acquired = true;
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("{} alert send interrupted for user {} and symbol {}.", type, userId, symbol);
                } catch (Exception e) {
                    log.warn("{} alert send failed for user {} and symbol {}: {}", type, userId, symbol, e.getMessage(), e);
                } finally {
                    if (acquired) {
                        notificationSendSemaphore.release();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Cannot queue {} alert for user {} and symbol {}: {}", type, userId, symbol, e.getMessage(), e);
        }
    }

    private synchronized void markShareAlertSent(Long userId, String symbol, Long quoteId) {
        transactionTemplate.executeWithoutResult(status -> {
            var user = appUserRepository.findById(userId);
            var quote = quoteRepository.findById(quoteId);
            if (user.isEmpty() || quote.isEmpty()) {
                log.warn("Share alert was sent but state was not saved. userId={}, symbol={}, quoteId={}",
                        userId, symbol, quoteId);
                return;
            }

            UserSymbolAlertStateEntity state = alertStateRepository
                    .findByUserIdAndSymbolIgnoreCase(userId, symbol)
                    .orElseGet(UserSymbolAlertStateEntity::new);
            state.setUser(user.get());
            state.setSymbol(symbol);
            state.setLastSentQuote(quote.get());
            state.setLastSentAt(Instant.now());
            alertStateRepository.save(state);
        });
    }

    private synchronized void markCryptoAlertSent(Long userId, String symbol, Long cryptoQuoteId) {
        transactionTemplate.executeWithoutResult(status -> {
            var user = appUserRepository.findById(userId);
            var quote = cryptoQuoteRepository.findById(cryptoQuoteId);
            if (user.isEmpty() || quote.isEmpty()) {
                log.warn("Crypto alert was sent but state was not saved. userId={}, symbol={}, cryptoQuoteId={}",
                        userId, symbol, cryptoQuoteId);
                return;
            }

            CryptoUserSymbolAlertStateEntity state = cryptoAlertStateRepository
                    .findByUserIdAndSymbolIgnoreCase(userId, symbol)
                    .orElseGet(CryptoUserSymbolAlertStateEntity::new);
            state.setUser(user.get());
            state.setSymbol(symbol);
            state.setLastSentQuote(quote.get());
            state.setLastSentAt(Instant.now());
            cryptoAlertStateRepository.save(state);
        });
    }
}
