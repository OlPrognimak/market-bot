# Interactive Brokers Manual Trading Concept

## Purpose

Add Interactive Brokers integration to market-bot for **manual dashboard trading**.

This concept is intentionally conservative:

- No automatic trading.
- No real order by default.
- Paper/simulation mode is the default.
- Live IBKR execution is disabled unless explicitly enabled by configuration.
- Every order requires an explicit user action and confirmation.
- Every order goes through risk checks before submission.

The original prompt is mostly valid, but it needs tightening for this project in several places:

- “One-click trading” should mean quick action buttons plus mandatory preview/confirmation, not immediate live execution.
- Paper orders should be stored in PostgreSQL, not memory, because the app already relies on persisted quote and portfolio history.
- IBKR symbol/contract mapping must be explicit because market-bot already has provider-specific symbol mapping.
- IBKR TWS API must be isolated behind an adapter so the app can start normally without TWS or IB Gateway running.
- Live trading controls should be admin-visible and disabled by default.

## Official IBKR API Direction

Use the current IBKR Campus TWS API documentation as the source of truth.

Relevant facts from official docs:

- TWS API supports Java and connects through Trader Workstation or IB Gateway.
- The API covers connection lifecycle, account/portfolio data, positions, order placement, order status, executions, market data, and error handling.
- The old `interactivebrokers.github.io/tws-api` documentation is deprecated and points users to IBKR Campus.
- TWS / IB Gateway authentication and reauthentication are operational requirements, not something the backend can fully hide.

References:

- IBKR Campus TWS API documentation: https://www.interactivebrokers.com/campus/ibkr-api-page/twsapi-doc/
- Deprecated old TWS API docs: https://interactivebrokers.github.io/tws-api/

## Scope

### In Scope

- Paper trading mode.
- Manual dashboard trade buttons.
- Order preview endpoint.
- Order confirmation endpoint.
- Risk checks before order submission.
- PostgreSQL persistence for simulated orders, executions, and paper positions.
- IBKR service skeleton behind feature flags.
- Optional live IBKR execution only when explicitly enabled.
- Visible error handling for rejected or failed orders.
- Unit tests for risk and paper broker behavior.

### Out of Scope

- Fully automatic trading.
- Background order execution from scanners or alerts.
- Strategy engine.
- Margin trading.
- Options, futures, forex, crypto, and complex IBKR order types in the first version.
- Short selling in the first version.
- Hidden retries for live orders.

## Recommended Rollout

### Phase 1: Paper Trading Only

Implement the complete trading API and UI against `PaperBrokerService`.

This phase should be usable without IBKR account, TWS, or IB Gateway.

Deliverables:

- Trading domain model.
- Trading database tables.
- Risk manager.
- Paper broker.
- REST endpoints.
- Dashboard buttons.
- Confirmation modal.
- Trading status UI.
- Tests.

### Phase 2: IBKR Connection Skeleton

Add `IbkrBrokerService` with connection lifecycle only.

Deliverables:

- TWS / IB Gateway connection configuration.
- Connect/disconnect lifecycle.
- Connection status endpoint.
- Error handling and logs.
- No live order submission yet, unless separately enabled.

### Phase 3: IBKR Paper Account Execution

Allow IBKR paper account order submission only when:

- `trading.mode=LIVE`
- `trading.live-enabled=true`
- `trading.ibkr.enabled=true`
- `trading.ibkr.paper-account-only=true`
- current TWS / IB Gateway port is paper port
- manual confirmation is present

The naming is intentionally strict: even though IBKR calls it a paper account, the application is now submitting to a real broker API, so it must be treated as external execution.

### Phase 4: Real Live Execution

Only after paper account execution is stable:

- Allow real account execution.
- Require explicit production configuration.
- Add extra UI warnings.
- Add audit logging.
- Add a startup warning when live trading is enabled.

## Backend Package Structure

Use a separate trading module:

```text
com.prognimak.marketbot.trading
com.prognimak.marketbot.trading.api
com.prognimak.marketbot.trading.config
com.prognimak.marketbot.trading.entity
com.prognimak.marketbot.trading.ibkr
com.prognimak.marketbot.trading.model
com.prognimak.marketbot.trading.paper
com.prognimak.marketbot.trading.repository
com.prognimak.marketbot.trading.risk
com.prognimak.marketbot.trading.service
```

