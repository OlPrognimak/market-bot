# Fast Visualization Concept

This document describes a fast first version for visualizing generated market scan data.

## Goal

Provide a lightweight frontend view that shows the latest generated scan results from the backend in near real time.

The first version should be simple:

- Use the existing backend scan result data.
- Keep generated scan messages/results in memory or persist them later if needed.
- Push updates to the frontend after each scan.
- Show sorted market movement data in a clear dashboard.
- Highlight the strongest positive and strongest negative movements.

## Technology Assumption

- Frontend: React with Next.js.
- Backend communication: WebSocket.
- Backend: existing Spring Boot application.

The frontend should not calculate financial meaning. It should mainly display and optionally sort already-prepared scan results.

## Backend Data Source

The list of symbols should come from the same dynamic watchlist loader used by the backend scanner.

Preferred watchlist configuration:

```yaml
market-bot:
  watchlist-file: /app/config/watchlist.yaml
```

Example external YAML:

```yaml
watchlist:
  AAPL: Apple
  NVDA: NVIDIA
  BMW.DE: BMW
```

The backend already generates a Telegram-style message in:

```java
private String buildMessage(...)
```

For visualization, do not rely only on parsing this formatted text. The better concept is:

1. Create a structured scan result object before building the message.
2. Use that object for:
   - Telegram message generation.
   - WebSocket dashboard updates.
   - optional persistence.

Example conceptual object:

```text
MarketScanResult
- symbol
- companyName
- currentPercent
- previousPercent
- delta
- rollingDelta
- rollingWindowSize
- currentPrice
- low
- high
- open
- previousClose
- direction
- timestamp
- messageText
```

The existing `buildMessage()` output can still be included as `messageText`, but the dashboard should use structured fields for sorting and rendering.

## Backend Result Storage

For a fast first version, keep the latest scan results in memory:

```text
Map<String, MarketScanResult> latestResultsBySymbol
```

Each symbol gets overwritten after every scan. This keeps the dashboard focused on the latest state.

Later options:

- Store every scan result in the database.
- Store only alert-worthy results.
- Keep an in-memory rolling history per symbol.
- Add Redis if the app becomes multi-instance.

## Sorting Logic

The primary sorting should happen on the backend.

Reason:

- The backend owns the calculation.
- WebSocket clients receive data already ordered.
- All clients see the same ranking.
- Frontend implementation stays simple.

The frontend may still support local re-sorting for UI interactions, but the default order should come from the backend.

Recommended ranking value:

```text
movementScore = max(abs(rollingDelta), abs(delta))
```

Alternative rule based on the current idea:

```text
if abs(delta) > abs(rollingDelta) and delta is positive:
    rank by delta
else:
    rank by rollingDelta
```

More consistent first version:

```text
primary sort: abs(max(delta, rollingDelta by absolute movement)) descending
secondary sort: timestamp descending
```

For display, preserve the signed values:

- positive movement: blue or green
- negative movement: red
- neutral: gray

## Dashboard Layout

The frontend should show one main frame with the sorted result list and a compact summary area above it.

Recommended layout:

```text
------------------------------------------------------
| Biggest Positive | Biggest Negative | Last Scan     |
------------------------------------------------------
| Sorted Results Table/List                          |
|                                                    |
| AEye (LIDR)    rolling: +4.2%   delta: +1.1%       |
| Apple (AAPL)   rolling: -2.1%   delta: -0.4%       |
| ...                                                |
------------------------------------------------------
```

## Top Summary Panels

Show small panels at the top of the page:

- Biggest positive rolling movement.
- Biggest negative rolling movement.
- Biggest positive delta.
- Biggest negative delta.
- Last scan time.
- Number of scanned symbols.

Minimal first version:

```text
Top Gainer: company, symbol, rollingDelta, delta
Top Loser: company, symbol, rollingDelta, delta
Last Scan: timestamp
```

These panels allow fast visual detection of the strongest positive and negative values.

## Main Result List

The main frame should show sorted scan results.

Suggested columns:

```text
Symbol
Company
Current %
Previous %
Delta %
Rolling %
Price
Range
Direction
Updated At
```

Rows should be visually scannable:

- Strong positive movement: highlight with positive color.
- Strong negative movement: highlight with negative color.
- Alert-worthy rows: stronger border or marker.
- Recently updated rows: subtle short highlight.

## WebSocket Flow

Backend publishes an update after each scan.

Conceptual flow:

```text
MarketScannerService.scanMarket()
  -> create/update MarketScanResult per symbol
  -> sort latest results
  -> publish MarketDashboardSnapshot through WebSocket
```

Dashboard snapshot:

```text
MarketDashboardSnapshot
- lastScanAt
- results
- topPositiveRolling
- topNegativeRolling
- topPositiveDelta
- topNegativeDelta
```

The frontend subscribes once and replaces its current dashboard state whenever a new snapshot arrives.

## Scan Trigger Options

The scan trigger should be configurable.

Possible modes:

```text
BACKEND_SCHEDULED
FRONTEND_TRIGGERED
BOTH
```

### BACKEND_SCHEDULED

The backend scans periodically using `@Scheduled`.

Best for:

- production
- unattended monitoring
- Telegram alerting
- consistent scan intervals

### FRONTEND_TRIGGERED

The frontend sends a WebSocket or REST command to trigger scanning.

Best for:

- local testing
- manual refresh
- demos
- development

### BOTH

Backend scans on schedule, and frontend can request an extra manual scan.

Best first practical option:

```text
BACKEND_SCHEDULED with optional manual frontend refresh
```

## Configuration Idea

Conceptual properties:

```yaml
market-bot:
  dashboard:
    enabled: true
    websocket-enabled: true
    scan-trigger-mode: BACKEND_SCHEDULED
    keep-latest-results: true
    max-results: 100
```

## Frontend Responsibilities

The frontend should:

- connect to backend WebSocket
- receive dashboard snapshots
- render summary panels
- render sorted result list
- optionally allow local sort by delta, rolling movement, symbol, or timestamp
- show connection status
- show last update time

The frontend should not:

- recalculate business logic differently from backend
- parse Telegram message text as the primary data source
- decide whether a movement is alert-worthy without backend support

## First Version Recommendation

Build the first version in this order:

1. Backend creates structured `MarketScanResult` in parallel with the existing Telegram message.
2. Backend keeps latest results in an in-memory `Map`.
3. Backend sorts results and creates a dashboard snapshot after each scan.
4. Backend publishes the snapshot through WebSocket.
5. Next.js frontend displays top panels and sorted list.
6. Add optional manual scan trigger from frontend.
7. Persist dashboard history only after the live view is useful.

## Important Design Decision

Do not build the dashboard around parsing the text from `buildMessage()`.

Use the same values that are passed into `buildMessage()` to create a structured result object. This avoids fragile parsing and makes sorting, filtering, and visualization much easier.

`buildMessage()` should become one output format, not the data model.
