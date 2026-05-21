# Market Bot Dashboard Frontend

This is the Next.js frontend for the Market Bot live dashboard.

The UI connects to the Spring Boot backend, receives market scan snapshots through WebSocket, and renders the latest watchlist movement in an operational dashboard.

## UI Overview

The dashboard is designed as a compact monitoring screen, not a landing page.

Main areas:

- Header with connection status and last scan time.
- Summary strip with strongest positive and negative movement.
- Toolbar with search, sorting, reload, and optional manual scan.
- Exact filters for country, priority, sector, and exchange.
- Main results table with the latest scan results.

The summary strip shows:

- Top positive rolling movement.
- Top negative rolling movement.
- Top positive delta.
- Top negative delta.
- Last scan time and number of symbols.

The table shows:

- symbol
- company
- region
- priority
- current percent change
- delta since previous persisted value
- rolling movement
- current price
- daily range
- update time

Rows are visually colored by movement direction:

- positive values use green
- negative values use red
- neutral values use gray
- alert-worthy rows get a left-side marker

## Data Flow

The frontend receives full dashboard snapshots from:

```text
ws://localhost:8080/ws/market-dashboard
```

It can also load the latest snapshot over HTTP:

```text
GET http://localhost:8080/api/dashboard/snapshot
```

If backend configuration allows manual scanning, the `Scan Now` button calls:

```text
POST http://localhost:8080/api/dashboard/scan
```

The frontend expects the backend to own the main ranking logic. Local sorting is only a UI convenience. Exact filters can be combined, for example country `US` plus priority `HIGH`.

Watchlist metadata such as `region`, `sector`, `exchange`, `currency`, and `priority` comes from the backend watchlist file. The current table displays `region` and `priority`; the other fields are part of the API model so later filters and detail panels can use them without changing the backend contract again.

## Configuration

By default, the frontend expects the backend at:

```text
http://localhost:8080
```

Override it with:

```bash
NEXT_PUBLIC_MARKET_BOT_API_URL=http://localhost:8080
```

Example `.env.local`:

```bash
NEXT_PUBLIC_MARKET_BOT_API_URL=http://localhost:8080
```

The WebSocket URL is derived from this value:

- `http://...` becomes `ws://...`
- `https://...` becomes `wss://...`

## Development

Install dependencies:

```bash
npm install
```

Start the dev server:

```bash
npm run dev -- --port 3000
```

Open:

```text
http://localhost:3000
```

The backend must be running separately for live data.

## Build

Create a production build:

```bash
npm run build
```

Start the production server:

```bash
npm run start
```

## Source Structure

```text
frontend/
  src/
    app/
      page.tsx
      layout.tsx
      globals.css
    features/
      dashboard/
        components/
          DashboardPage.tsx
          SummaryStrip.tsx
          MarketResultsTable.tsx
          ConnectionStatus.tsx
        hooks/
          useMarketDashboardSocket.ts
        types/
          market-dashboard.ts
        utils/
          sortMarketResults.ts
    lib/
      websocket.ts
      format.ts
```

## Notes

The dashboard does not parse Telegram message text. It displays structured `MarketScanResult` data produced by the backend from the same values used to generate Telegram messages.

This keeps sorting and visualization stable even if the Telegram message text changes later.
