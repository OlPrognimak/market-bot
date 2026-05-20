# OpenAI News Monitoring Idea

This document captures the analysis for adding an OpenAI-powered news and global event monitoring layer to `market-bot`.

## Goal

Use the shares defined in the watchlist and periodically scan for company-specific, corporate, and global information that may affect share prices.

Examples:

- Company news: earnings, contracts, litigation, product launches, executive changes, financing, bankruptcy risk.
- Corporate news: press releases, partnerships, acquisitions, leadership changes, guidance updates, investor presentations, regulatory filings, product milestones.
- Global news: wars, sanctions, catastrophes, tax changes, tariffs, interest rates, inflation, supply-chain disruption, energy shocks.

OpenAI should be used as the analysis/classification layer, not as the only news source.

## Recommended Architecture

1. Read the watchlist through the existing watchlist loader. Prefer `market-bot.watchlist-file` pointing to an external YAML file, with `market-bot.watchlist` as fallback.
2. Scan company/ticker news from financial news APIs.
3. Scan corporate news sources such as company press releases, SEC/filing feeds, investor relations pages, and official announcements.
4. Scan global macro/event sources for geopolitical, disaster, tax, tariff, and supply-chain events.
5. Deduplicate articles by URL, title, and publication timestamp.
6. Send compact article/event data to OpenAI.
7. Ask OpenAI to classify market relevance and likely impact.
8. Store processed event IDs/URLs to avoid repeated alerts.
9. Send Telegram alerts only for high-confidence, high-impact events.

Corporate news can often be positive, for example new contracts, partnerships, product milestones, or raised guidance. It is still useful because it can explain upward price movement, identify momentum catalysts, and provide context for whether an alert should be treated as meaningful or ignored as normal noise.

OpenAI should classify:

- affected ticker/company
- direction: `UP`, `DOWN`, `NEUTRAL`, or `UNKNOWN`
- confidence
- impact score
- expected time horizon
- reason
- source URLs
- whether an alert should be sent

Important rule: require source URLs and return `UNKNOWN` when evidence is weak.

## First Phase Code Organization

For the first phase, do not create a separate Maven module and do not create a new microservice.

Use the existing Spring Boot application and organize the feature with clear package boundaries. This keeps deployment, configuration, scheduling, database access, Telegram alerts, and secrets management simple while the idea is still being validated.

Recommended package structure:

```text
com.prognimak.marketbot
  service/
    MarketScannerService.java
  news/
    NewsScannerService.java
    NewsProvider.java
    NewsArticle.java
    CorporateNewsScanner.java
    GlobalNewsScanner.java
  openai/
    OpenAiClient.java
    NewsImpactClassifier.java
    NewsImpactResult.java
  event/
    ProcessedNewsEvent.java
    ProcessedNewsEventRepository.java
```

Suggested responsibilities:

- `news.NewsScannerService`: scheduled coordinator for news scanning.
- `news.NewsProvider`: interface for external news APIs.
- `news.CorporateNewsScanner`: scans company announcements, press releases, filings, and investor relations sources.
- `news.GlobalNewsScanner`: scans macro/global events such as war, tariffs, disasters, tax changes, and supply-chain disruption.
- `openai.NewsImpactClassifier`: sends prepared article/event data to OpenAI and receives structured impact analysis.
- `event.ProcessedNewsEvent`: stores processed article/event IDs or URLs to avoid duplicate alerts.

This can be extracted later if needed:

- Extract to Maven modules when package boundaries become large and stable.
- Extract to a separate microservice only if independent deployment, independent scaling, or separate runtime ownership becomes necessary.

## Watchlist Source

The news monitoring feature should reuse the same watchlist source as the market scanner.

Preferred configuration:

```yaml
market-bot:
  watchlist-file: /app/config/watchlist.yaml
```

External YAML format:

```yaml
watchlist:
  AAPL: Apple
  NVDA: NVIDIA
  BMW.DE: BMW
```

The loader should reload the file periodically or on every scan, so changes to the watched companies can be applied without rebuilding the app.

## Candidate News APIs

| Provider | Use | Free Notes |
|---|---|---|
| Alpha Vantage News Sentiment | Company/ticker and topic news | Has `NEWS_SENTIMENT`, supports `tickers`, `topics`, and time filters. |
| Finnhub | Company news and market news | Useful because this project already has Finnhub configuration. |
| Marketaux | Stock and finance news with sentiment | Advertises a free plan with no payment details required. |
| GDELT Cloud | Global wars, protests, disasters, geopolitical events | Structured Events, Stories, Entities, and global event signals. |
| The Guardian Open Platform | High-quality global and political news | Free non-commercial developer key, around 500 calls/day. |
| NewsAPI.org | General news | Free plan is for development/testing only, around 100 requests/day, articles delayed. |
| NewsData.io | General/global news | Public material mentions a free tier around 200 credits/day. |