This avoids mixing broker execution with existing scanner, portfolio import, and dashboard code.

## Domain Model

### Enums

```java
public enum TradingMode {
    PAPER,
    LIVE
}

public enum OrderSide {
    BUY,
    SELL
}

public enum OrderType {
    MARKET,
    LIMIT
}

public enum TradeOrderStatus {
    PREVIEWED,
    REJECTED,
    SUBMITTED,
    FILLED,
    PARTIALLY_FILLED,
    CANCELLED,
    FAILED
}

public enum BrokerType {
    PAPER,
    IBKR
}
```

### TradeOrderRequest

```java
public record TradeOrderRequest(
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal amount,
        OrderType orderType,
        BigDecimal limitPrice,
        String currency,
        String exchange,
        BigDecimal stopLossPercent,
        BigDecimal takeProfitPercent,
        String confirmationToken
) {
}
```

Notes:

- `quantity` or `amount` is required.
- For fixed-amount buy buttons, frontend sends `amount`.
- For sell buttons, frontend sends quantity or a close instruction.
- `confirmationToken` should be required for final submit after preview.

### TradeOrderPreview

Add this model. It is missing from the original prompt but necessary.

```java
public record TradeOrderPreview(
        String previewId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        BigDecimal currentPrice,
        BigDecimal estimatedQuantity,
        BigDecimal estimatedValue,
        String currency,
        TradingMode tradingMode,
        boolean allowed,
        List<String> warnings,
        List<String> rejectionReasons,
        String confirmationToken,
        Instant expiresAt
) {
}
```

The frontend should not submit an order without a fresh preview.

### TradeOrderResult

```java
public record TradeOrderResult(
        Long orderId,
        String brokerOrderId,
        String symbol,
        OrderSide side,
        TradeOrderStatus status,
        Instant submittedAt,
        String message
) {
}
```

### PositionDto

```java
public record PositionDto(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPercent,
        String currency,
        BrokerType brokerType
) {
}
```

## Broker Abstraction

The proposed interface is valid but should include user context and preview support.

```java
public interface BrokerService {
    TradeOrderPreview previewOrder(Long userId, TradeOrderRequest request);
    TradeOrderResult placeOrder(Long userId, TradeOrderRequest request);
    List<PositionDto> getPositions(Long userId);
    Optional<PositionDto> getPosition(Long userId, String symbol);
    TradeOrderResult closePosition(Long userId, String symbol);
    boolean isConnected();
    BrokerType brokerType();
}
```

Reason:

- market-bot already has authenticated users.
- Trading state must be user-specific.
- Paper positions must not be shared across users.

## Broker Routing

Add a `TradingService` facade.

Responsibilities:

- Choose broker based on config.
- Always route to paper broker in default mode.
- Call `RiskManager`.
- Persist order attempt.
- Return user-visible result.

Routing rule:

| Config | Effective broker |
| --- | --- |
| `trading.mode=PAPER` | `PaperBrokerService` |
| `trading.mode=LIVE`, `live-enabled=false` | reject |
| `trading.mode=LIVE`, `live-enabled=true`, `ibkr.enabled=false` | reject |
| `trading.mode=LIVE`, `live-enabled=true`, `ibkr.enabled=true` | `IbkrBrokerService` |

## PaperBrokerService

Paper broker should be the default implementation.

Requirements:

- Never sends real orders.
- Persists orders in PostgreSQL.
- Persists executions in PostgreSQL.
- Persists or derives paper positions.
- Uses latest quote from existing quote repository / quote service.
- Supports:
  - `BUY` by amount
  - `BUY` by quantity
  - `SELL` by quantity
  - `SELL 50%`
  - `SELL ALL`
- Calculates average price.
- Calculates realized P/L for paper sells.
- Calculates unrealized P/L from latest quote.
- Uses deterministic execution model in first version:
  - Market buy/sell executes at latest current price.
  - Limit order fills only if latest price satisfies limit condition.

Memory-only paper trading is not recommended because:

- Positions disappear after restart.
- Tests and analysis become less reliable.
- Users cannot audit actions.

## IbkrBrokerService

