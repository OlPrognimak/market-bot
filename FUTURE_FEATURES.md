# Future Feature Ideas

This document collects useful next features for Market Bot.

The main goals are:

- better control of scanning
- less alert noise
- richer market context
- better dashboard filtering
- future integration with news and OpenAI analysis

## Implemented Foundation

The first foundation feature is now:

```text
Region-aware watchlist + favorite priority
```

This gives practical control quickly and prepares the application for better dashboard filters, Telegram commands, and news classification.

Example:

```yaml
watchlist:
  AAPL:
    name: Apple
    region: US
    sector: Technology
    exchange: NASDAQ
    currency: USD
    priority: HIGH
    enabled: true

  BMW.DE:
    name: BMW
    region: EU
    sector: Automotive
    exchange: XETRA
    currency: EUR
    priority: NORMAL
    enabled: true
```

The next useful layer on top of this foundation is runtime scan mode.

Runtime configuration idea:

```yaml
market-bot:
  scan-region: ALL
```

Supported values:

```text
US
EU
ALL
```

Later this can be controlled through:

- dashboard segmented control: `All | US | EU`
- Telegram commands: `/scan_us`, `/scan_eu`, `/scan_all`

## Remote Scan Control

Add runtime controls through the UI and/or Telegram commands.

Useful controls:

- scan US shares only
- scan EU shares only
- scan all shares
- pause scanning
- resume scanning
- trigger manual scan
- temporarily change alert thresholds
- enable or disable Telegram alerts

Example configuration model:

```text
scan-market = US | EU | ALL
scanner-enabled = true | false
telegram-alerts-enabled = true | false
dashboard-enabled = true | false
```

This would allow the user to react quickly without editing files and restarting the application.

## Market Region Classification

The watchlist now supports structured metadata, not only symbol-to-name pairs.

Current simple format:

```yaml
watchlist:
  AAPL: Apple
  BMW.DE: BMW
```

Structured format:

```yaml
watchlist:
  AAPL:
    name: Apple
    region: US
    sector: Technology
    exchange: NASDAQ
    currency: USD
    priority: HIGH
    enabled: true

  BMW.DE:
    name: BMW
    region: EU
    sector: Automotive
    exchange: XETRA
    currency: EUR
    priority: NORMAL
    enabled: true
```

Possible metadata:

- `name`
- `region`
- `sector`
- `exchange`
- `currency`
- `priority`
- `enabled`
- `tags`

Benefits:

- scan only US, EU, or all shares
- filter dashboard by region or sector
- create more useful Telegram summaries
- prepare for news classification by sector and region

## Favorite Shares

Favorite shares should use the `priority` field instead of a separate hard-coded list.

Supported values:

```text
HIGH
NORMAL
LOW
```

Initial use:

- show priority on the dashboard
- make `HIGH` priority rows easier to filter
- later scan `HIGH` priority shares more often
- later use stricter or looser alert thresholds per priority
- later make Telegram commands such as `/favorites` and `/scan_favorites`

## Alert Noise Control

Noise reduction is important because price movements can trigger repeated messages.

Useful controls:

- cooldown per symbol
- no repeated Telegram alert for the same symbol within a configured time window
- alert severity: `LOW`, `MEDIUM`, `HIGH`
- only alert if movement is new compared with the previous alert
- suppress alerts for known unavailable symbols
- track repeated 404s and temporarily disable bad symbols

Example:

```yaml
market-bot:
  alerts:
    cooldown-minutes: 30
    min-severity: MEDIUM
    disable-symbol-after-404-count: 3
```

Alert severity idea:

```text
LOW: movement just crossed threshold
MEDIUM: strong rolling movement or unusual volume
HIGH: strong movement plus high volume or news event
```

## Telegram Commands

Telegram can be used not only for alerts, but also for remote control and status checks.

Useful commands:

```text
/status
/scan
/scan_us
/scan_eu
/scan_all
/pause
/resume
/top
/losers
/config
```

Example `/status` response:

```text
Market Bot status

Scanner: running
Mode: ALL
Last scan: 2026-05-20 06:30
Symbols: 42
Telegram alerts: enabled
Dashboard: enabled
```

Example `/top` response:

```text
Top movers

NVDA +2.40% rolling
BMW.DE -1.10% rolling
LIDR +0.90% rolling
```

## Additional Financial Information

Add more context to alerts and dashboard rows.

Useful fields:

- volume
- average volume
- volume ratio
- market cap
- previous close
- day range
- 52-week high/low
- premarket or after-hours price if available
- exchange
- currency

Most useful first addition:

```text
volume vs average volume
```

Reason: price movement with unusually high volume is more meaningful than price movement on normal or low volume.

Possible alert text:

```text
NVDA UP

Rolling move: +1.20%
Delta: +0.45%
Volume: 1.8x average
Price: $...
```

## Dashboard Filters

The dashboard should support quick operational filtering.

Useful filters:

- region: `US`, `EU`, `ALL`
- direction: `UP`, `DOWN`, `ALL`
- alert only
- sector
- minimum delta
- minimum rolling movement
- text search by symbol or company

Recommended first UI filters:

```text
All | US | EU
Up | Down | All
Alert only
```

The frontend can perform local filtering for responsiveness, but the backend should own the canonical scan mode.

## Historical View

For each selected symbol, show a compact history.

Useful data:

- recent scan points
- recent alerts
- delta history
- rolling movement history
- last Telegram message
- last news explanation later

Dashboard concept:

```text
Main table -> click symbol -> side panel

Side panel:
- latest quote
- last 10 scan results
- last alerts
- rolling trend
- related news later
```

This would make the dashboard more useful than a live table only.

## News And OpenAI Context

Later, connect this with the OpenAI/news-monitoring concept.

Useful questions:

- why could this movement be happening?
- is there relevant company news?
- is there relevant macro/global news?
- is this likely noise or a meaningful event?

Possible dashboard additions:

- latest relevant news near the symbol
- AI impact direction: `UP`, `DOWN`, `NEUTRAL`, `UNKNOWN`
- confidence score
- source URLs
- short explanation

Possible Telegram addition:

```text
Possible reason:
New company announcement or sector news detected.

Confidence: 72%
Sources:
- https://...
```

Important rule:

```text
OpenAI should classify supplied evidence, not invent facts.
```

## Suggested Implementation Order

1. Region-aware structured watchlist.
2. Runtime scan mode: `US`, `EU`, `ALL`.
3. Dashboard filters for region and direction.
4. Telegram status and manual scan commands.
5. Alert cooldown per symbol.
6. Volume and average-volume context.
7. Symbol detail/history panel.
8. News/OpenAI explanation layer.
