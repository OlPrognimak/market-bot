# System Settings and External Symbol Mapping Concept

## Goal

Move provider-specific ticker mappings and selected runtime settings out of Java code into database-managed configuration. Add a new **System settings** UI area where an admin can maintain:

- provider ticker aliases, for example Trade Republic ISIN -> Yahoo ticker
- global scanner settings, for example delta threshold, rolling window size, retry limits
- selected provider keys/tokens, with security restrictions
- future cache-related settings

This avoids repeated code changes for every new unmapped Revolut or Trade Republic symbol.

## Current State

### Symbol Mapping

Mappings are currently hard-coded in `PortfolioTickerAliases`:

- Revolut provider symbols are mapped to market/Yahoo symbols.
- Trade Republic ISINs are mapped to market/Yahoo symbols.
- Reverse lookup is used for chart markers, so chart symbol `SAF.PA` can find Revolut transactions with `SEJ1`.

Current consumers:

- `PortfolioImportService`
  - normalizes imported portfolio transactions and realized lots
  - maps Trade Republic symbols during import
  - now also maps Revolut provider symbols during import
- `PortfolioAnalysisService`
  - uses alias candidates for chart markers
- `PortfolioMarketPriceService`
  - uses alias candidates to resolve current market price
- `PortfolioSchemaRepair`
  - repairs already imported rows that still contain raw provider symbols

Problem: every unknown provider symbol requires Java changes, rebuild, and redeploy.

### Settings

Configuration currently exists in several places:

- `application.yaml` / environment variables:
  - provider API keys
  - scanner intervals
  - quote retry parameters
  - trend calibration defaults
  - extended-hours and futures settings
- `AppProperties`, `TrendProperties`, `MarketDashboardProperties`
  - strongly typed application configuration
- `app_user_property`
  - user-specific alert thresholds
  - user watchlists
  - user messenger settings
- `stock_trend_profile`
  - calculated per-stock adaptive trend profile

This split is reasonable, but it lacks a global editable configuration store.

## Proposed Data Model

### `provider_symbol_mapping`

Stores mappings from provider-specific symbols to normalized market-data symbols.

Suggested columns:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | bigint | primary key |
| `provider_type` | varchar | `REVOLUT`, `TRADE_REPUBLIC`, later more |
| `source_symbol` | varchar | provider symbol or ISIN, normalized uppercase |
| `source_symbol_type` | varchar | `TICKER`, `ISIN`, `CUSIP`, `WKN`, `UNKNOWN` |
| `market_provider` | varchar | initially `YAHOO` |
| `market_symbol` | varchar | Yahoo symbol, for example `AXP`, `BSP.DE` |
| `instrument_name` | varchar | optional readable name |
| `currency` | varchar | optional import/trading currency |
| `enabled` | boolean | disabled mappings are ignored |
| `verified` | boolean | manual verification flag |
| `priority` | int | tie-breaker if multiple mappings exist |
| `created` / `modified` | timestamp | inherited style should match existing entities |

Recommended constraints:

- unique active mapping on `(provider_type, source_symbol, market_provider)`
- index on `(provider_type, source_symbol, enabled)`
- index on `(market_provider, market_symbol, enabled)`

Example rows:

| Provider | Source | Type | Market provider | Market symbol |
| --- | --- | --- | --- | --- |
| `TRADE_REPUBLIC` | `US0258161092` | `ISIN` | `YAHOO` | `AXP` |
| `TRADE_REPUBLIC` | `GB0002634946` | `ISIN` | `YAHOO` | `BSP.DE` |
| `REVOLUT` | `SEJ1` | `TICKER` | `YAHOO` | `SAF.PA` |

### `system_setting`

Stores global editable application settings.

Suggested columns:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | bigint | primary key |
| `setting_group` | varchar | `SCANNER`, `TREND`, `NEWS`, `PROVIDER`, `SECURITY`, `CACHE` |
| `setting_key` | varchar | stable machine key |
| `setting_value` | text | stored value, optionally encrypted |
| `value_type` | varchar | `TEXT`, `NUMBER`, `BOOLEAN`, `SECRET`, `JSON` |
| `description` | varchar | UI help text |
| `enabled` | boolean | allows soft disable |
| `requires_restart` | boolean | useful for settings that are not dynamically reloadable |
| `created` / `modified` | timestamp | inherited style should match existing entities |

Recommended constraints:

- unique key on `(setting_group, setting_key)`
- value validation in service layer, not only UI

Important security split:

- API keys and tokens can be stored here only if encrypted at rest.
- Database password, JWT signing secret, encryption master key, and admin bootstrap password should remain environment variables.

## Backend Concept

### Symbol Mapping Service

Create a service such as `ProviderSymbolMappingService`.

Responsibilities:

- resolve provider source symbol to market symbol
- return reverse candidates for chart markers
- validate new mappings
- expose CRUD operations for admin UI
- cache mappings for fast import/chart use

Suggested methods:

```java
String resolveMarketSymbol(PortfolioProviderType providerType, String sourceSymbol, String fallback);
List<String> reverseProviderSymbols(String marketProvider, String marketSymbol);
List<MappingResponse> listMappings(...filters...);
MappingResponse saveMapping(MappingRequest request);
```

Migration path:

1. Keep `PortfolioTickerAliases` as fallback for one release.
2. Seed DB table from current hard-coded aliases.
3. Change import, analysis, market-price, and chart-marker code to call `ProviderSymbolMappingService`.
4. Remove hard-coded alias maps after DB mapping is stable.

### Settings Service

Create a service such as `SystemSettingService`.