`IbkrBrokerService` must be disabled by default.

Configuration gates:

- `trading.mode=LIVE`
- `trading.live-enabled=true`
- `trading.ibkr.enabled=true`

Responsibilities:

- Connect to TWS / IB Gateway.
- Manage connection lifecycle.
- Handle `nextValidId`.
- Map market-bot symbol to IBKR contract.
- Place supported order types.
- Track order status callbacks.
- Request positions.
- Surface API errors to UI.
- Never retry live orders blindly.

Important implementation note:

The official TWS Java API uses an asynchronous socket model with wrapper callbacks and an `EReader` thread. The IBKR adapter should isolate this complexity from the rest of the application.

Recommended internal classes:

```text
IbkrClientAdapter
IbkrConnectionManager
IbkrContractFactory
IbkrOrderFactory
IbkrOrderStatusStore
IbkrErrorMapper
```

## IBKR Contract Mapping

Do not assume the market-bot display symbol is enough for IBKR.

Add explicit contract mapping fields:

- `marketSymbol`
- `ibkrSymbol`
- `secType`, first version `STK`
- `currency`
- `exchange`, default `SMART`
- `primaryExchange`
- `conId`, optional but preferred when known

This can reuse or extend the existing provider symbol mapping concept, but IBKR contract metadata is richer than Yahoo/Revolut mapping.

Example:

| Market symbol | IBKR symbol | Sec type | Currency | Exchange | Primary exchange |
| --- | --- | --- | --- | --- | --- |
| `AAPL` | `AAPL` | `STK` | `USD` | `SMART` | `NASDAQ` |
| `SIE.DE` | `SIE` | `STK` | `EUR` | `SMART` | `IBIS` |

Without this mapping, European shares and ADRs can be misrouted.

## Configuration

Add a dedicated trading config block.

```yaml
trading:
  enabled: ${MARKET_BOT_TRADING_ENABLED:false}
  mode: ${MARKET_BOT_TRADING_MODE:PAPER}
  live-enabled: ${MARKET_BOT_TRADING_LIVE_ENABLED:false}
  require-manual-confirmation: ${MARKET_BOT_TRADING_REQUIRE_MANUAL_CONFIRMATION:true}
  max-order-value-eur: ${MARKET_BOT_TRADING_MAX_ORDER_VALUE_EUR:500}
  max-daily-loss-eur: ${MARKET_BOT_TRADING_MAX_DAILY_LOSS_EUR:100}
  max-open-orders-per-symbol: ${MARKET_BOT_TRADING_MAX_OPEN_ORDERS_PER_SYMBOL:1}
  max-spread-percent: ${MARKET_BOT_TRADING_MAX_SPREAD_PERCENT:1.0}
  preview-ttl-seconds: ${MARKET_BOT_TRADING_PREVIEW_TTL_SECONDS:60}
  allow-short-selling: ${MARKET_BOT_TRADING_ALLOW_SHORT_SELLING:false}
  allowed-order-types:
    - MARKET
    - LIMIT
  ibkr:
    enabled: ${MARKET_BOT_TRADING_IBKR_ENABLED:false}
    host: ${MARKET_BOT_TRADING_IBKR_HOST:127.0.0.1}
    port: ${MARKET_BOT_TRADING_IBKR_PORT:7497}
    client-id: ${MARKET_BOT_TRADING_IBKR_CLIENT_ID:1}
    account-id: ${MARKET_BOT_TRADING_IBKR_ACCOUNT_ID:}
    paper-account-only: ${MARKET_BOT_TRADING_IBKR_PAPER_ACCOUNT_ONLY:true}
    connect-on-startup: ${MARKET_BOT_TRADING_IBKR_CONNECT_ON_STARTUP:false}
```

Notes:

- `7497` is commonly used for paper TWS.
- `7496` is commonly used for live TWS.
- Do not hardcode account IDs.
- Do not commit secrets.
- TWS API usually does not use an API key, but TWS / IB Gateway requires an authenticated local session.
- `trading.enabled=false` should hide UI buttons and reject endpoints.

## RiskManager

`RiskManager` is mandatory and should run for preview and submit.

Checks:

