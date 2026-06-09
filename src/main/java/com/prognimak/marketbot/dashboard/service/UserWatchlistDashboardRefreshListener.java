package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.user.service.UserWatchlistChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserWatchlistDashboardRefreshListener {
    private final MarketDashboardService marketDashboardService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refreshDashboard(UserWatchlistChangedEvent event) {
        marketDashboardService.refreshSnapshots();
        log.info("Dashboard watchlist refreshed after settings update for user {}.", event.userId());
    }
}