Recommended first version:

```text
Alpha Vantage + GDELT + Guardian or NewsAPI/NewsData + OpenAI
```

## OpenAI Usage Pattern

Use the OpenAI Responses API.

Two possible approaches:

1. Use OpenAI hosted `web_search` for direct current-web lookup.
2. Preferably, fetch news with your own APIs, then ask OpenAI to classify the fetched data.

The second option is usually better for production because it is more controllable, easier to cache, easier to deduplicate, and easier to audit.

## Example: Direct OpenAI Web Search

```bash
curl https://api.openai.com/v1/responses \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-5.5",
    "tools": [{ "type": "web_search" }],
    "input": "Search current news for AEye / LIDR. Identify only concrete events likely to affect the stock price. Return concise JSON with direction, confidence, impact_reason, and source_urls."
  }'
```

## Example: Better Production Input

Fetch articles yourself, then send compact JSON to OpenAI:

```json
{
  "company": "AEye",
  "ticker": "LIDR",
  "articles": [
    {
      "title": "Example title",
      "summary": "Short article summary or snippet",
      "url": "https://example.com/article",
      "publishedAt": "2026-05-18T12:00:00Z"
    }
  ]
}
```

## Example Prompt

```text
You are a financial news classifier.

Analyze only the supplied articles.
Do not invent facts.
If evidence is weak, use UNKNOWN.

Return JSON with:
- ticker
- direction: UP, DOWN, NEUTRAL, or UNKNOWN
- confidence: 0-100
- impact_score: 0-100
- time_horizon: intraday, days, weeks, or unknown
- reason
- alert: true or false
- source_urls
```

## Example Output Shape

```json
{
  "ticker": "LIDR",
  "company": "AEye",
  "direction": "DOWN",
  "confidence": 72,
  "impact_score": 68,
  "time_horizon": "days",
  "reason": "The supplied articles report a financing event that may dilute existing shareholders.",
  "alert": true,
  "source_urls": [
    "https://example.com/article"
  ]
}
```

## Suggested Classification Schema

```json
{
  "type": "object",
  "properties": {
    "ticker": { "type": "string" },
    "company": { "type": "string" },
    "direction": {
      "type": "string",
      "enum": ["UP", "DOWN", "NEUTRAL", "UNKNOWN"]
    },
    "confidence": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100
    },
    "impact_score": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100
    },
    "time_horizon": {
      "type": "string",
      "enum": ["intraday", "days", "weeks", "unknown"]
    },
    "reason": { "type": "string" },
    "alert": { "type": "boolean" },
    "source_urls": {
      "type": "array",
      "items": { "type": "string" }
    }
  },
  "required": [
    "ticker",
    "company",
    "direction",
    "confidence",
    "impact_score",
    "time_horizon",
    "reason",
    "alert",
    "source_urls"
  ],
  "additionalProperties": false
}
```

## Alert Rules

Initial conservative alert rule:

```text
alert = true when:
- impact_score >= 70
- confidence >= 65
- source_urls is not empty
- direction is UP or DOWN
```

Avoid alerting on:

- opinion-only articles
- repeated syndications of the same article
- old news
- weak rumors without credible sourcing
- generic macro news with no clear connection to a watched company

## Global Event Monitoring

Use GDELT or general news APIs for global events. Search/classify categories such as:

- war and military escalation
- sanctions
- tariffs
- tax changes
- natural disasters
- energy supply disruption
- interest-rate decisions
- inflation reports
- port/shipping disruption
- semiconductor export controls
- banking/credit stress

Then ask OpenAI to map events to watched companies.

Example prompt:

```text
Given this global event and this watchlist, identify which companies may be affected.
Return only companies with a plausible connection.
If no clear connection exists, return an empty list.
```

## Important Risks

This should be an alerting and ranking system first, not an automatic trading system.

Reasons:

- News impact is noisy.
- Important news may already be priced in.
- Free news APIs can be delayed or incomplete.
- Models can misclassify impact direction.
- Small-cap stocks can move on rumors or low-liquidity noise.

Use OpenAI output as a signal requiring validation, not as guaranteed investment advice.

## Useful Sources

- OpenAI Responses API and hosted web search documentation.
- OpenAI Structured Outputs documentation.
- Alpha Vantage `NEWS_SENTIMENT` documentation.
- GDELT Cloud API documentation.
- The Guardian Open Platform documentation.
- NewsAPI pricing and usage rules.
- Marketaux financial news API documentation.