- Reject if trading is globally disabled.
- Reject if `mode=LIVE` and `live-enabled=false`.
- Reject if final submit has no valid confirmation token.
- Reject if request has neither quantity nor amount.
- Reject if amount and quantity conflict.
- Reject if order value exceeds `max-order-value-eur`.
- Reject if daily realized/unrealized paper loss exceeds `max-daily-loss-eur`.
- Reject buy if symbol is not in the user watchlist.
- Reject buy if no current quote exists.
- Reject if spread is too high when bid/ask is available.
- Reject duplicate pending order for the same user and symbol.
- Reject unsupported order type.
- Reject sell quantity larger than current position unless short selling is explicitly enabled.
- Reject live order if IBKR is disconnected.
- Reject live order if IBKR account id does not match configured account.
- Warn if quote is stale.
- Warn if market is closed and order is market order.

Risk result model:

```java
public record RiskCheckResult(
        boolean allowed,
        List<String> warnings,
        List<String> rejectionReasons
) {
}
```

Rejected orders should be persisted as rejected attempts.

## REST API

Create `TradingController` under:

```text
com.prognimak.marketbot.trading.api
```

Endpoints:

```text
GET  /api/trading/status
GET  /api/trading/positions
GET  /api/trading/positions/{symbol}
POST /api/trading/orders/preview
POST /api/trading/orders
POST /api/trading/positions/{symbol}/close/preview
POST /api/trading/positions/{symbol}/close
GET  /api/trading/orders
GET  /api/trading/orders/{orderId}
```

Keep `/preview` separate from `/orders`.

Final order submission must require:

- authenticated user
- fresh preview
- confirmation token
- same request values as preview

## Security

All trading endpoints must require authentication.

Recommended first version:

- Any authenticated user can use paper trading for their own account.
- Only admins can see or enable live trading settings.
- Live order submission should require `ADMIN` role at first.
- Later, add per-user permission such as `TRADING_LIVE_ALLOWED`.

Do not allow anonymous access to trading status if it reveals broker state.

## Database Design

Add tables:

```text
trade_order
trade_execution
paper_position
trade_risk_event
```

These tables should stay separate from the current portfolio import tables.

Reason:

- `portfolio_transaction`, `portfolio_realized_lot`, and `portfolio_income` represent imported broker history from files.
- `trade_order`, `trade_execution`, and `paper_position` represent app-created trading activity.
- Mixing them directly would make it too easy to confuse simulated paper trades with imported real broker activity.

Analyze can read from both sources through a service-level aggregation layer.

### trade_order

Fields:

- `id`
- `user_id`
- `broker_type`
- `trading_mode`
- `symbol`
- `side`
- `order_type`
- `requested_quantity`
- `requested_amount`
- `limit_price`
- `currency`
- `exchange`
- `status`
- `broker_order_id`
- `confirmation_token_hash`
- `created`
- `submitted_at`
- `completed_at`
- `message`
- `request_fingerprint`

### trade_execution

Fields:

- `id`
- `order_id`
- `user_id`
- `broker_execution_id`
- `symbol`
- `side`
- `quantity`
- `price`
- `gross_amount`
- `fees`
- `currency`
- `executed_at`

### paper_position

Fields:

- `id`
- `user_id`
- `symbol`
- `quantity`
- `avg_price`
- `currency`
- `realized_pnl`
- `created`
- `modified`

### trade_risk_event

Fields:

- `id`
- `user_id`
- `order_id`
- `symbol`
- `allowed`
- `warnings`
- `rejection_reasons`
- `created`

Use JSON columns for warnings/rejection reasons if convenient.

## Frontend Design

Current frontend is a Next.js single-page dashboard with view switching in `frontend/src/app/page.tsx`.

Add trading UI inside the shares dashboard first.

Buttons near each stock row:

- `BUY 100 EUR`
- `BUY 250 EUR`
- `SELL 50%`
- `SELL ALL`

Button behavior:

1. Call `/api/trading/orders/preview`.
2. Show confirmation modal.
3. Display:
   - symbol
   - current price
   - estimated quantity
   - estimated value
   - order side
   - order type
   - stop loss
   - take profit
   - max possible loss
   - warnings
   - rejection reasons
   - trading mode
4. Disable final submit if preview is not allowed.
5. If trading mode is `LIVE`, show a strong warning and require explicit confirmation text.
6. Submit to `/api/trading/orders`.