Responsibilities:

- read typed global settings
- merge fallback values from `application.yaml`
- expose admin CRUD/update operations
- validate setting keys, types, ranges, and secrets
- publish cache invalidation or application events after updates

Recommended rule:

- code reads settings through typed services, not directly from generic key-value rows everywhere

Example:

```java
ScannerRuntimeSettings scannerSettings = systemSettingService.scannerSettings();
TrendRuntimeSettings trendSettings = systemSettingService.trendSettings();
```

### APIs

Add admin-only endpoints:

- `GET /api/system/symbol-mappings`
- `POST /api/system/symbol-mappings`
- `PUT /api/system/symbol-mappings/{id}`
- `DELETE /api/system/symbol-mappings/{id}` or soft disable
- `POST /api/system/symbol-mappings/validate`
- `GET /api/system/settings`
- `PUT /api/system/settings/{group}/{key}`

Validation endpoint should check:

- source symbol is not blank
- target Yahoo symbol is syntactically valid
- optional live provider lookup can resolve target symbol
- duplicate active mapping does not already exist

## Frontend Concept

Add main menu item:

- **System settings**

Suggested sections:

### Symbol mappings

Features:

- table with provider, source symbol, source type, market provider, market symbol, name, enabled, verified
- filter by provider and market provider
- search by source symbol or market symbol
- add/edit dialog
- validate button for Yahoo symbol
- disable/enable mapping
- import/export mappings as CSV later

### Global scanner settings

Fields:

- share delta threshold defaults
- share rolling threshold defaults
- rolling window size
- scanner interval
- quote retry count/delay
- extended-hours enabled/rolling size
- futures enabled/rolling size

The UI should show whether a setting applies immediately or requires restart.

### Provider keys and tokens

Fields:

- Finnhub API key
- Twelve Data API key
- OpenAI API key
- Telegram bot token
- Telegram chat id

Recommended UI behavior:

- never display existing secret values in full
- show masked values only
- save replacement values only
- allow clearing a secret if configured
- warn if a setting is currently supplied by environment variable and DB override is disabled

## PostgreSQL vs Redis

### Recommendation for First Version

Use PostgreSQL only.

Reasons:

- mappings and settings are configuration data, not high-volume transient data
- current project already uses PostgreSQL and JPA
- easier backup, migration, audit, and admin UI integration
- no extra infrastructure required
- values must survive application restarts

Use in-memory Spring cache for hot reads:

- cache symbol mappings by `(providerType, sourceSymbol)`
- cache reverse mappings by `(marketProvider, marketSymbol)`
- cache global settings by key
- invalidate cache after admin changes

### When Redis Makes Sense

Redis becomes useful later if the application needs:

- shared cache across multiple backend instances
- high-frequency quote/session cache
- distributed locks for scanners
- WebSocket/session fanout
- short-lived provider response caching
- rate-limit counters

Redis should not be the source of truth for mappings or system settings. If introduced, PostgreSQL remains canonical and Redis is a derived cache.

Suggested future Redis keys:

- `symbol-map:{provider}:{source}`
- `symbol-map-reverse:{marketProvider}:{marketSymbol}`
- `system-setting:{group}:{key}`
- `quote-cache:{provider}:{symbol}:{interval}`

## Migration Strategy

1. Create DB tables:
   - `provider_symbol_mapping`
   - `system_setting`
2. Seed `provider_symbol_mapping` from current `PortfolioTickerAliases`.
3. Add `ProviderSymbolMappingService`.
4. Change portfolio import and analysis code to use the service.
5. Add admin APIs.
6. Add frontend **System settings** page.
7. Add startup validation:
   - log mappings that point to invalid market symbols
   - log duplicate or disabled mappings affecting imported positions
8. Keep old hard-coded fallback for one release.
9. Remove hard-coded mappings after confidence is high.

## Risk and Guardrails

### Wrong Symbol Mapping

Wrong aliases can corrupt analysis and chart markers.

Guardrails:

- validation before save
- verified flag
- audit columns
- visible source provider and original symbol in analysis details
- soft disable instead of hard delete

### Secret Handling

Global keys and tokens are sensitive.

Guardrails:

- encrypt secret values at rest
- keep encryption key outside DB
- never return raw secret values to UI
- restrict APIs to admin users
- log setting names only, never secret values

### Runtime Settings

Changing scanner intervals or thresholds while jobs are running can cause confusing behavior.

Guardrails:

- mark settings as dynamic or restart-required
- publish application event after updates
- scanner services should reload dynamic settings at scan start, not mid-scan

## Suggested First Implementation Scope

First version should include:

- DB-backed provider symbol mappings
- seed current aliases into DB
- service-level cache using Spring/in-memory cache
- admin CRUD API for symbol mappings
- **System settings** page with only symbol mappings first
- startup repair using DB mappings instead of hard-coded mappings

Second version:

- editable global settings
- validation UI for ranges and secrets
- dynamic scanner settings reload

Third version:

- optional Redis cache if there is a real scaling or performance reason
- audit history for settings and mappings
- CSV import/export for mappings

## Open Decisions

- Should mapping target always be Yahoo, or should the table allow `YAHOO`, `FINNHUB`, `TWELVE_DATA` targets from day one?
- Should Trade Republic mappings prefer primary exchange symbols, German/EUR symbols, or user-selected symbols?
- Should imported historical rows keep original source symbol in an additional column for traceability?
- Should system settings be global only, or should some settings support user overrides?
- Should secrets in DB be enabled at all, or should UI manage only non-secret settings and show environment-key status?
