package com.prognimak.marketbot.trading.api;

import com.prognimak.marketbot.security.AppUserPrincipal;
import com.prognimak.marketbot.trading.model.*;
import com.prognimak.marketbot.trading.service.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradingController {
    private final TradingService tradingService;

    @GetMapping("/status")
    public TradingStatusResponse status(@AuthenticationPrincipal AppUserPrincipal principal) {
        requireUser(principal);
        return tradingService.status();
    }

    @GetMapping("/ibkr/status")
    public IbkrConnectionStatusResponse ibkrStatus(@AuthenticationPrincipal AppUserPrincipal principal) {
        requireUser(principal);
        return tradingService.ibkrStatus();
    }

    @PostMapping("/ibkr/connect")
    public IbkrConnectionStatusResponse connectIbkr(@AuthenticationPrincipal AppUserPrincipal principal) {
        requireUser(principal);
        return tradingService.connectIbkr();
    }

    @PostMapping("/ibkr/disconnect")
    public IbkrConnectionStatusResponse disconnectIbkr(@AuthenticationPrincipal AppUserPrincipal principal) {
        requireUser(principal);
        return tradingService.disconnectIbkr();
    }

    @GetMapping("/positions")
    public List<PositionDto> positions(@AuthenticationPrincipal AppUserPrincipal principal) {
        return tradingService.positions(requireUser(principal));
    }

    @GetMapping("/positions/{symbol}")
    public PositionDto position(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String symbol) {
        try {
            return tradingService.position(requireUser(principal), symbol);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PostMapping("/orders/preview")
    public TradeOrderPreview preview(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody TradeOrderRequest request) {
        try {
            return tradingService.preview(requireUser(principal), request);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/orders")
    public TradeOrderResult submit(@AuthenticationPrincipal AppUserPrincipal principal, @RequestBody TradeOrderRequest request) {
        try {
            return tradingService.submit(requireUser(principal), request);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/positions/{symbol}/close/preview")
    public TradeOrderPreview closePreview(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String symbol,
            @RequestParam(required = false) String currency
    ) {
        try {
            return tradingService.closePreview(requireUser(principal), symbol, currency);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/orders")
    public List<TradeOrderResult> orders(@AuthenticationPrincipal AppUserPrincipal principal) {
        return tradingService.orders(requireUser(principal));
    }

    @GetMapping("/orders/{orderId}")
    public TradeOrderResult order(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long orderId) {
        try {
            return tradingService.order(requireUser(principal), orderId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    private Long requireUser(AppUserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.user().getId();
    }
}