Default UI state:

- If `trading.enabled=false`, no trading buttons.
- If `trading.mode=PAPER`, label all buttons and modal as paper trading.
- If `trading.mode=LIVE`, show persistent visible warning.

Add a simple positions widget:

- current paper/live positions
- quantity
- average price
- current price
- unrealized P/L
- close button

## WebSocket Updates

Do not send orders through WebSocket.

Use WebSocket only for read-side dashboard updates:

- trading status changed
- paper position changed
- order status changed

Order creation must stay REST-based because it needs explicit request/response, validation, and audit behavior.

## Interaction With Existing Market-Bot Data

Use existing data where possible:

- Latest quote from `QuoteRepository`.
- Watchlist from `WatchlistService`.
- User identity from existing JWT authentication.
- Existing provider symbol mapping as a base for IBKR contract mapping.
- Dashboard snapshot for row-level trading buttons.
- Existing Analyze page concepts for current positions, filtered ticker results, realized P/L, and chart markers.

Do not modify:

- scanner scheduling
- Telegram alert sending
- chart data
- portfolio import
- existing dashboard WebSocket behavior

## Integration With Existing Analyze Page

The existing Analyze page already works with imported portfolio data from providers such as Revolut and Trade Republic.

Current Analyze concepts that should be reused:

- provider filter
- ticker filter
- period filter
- current positions
- realized profit/loss
- income
- selected ticker realized lots
- chart markers for buy/sell transactions

Trading integration should extend Analyze, not replace it.

### Provider Model

Extend provider handling with trading providers:

```text
REVOLUT
TRADE_REPUBLIC
PAPER_TRADING
IBKR
ALL
```

Important distinction:

- `PAPER_TRADING` means simulated orders created inside market-bot.
- `IBKR` means real broker executions received from IBKR API or later imported from IBKR statements.
- `ALL` aggregates imported portfolio providers plus trading providers.

The first implementation should include `PAPER_TRADING` in Analyze.

`IBKR` Analyze integration should wait until the IBKR execution synchronization model is clear.

### Data Flow

Recommended flow:

```text
PaperBrokerService
  -> trade_order / trade_execution / paper_position
  -> TradingAnalysisAdapter
  -> PortfolioAnalysisService aggregation
  -> /api/portfolio/analysis
  -> Analyze page
```

Do not write paper trades directly into `portfolio_transaction` in the first version.

Instead, create an adapter that converts trading executions into the same analysis shape used by portfolio imports.

### Backend Changes For Analyze

Add a service such as:

```java
public interface PortfolioAnalysisSource {
    PortfolioProviderType providerType();
    List<PortfolioTransactionLike> transactions(Long userId);
    List<PortfolioRealizedLotLike> realizedLots(Long userId);
    List<PortfolioIncomeLike> income(Long userId);
}
```

Then implement:

```text
ImportedPortfolioAnalysisSource
TradingPortfolioAnalysisSource
```

Alternative simpler first version:

- Keep `PortfolioAnalysisService` mostly unchanged.
- Add `TradingAnalysisService`.
- Merge trading positions/results into `PortfolioAnalysisResponse` after imported portfolio analysis is calculated.

The source abstraction is cleaner if more providers will be added.

### Analyze Response Changes

The current `PortfolioAnalysisResponse` can be extended with:

- `providerType=PAPER_TRADING`
- trading positions in existing `positions`
- trading realized P/L in existing `filteredTickerResults`
- trading buy/sell details in existing `selectedTickerRealizedLots` or a new `selectedTickerTradeExecutions`

Recommended first version:

- Add paper positions to `positions`.
- Add paper realized P/L to `filteredTickerResults`.
- Add paper executions to a new detail list:

```java
List<TradeExecutionDetail> selectedTickerTradeExecutions
```

Reason:

- Realized lots from imported statements and simulated executions are related, but not identical.
- Keeping trade executions visible separately makes debugging easier.

### Provider Filter Behavior

Analyze provider filter should work as follows:

