# Market Bot

Market Bot is a Spring Boot service with a Next.js frontend for watching shares, crypto, futures, portfolio imports, and manual paper trading. The backend stores data in PostgreSQL, scans market providers on a schedule, exposes REST/WebSocket APIs, and manages user-specific watchlists and notification settings.

The current IBKR implementation supports explicit connection checks to TWS / IB Gateway and internal paper order execution. It does not yet send live orders to IBKR; keep `MARKET_BOT_TRADING_LIVE_ENABLED=false` unless live routing has been implemented and reviewed.

## Main Features

- Authenticated web UI with admin and regular user roles.
- Share dashboard with live scan snapshots, WebSocket updates, sorting, filtering, charts, manual scan, and pre-market/post-market views.
- Crypto dashboard and futures dashboard with independent scan configuration.
- Stock and crypto catalog administration for symbols, names, region, sector, exchange, currency, priority, and enabled state.
- User management with per-user share/crypto watchlists, thresholds, and notification preferences.
- Provider symbol mappings for imported portfolio symbols such as ISINs.
- API credential storage in the database for provider credentials that should be selectable from the UI.
- Portfolio CSV import for Revolut and Trade Republic exports.
- Portfolio analysis with realized P/L, income, current positions, and reconciliation status.
- News monitoring and AI-assisted news insight scoring.
- Paper trading workspace with order previews, manual confirmation, risk limits, paper positions, and IBKR socket status.

## Menu Items

- `Shares`: main market dashboard for the user's stock watchlist. Includes regular-session scanner results, filters, charts, manual reload/scan, and extended-hours tabs.
- `Crypto`: crypto dashboard for configured crypto symbols and scan thresholds.
- `Futures`: futures dashboard for futures/market session data.
- `IBKR Trade`: manual trading workspace. Shows IBKR connection state, loads tradable shares from the dashboard snapshot, previews paper orders, confirms orders, and shows paper positions.
- `Import data`: upload Revolut or Trade Republic CSV exports. Duplicate rows/files are skipped.
- `Analyze`: analyze imported portfolio activity by date range, ticker, and provider. Shows realized lots, income, open positions, market value, and P/L.
- `Settings`: current user's profile, watchlists, thresholds, and notification preferences.
- `Users`: admin-only user list, create/edit/delete users, roles, enabled state, and per-user settings.
- `Account`: non-admin version of user settings for the current user.
- `Catalog`: admin-only stock and crypto catalog editor.
- `System settings`: admin-only provider symbol mappings and API credential editor.
- `Logout`: clears the browser session.

## Runtime Layout

- Backend API: `http://localhost:8080`
- Frontend dev server: `http://localhost:3000`
- PostgreSQL default used by local config: `localhost:5455/test_db`
- Docker image exposes the backend on `${MARKET_BOT_PORT:-8080}`.

The frontend talks to the backend through `NEXT_PUBLIC_MARKET_BOT_API_URL`. In local frontend development use:

```bash
NEXT_PUBLIC_MARKET_BOT_API_URL=http://localhost:8080 npm run dev
```

## Local Development

1. Start PostgreSQL and create the configured database.
2. Copy `.env.example` to `.env` and adjust database credentials, JWT secret, provider keys, and trading settings.
3. Start the backend:

```bash
./mvnw spring-boot:run
```

4. Start the frontend:

```bash
cd frontend
npm install
NEXT_PUBLIC_MARKET_BOT_API_URL=http://localhost:8080 npm run dev
```

Default admin credentials come from `APP_SECURITY_ADMIN_USERNAME` and `APP_SECURITY_ADMIN_PASSWORD`.

## Docker

Build the jar first, then start Compose:

```bash
./mvnw clean package
docker compose --env-file .env up --build -d
```

Docker uses `host.docker.internal` for services running on the Mac host, including PostgreSQL and IB Gateway/TWS. The default Compose file mounts:

- `${MARKET_BOT_WATCHLIST_HOST_FILE:-./config/watchlist.yaml}` to `/app/config/watchlist.yaml`
- `${MARKET_BOT_LOG_DIR:-./logs}` to `/app/logs`

Telegram values are optional in Compose. If you store Telegram credentials in the database, keep `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` empty in `.env`.

## IBKR Setup

Use either TWS or IB Gateway. On macOS, ActiveX is not used; the Java API socket is enough.

Paper trading defaults:

- TWS paper port: `7497`
- IB Gateway paper port: `4002`
- Local IntelliJ/backend host: `127.0.0.1`
- Docker backend host: `host.docker.internal`
- `client-id`: any unique integer per application connection, commonly `1`
- `account-id`: your IBKR paper account id, visible in TWS / IB Gateway account windows. Leave empty until you know it.

Recommended local IntelliJ settings for IB Gateway paper:

```env
MARKET_BOT_TRADING_ENABLED=true
MARKET_BOT_TRADING_IBKR_ENABLED=true
MARKET_BOT_TRADING_IBKR_HOST=127.0.0.1
MARKET_BOT_TRADING_IBKR_PORT=4002
MARKET_BOT_TRADING_IBKR_CLIENT_ID=1
MARKET_BOT_TRADING_IBKR_PAPER_ACCOUNT_ONLY=true
MARKET_BOT_TRADING_IBKR_CONNECT_ON_STARTUP=false
```

Recommended Docker settings for IB Gateway paper:

```env
MARKET_BOT_TRADING_IBKR_HOST=host.docker.internal
MARKET_BOT_TRADING_IBKR_PORT=4002
```

Before using the UI, verify the port from your Mac:

```bash
nc -vz 127.0.0.1 4002
```

In TWS / IB Gateway, enable socket clients/API access and keep read-only API disabled only if you explicitly intend to allow order placement. The current app should stay in paper mode unless live trading has been deliberately enabled and implemented.

## Configuration

All important environment variables are listed in `.env.example`. Spring Boot also supports relaxed environment binding, so variables such as `MARKET_BOT_DASHBOARD_MAX_RESULTS` override `market-bot.dashboard.max-results` even when `application.yaml` contains a literal default.

Important groups:

- `APP_DATASOURCE_*`: PostgreSQL connection.
- `SPRING_*`: Spring runtime, Docker Compose integration, and JPA/Hibernate schema settings.
- `APP_SECURITY_*`: JWT lifetime and bootstrapped admin user.
- `OPENAI_*`: AI model/key settings for news insights.
- `FINNHUB_API_KEY`, `TWELVE_API_KEY`: market data provider credentials.
- `TELEGRAM_*`: optional fallback notification credentials.
- `MARKET_BOT_SCANNER_*`: share scan frequency, quote change thresholds, and provider retry behavior.
- `MARKET_BOT_ALERT_*`: alert thresholds for share movement.
- `MARKET_BOT_TREND_*`: adaptive trend profile calibration.
- `MARKET_BOT_CRYPTO_*`, `MARKET_BOT_EXTENDED_HOURS_*`, `MARKET_BOT_FUTURES_*`: scanner settings for non-regular share dashboards.
- `MARKET_BOT_NEWS_*`: news monitoring, provider use, scoring thresholds, and background monitoring.
- `MARKET_BOT_DASHBOARD_*`: dashboard enablement, WebSocket support, manual scan mode, and result limits.
- `MARKET_BOT_HISTORY_*`: optional history backfill profile inputs.
- `MARKET_BOT_TRADING_*` and `TRADING_ALLOWED_ORDER_TYPES`: paper/live trading guardrails, supported order types, and IBKR connectivity.

## Tests

Run backend tests:

```bash
./mvnw test
```

Run frontend checks:

```bash
cd frontend
npm run lint
npm run build
```