| Filter | Included data |
| --- | --- |
| empty / `ALL` | Revolut, Trade Republic, Paper Trading, later IBKR |
| `REVOLUT` | imported Revolut only |
| `TRADE_REPUBLIC` | imported Trade Republic only |
| `PAPER_TRADING` | simulated market-bot orders only |
| `IBKR` | synchronized IBKR real executions only |

When the user selects `PAPER_TRADING`, the Analyze page should show:

- paper current positions
- paper buy/sell executions
- paper realized P/L
- paper unrealized P/L using latest quote
- paper order statuses where useful

### Period Filter Behavior

For trading data:

- Buy/sell execution rows are filtered by `executed_at`.
- Realized P/L is filtered by sell execution date.
- Current position remains current, but the page should clearly label it as current, not period-only.

This matches the existing Analyze behavior where current positions stay visible while realized results are period-filtered.

### Ticker Filter Behavior

Ticker filter should match:

- market-bot symbol
- IBKR symbol if different
- mapped provider symbol

Use the existing provider symbol mapping as the base, but add IBKR contract mapping where necessary.

Example:

```text
Analyze ticker SIE.DE
  -> imported Trade Republic ISIN if mapped
  -> Revolut ticker if mapped
  -> paper trade symbol SIE.DE
  -> IBKR contract symbol SIE / primary exchange IBIS
```

### Chart Marker Integration

The existing chart marker endpoint should include paper trade markers when the displayed market symbol matches the paper trade symbol.

Marker types:

- imported buy
- imported sell
- paper buy
- paper sell
- later IBKR buy
- later IBKR sell

UI should visually distinguish them:

| Source | Buy marker | Sell marker |
| --- | --- | --- |
| imported providers | existing colors |
| paper trading | lighter/dashed or different shape |
| IBKR live | strong color plus broker label |

Marker tooltip should include:

- source provider
- trading mode
- side
- quantity
- execution price
- gross value
- fees if known
- realized P/L for sell if available
- order status

### P/L Calculation Alignment

Paper trading P/L should follow the same economic logic as Analyze:

- Buy increases cost basis.
- Sell reduces position and realizes P/L.
- Fees reduce profit and increase loss.
- Current market value uses latest quote.
- Unrealized P/L is current market value minus remaining cost basis.

If paper trading uses no fees initially, store fees as zero explicitly.

When IBKR executions are later synchronized:

- Use IBKR commission/fees reports when available.
- Do not estimate real fees if broker-reported fees exist.

### UI Changes In Analyze

Add to Analyze page:

- provider filter option `Paper Trading`
- later provider filter option `IBKR`
- optional checkbox `Include paper trading`
- table section `Trading executions` when a ticker is selected
- clear labels for simulated vs imported data

Avoid showing paper trading as if it were real broker history.

Suggested labels:

```text
Provider: Paper Trading
Mode: Simulation
```

For `ALL`, aggregate values should show a warning or label when simulated data is included:

```text
Includes simulated paper trading data.
```

### Reconciliation With Imported Data

If the user later imports IBKR statements, there is a risk of double counting:

- IBKR API execution data
- IBKR CSV/statement import

Add a reconciliation strategy before enabling both.

Recommended rule:

- Paper trading never reconciles with imported broker files.
- Live IBKR API executions can be reconciled with later IBKR imports by broker execution id.
- If a matching imported execution exists, mark one source as reconciled and avoid double counting.

Add fields for future reconciliation:

- `broker_execution_id`
- `broker_order_id`
- `external_account_id`
- `reconciled_import_id`
- `reconciliation_status`

### Implementation Order For Analyze Integration

1. Add `PAPER_TRADING` provider option.
2. Add trading analysis adapter for paper executions and paper positions.
3. Extend `/api/portfolio/analysis` to optionally include paper trading data.
4. Extend provider filter in frontend.
5. Add `Trading executions` table for selected ticker.
6. Extend chart markers with paper buy/sell markers.
7. Add tests for provider filter, period filter, ticker filter, and P/L calculations.
8. Add IBKR Analyze support only after real execution synchronization is stable.

## Error Handling

All order attempts must produce clear results.

Examples:

- `TRADING_DISABLED`
- `LIVE_TRADING_DISABLED`
- `NO_CURRENT_QUOTE`
- `SYMBOL_NOT_IN_WATCHLIST`
- `ORDER_VALUE_TOO_HIGH`
- `DUPLICATE_PENDING_ORDER`
- `IBKR_DISCONNECTED`
- `IBKR_REJECTED_ORDER`
- `CONFIRMATION_REQUIRED`
- `PREVIEW_EXPIRED`

For IBKR errors:

- persist raw broker error code/message
- map to user-safe message
- show in UI
- do not retry automatically

## Testing Plan

Unit tests:

- `RiskManagerTest`
- `PaperBrokerServiceTest`
- `TradingServiceTest`
- `IbkrContractFactoryTest`
- `TradingAnalysisServiceTest`

Controller tests:

- blocked when unauthenticated
- blocked when trading disabled
- blocked live order when `live-enabled=false`
- preview returns rejection reasons
- paper order succeeds with valid confirmation

Integration tests:

- paper buy creates order, execution, and position
- paper sell updates position and realized P/L
- duplicate pending order rejected
- Analyze includes paper trading when provider filter is `PAPER_TRADING` or `ALL`
- Analyze excludes paper trading when provider filter is `REVOLUT` or `TRADE_REPUBLIC`

No automated test should require real TWS, IB Gateway, or live IBKR credentials.

## Dependency Strategy

The IBKR TWS Java API dependency should not be required for paper trading.

Recommended options:

1. Add IBKR API as an optional/local dependency only in the IBKR adapter module/package.
2. Keep IBKR adapter behind `@ConditionalOnProperty`.
3. If dependency management is difficult, create a small internal abstraction first and add the concrete IBKR dependency only in Phase 2.

The application must compile and run in paper mode without an active IBKR connection.

## README Section

Add documentation covering:

- how to enable paper trading UI
- how paper orders are simulated
- how to configure TWS / IB Gateway
- how to configure IBKR host, port, client id, and account id
- how to switch to live mode
- meaning of `7497` and `7496`
- warning that live trading uses real money
- statement that no automatic trading exists
- troubleshooting connection failures

## Revised Implementation Prompt

Use this as the safer implementation prompt:

```text
Implement market-bot manual trading integration in phases.

Project:
- Java / Spring Boot backend
- PostgreSQL
- JWT-authenticated users
- dashboard frontend
- WebSocket dashboard updates
- existing quote history, watchlists, charts, alerts, and portfolio analysis

Goal:
Add manual dashboard trading with paper mode first and optional IBKR execution later.

Strict safety rules:
- Do not implement automatic trading.
- Do not place live orders by default.
- Do not submit any order without explicit user confirmation.
- All trading endpoints require authentication.
- Paper mode is the default.
- Live mode requires trading.mode=LIVE, trading.live-enabled=true, and trading.ibkr.enabled=true.
- No background scanner or alert may create orders.

Phase 1:
- Add trading domain models.
- Add PostgreSQL tables for trade_order, trade_execution, paper_position, and trade_risk_event.
- Add TradingProperties.
- Add BrokerService abstraction.
- Add TradingService facade.
- Add RiskManager.
- Add PaperBrokerService as the default broker.
- Add REST endpoints for status, positions, preview, submit, and close.
- Add dashboard buttons for BUY 100 EUR, BUY 250 EUR, SELL 50%, SELL ALL.
- Add confirmation modal.
- Integrate paper trading data into Analyze with provider filter PAPER_TRADING.
- Add paper buy/sell markers to existing charts.
- Add unit/controller tests.

Phase 2:
- Add IBKR adapter skeleton behind feature flag.
- Use official IBKR TWS API approach through TWS or IB Gateway.
- Implement connection lifecycle and status.
- Do not enable live orders yet unless explicitly requested.

Phase 3:
- Add IBKR paper account order submission.
- Add contract mapping.
- Add order status handling.
- Surface broker errors in UI.
- Never retry real broker orders blindly.

Phase 4:
- Add real live order execution only after separate review.

Do not break existing market scanning, dashboard charts, WebSocket updates, Telegram alerts, quote history, portfolio import, or analysis pages.
```

## Final Assessment

The original prompt is implementable, but not as a single large change.

Recommended first implementation should include only:

- persisted paper trading
- risk manager
- preview/confirmation flow
- dashboard buttons
- tests

IBKR real execution should be introduced only after the paper workflow is stable and audited.
