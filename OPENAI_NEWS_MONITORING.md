# OpenAI News Monitoring Implementation Specification

## Scope

Add news monitoring and AI-assisted market-impact analysis to the existing Spring Boot and Next.js `market-bot` application.

This document defines the planned change only. It does not require a separate microservice or Maven module for the first implementation.

The feature must:

- request current news for a selected symbol/company when the user presses the chart-dialog `News` button;
- collect current news from configured news providers;
- collect official announcements from company websites, investor-relations pages, and RSS/Atom feeds configured in the stock catalog;
- associate news with shares and crypto coins from the database catalogs;
- use Spring AI with the OpenAI model integration to classify market impact;
- persist source articles and AI analysis results;
- avoid processing or notifying users about duplicate articles;
- provide REST endpoints for the frontend;
- add a `News` button to share and crypto chart dialogs;
- show received information in a separate lightweight news-browser dialog;
- optionally send high-confidence news alerts through the existing notification system.

OpenAI is the classification and summarization layer. It must not be treated as the primary news source.

News collection remains strictly on demand for the first implementation. Company websites and external providers are requested only after the logged-in user presses the chart-dialog `News` button. Application startup and normal price-scanner cycles must not trigger news collection.

## Dependency Changes

The project already imports the Spring AI BOM and currently has the Anthropic model starter.

For OpenAI news analysis, add the OpenAI Spring AI starter:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

Keep version management through the existing Spring AI BOM:

```xml
<properties>
    <spring-ai.version>2.0.0-M6</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

The Anthropic starter can remain when other application features use it. Otherwise, remove it to avoid maintaining unused model configuration.

Do not implement a custom low-level `OpenAiClient`. Use Spring AI `ChatClient` and structured response mapping.

## Configuration

### Spring AI OpenAI Configuration

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
          model: ${OPENAI_NEWS_MODEL:gpt-5-mini}
          temperature: 0
```

The API key must come from an environment variable or secret manager. It must never be returned through REST APIs or exposed to Next.js/browser code.

### Market Bot News Configuration

Add a `NewsMonitoringConfig` subset to `AppProperties`:

```yaml
market-bot:
  news-monitoring:
    enabled: ${MARKET_BOT_NEWS_MONITORING_ENABLED:false}
    lookback-hours: ${MARKET_BOT_NEWS_LOOKBACK_HOURS:24}
    max-articles-per-instrument: ${MARKET_BOT_NEWS_MAX_ARTICLES_PER_INSTRUMENT:10}
    minimum-confidence: ${MARKET_BOT_NEWS_MINIMUM_CONFIDENCE:65}
    minimum-impact-score: ${MARKET_BOT_NEWS_MINIMUM_IMPACT_SCORE:70}
    maximum-analysis-age-hours: ${MARKET_BOT_NEWS_MAXIMUM_ANALYSIS_AGE_HOURS:72}
    send-alerts: ${MARKET_BOT_NEWS_SEND_ALERTS:false}
    background-monitoring-enabled: ${MARKET_BOT_NEWS_BACKGROUND_MONITORING_ENABLED:false}
    background-poll-interval-ms: ${MARKET_BOT_NEWS_BACKGROUND_POLL_INTERVAL_MS:900000}
    providers:
      company-sources:
        enabled: ${MARKET_BOT_NEWS_COMPANY_SOURCES_ENABLED:true}
        request-timeout-ms: ${MARKET_BOT_NEWS_COMPANY_SOURCE_TIMEOUT_MS:10000}
        max-pages-per-request: ${MARKET_BOT_NEWS_COMPANY_SOURCE_MAX_PAGES:3}
      finnhub:
        enabled: ${MARKET_BOT_NEWS_FINNHUB_ENABLED:true}
      gdelt:
        enabled: ${MARKET_BOT_NEWS_GDELT_ENABLED:false}
      guardian:
        enabled: ${MARKET_BOT_NEWS_GUARDIAN_ENABLED:false}
        api-key: ${GUARDIAN_API_KEY:}
```

Recommended `AppProperties` record:

```java
public record NewsMonitoringConfig(
        boolean enabled,
        int lookbackHours,
        int maxArticlesPerInstrument,
        int minimumConfidence,
        int minimumImpactScore,
        int maximumAnalysisAgeHours,
        boolean sendAlerts,
        boolean backgroundMonitoringEnabled,
        long backgroundPollIntervalMs,
        NewsProviderConfig providers
) {
}
```

`enabled` controls whether users can request news analysis.

`background-monitoring-enabled` and `background-poll-interval-ms` are optional future settings. They are not required for the first implementation and background monitoring must remain disabled by default.

`company-sources.enabled` controls collection from URLs stored in `stock_catalog`. It does not discover or crawl arbitrary websites. Only explicitly configured and validated catalog URLs may be requested.

## Stock Catalog Company Sources

Extend `StockCatalogEntity` and the corresponding catalog DTOs, CRUD API, and admin catalog UI with these nullable fields:

```text
company_website          varchar(1000)
investor_relations_url   varchar(1000)
news_feed_url            varchar(1000)
```

Field meanings:

- `companyWebsite`: official company homepage. It is primarily identity metadata and should not normally be crawled broadly.
- `investorRelationsUrl`: official investor-relations, press-release, or company-news page containing dated announcements.
- `newsFeedUrl`: official RSS or Atom feed. This is the preferred company-owned source because it provides structured titles, timestamps, summaries, and canonical URLs.

Requirements:

- URLs are optional because not every company publishes a stable investor-relations page or feed.
- Admin users manage these fields through the existing stock catalog CRUD page.
- Accept only absolute `http` or `https` URLs and normalize them before persistence.
- Reject unsupported schemes such as `file`, `javascript`, and `data`.
- Do not automatically trust a manually entered URL as official. Record how it was entered and when it was last validated.
- A future metadata-discovery service may suggest URLs, but an administrator should confirm them before they become enabled collection sources.
- Crypto catalog source URLs should be designed separately because project websites, blogs, GitHub releases, and protocol governance sources differ from company investor-relations sources.

## Package Structure

```text
com.prognimak.marketbot.news
  api/
    NewsInsightController.java
  config/
    NewsAiConfig.java
  model/
    NewsArticle.java
    NewsImpactAnalysis.java
    NewsDirection.java
    NewsTimeHorizon.java
  provider/
    NewsProvider.java
    CompanySourceNewsProvider.java
    RssAtomNewsProvider.java
    FinnhubNewsProvider.java
    GdeltNewsProvider.java
    GuardianNewsProvider.java
  repository/
    NewsArticleRepository.java
    NewsInsightRepository.java
    UserNewsAlertStateRepository.java
  service/
    NewsMonitoringService.java
    NewsCollectionService.java
    NewsDeduplicationService.java
    NewsImpactAnalysisService.java
    NewsInsightQueryService.java
    NewsNotificationService.java
  entity/
    NewsArticleEntity.java
    NewsInsightEntity.java
    UserNewsAlertStateEntity.java
```

## Service Definitions

### `NewsProvider`

Provider abstraction for external news sources:

```java
public interface NewsProvider {
    List<NewsArticle> findNews(
            String symbol,
            String instrumentName,
            Instant from,
            Instant to
    );
}
```

Each provider implementation must:

- return normalized article data;
- include a stable provider article ID when available;
- include the original source URL;
- include publication time;
- never call OpenAI;
- handle provider failures without stopping the complete monitoring cycle.

### `CompanySourceNewsProvider`

Collect official company announcements using URLs stored in `StockCatalogEntity`.

Source priority:

1. `newsFeedUrl`, parsed as RSS or Atom;
2. `investorRelationsUrl`, parsed for dated announcement links;
3. `companyWebsite`, used only as a constrained fallback when it directly exposes a news or press-release section.

The provider must:

- run only for `SHARE` instruments and only during an explicit on-demand refresh;
- request only configured catalog URLs and directly linked announcement pages within the configured page limit;
- prefer structured RSS/Atom feeds over HTML parsing;
- respect robots directives, website terms, request timeouts, response-size limits, and content-type checks;
- never execute website JavaScript or submit forms;
- reject redirects to unexpected schemes or unrelated domains;
- extract only title, summary/snippet, publication timestamp, canonical URL, and source name;
- identify the source as an official company source;
- return no result when a page cannot be parsed reliably instead of inventing article metadata.

Official company sources are authoritative for announcements, but they represent the company's own perspective. AI analysis must distinguish official announcements from independent reporting and must not treat a positive press release as independently verified evidence.

### `NewsCollectionService`

Responsibilities:

- load enabled instruments from `StockCatalogService` and `CryptoCoinCatalogService`;
- load `companyWebsite`, `investorRelationsUrl`, and `newsFeedUrl` for requested shares;
- call all enabled `NewsProvider` implementations;
- normalize article titles, URLs, timestamps, and source names;
- pass collected articles to deduplication;
- persist new source articles before AI analysis.

The first phase should prioritize shares. Crypto support can use the same service contract after suitable crypto-news providers are configured.

For on-demand share requests, collection order should be:

1. return fresh persisted insights immediately;
2. request the configured official company feed/page;
3. request enabled independent news providers;
4. merge and deduplicate all results;
5. analyze only new articles.

### `NewsDeduplicationService`

Deduplicate using:

1. provider plus provider article ID;
2. normalized canonical URL;
3. fallback fingerprint from normalized title, source, and publication date.

Do not call OpenAI again for an already processed article unless explicit reanalysis is requested.

The same announcement may appear in an official RSS feed, investor-relations page, and external news provider. Canonical URL matching and title similarity should collapse those copies while retaining discovered source URLs and source classifications.

### `NewsImpactAnalysisService`

Use Spring AI `ChatClient`:

```java
@Service
public class NewsImpactAnalysisService {
    private final ChatClient chatClient;
}
```

The service must:

- accept only persisted and normalized article data;
- include catalog metadata such as symbol, name, region, sector, exchange, company website, and investor-relations URL;
- identify whether each article is an official company announcement or independent reporting;
- request structured output mapped to `NewsImpactAnalysis`;
- reject incomplete responses without source URLs;
- store the model name, prompt version, analysis timestamp, and result;
- return `UNKNOWN` when evidence is insufficient.

Suggested result record:

```java
public record NewsImpactAnalysis(
        String symbol,
        String instrumentName,
        NewsDirection direction,
        int confidence,
        int impactScore,
        NewsTimeHorizon timeHorizon,
        String summary,
        String reason,
        boolean alertRecommended,
        List<String> sourceUrls
) {
}
```

Suggested directions:

```text
UP
DOWN
NEUTRAL
UNKNOWN
```

Suggested time horizons:

```text
INTRADAY
DAYS
WEEKS
UNKNOWN
```

### `NewsMonitoringService`

On-demand coordinator called when a user requests current news for a symbol:

```java
public NewsRefreshResult refreshNews(
        Long userId,
        InstrumentType instrumentType,
        String symbol
)
```

Processing flow:

1. Reject the request when news monitoring is disabled.
2. Verify that the requested symbol exists and the logged-in user may view it.
3. Load symbol/company metadata from the appropriate catalog.
4. Return persisted insights immediately when they are still fresh enough.
5. Otherwise collect recent official company announcements and independent news within `lookback-hours`.
6. Deduplicate and persist new articles.
7. Analyze new articles with `NewsImpactAnalysisService`.
8. Persist and return the AI insight results.

Only the requested instrument is processed. A provider failure must not prevent results from other enabled providers from being returned.

No company-source request may run during application startup, stock scanning, crypto scanning, or catalog initialization.

Prevent duplicate concurrent refreshes for the same instrument. If a refresh is already running, return current persisted results with `refreshInProgress=true`.

### Optional Background Monitoring

Background monitoring can be added later for automatic alerts:

```java
@Scheduled(fixedDelayString = "${market-bot.news-monitoring.background-poll-interval-ms}")
public void monitorConfiguredNews()
```

This method must run only when both news monitoring and background monitoring are enabled. It is not part of the first implementation.

### `NewsInsightQueryService`

Read service used by REST controllers and chart dialogs.

Required queries:

- latest insights for a symbol;
- insights for a symbol and date range;
- insight details by ID;
- only currently valid insights;
- optionally only high-impact insights.

### `NewsNotificationService`

Integrate with the existing async notification architecture.

Send a news alert only when:

```text
alertRecommended = true
confidence >= minimum-confidence
impactScore >= minimum-impact-score
direction is UP or DOWN
sourceUrls is not empty
```

Users receive alerts only when:

- the instrument is enabled in their personal watchlist;
- at least one messenger is enabled;
- the same user has not already received the same insight.

`UserNewsAlertStateEntity` should identify a sent notification by `user_id + news_insight_id`.

## Spring AI Prompt Contract

System prompt:

```text
You are a financial news impact classifier.

Analyze only the supplied article information.
Do not invent facts.
Do not provide trading instructions.
Return UNKNOWN when evidence is weak or conflicting.
Every conclusion must reference at least one supplied source URL.
```

User input must include:

```json
{
  "instrument": {
    "symbol": "XFAB.PA",
    "name": "X-FAB",
    "region": "EU",
    "sector": "Semiconductors",
    "exchange": "Euronext Paris",
    "currency": "EUR"
  },
  "articles": [
    {
      "title": "Example title",
      "summary": "Provider summary",
      "source": "Example source",
      "sourceType": "OFFICIAL_COMPANY",
      "url": "https://example.com/article",
      "publishedAt": "2026-06-07T08:00:00Z"
    }
  ]
}
```

The prompt version must be stored with every analysis result so future prompt changes remain auditable.

## Persistence Model

### `news_article`

Suggested fields:

```text
id
provider
provider_article_id
canonical_url
title
summary
source_name
source_type              OFFICIAL_COMPANY or INDEPENDENT_NEWS
published_at
content_hash
created
modified
version
```

Suggested unique constraints:

```text
provider + provider_article_id
canonical_url
content_hash
```

### `news_insight`

Suggested fields:

```text
id
news_article_id
instrument_type        SHARE or CRYPTO
symbol
instrument_name
direction
confidence
impact_score
time_horizon
summary
reason
alert_recommended
model_name
prompt_version
analyzed_at
valid_until
created
modified
version
```

Indexes:

```text
symbol + analyzed_at
instrument_type + symbol + analyzed_at
impact_score + confidence
```

### `user_news_alert_state`

Suggested fields:

```text
id
user_id
news_insight_id
sent_at
created
modified
version
```

Unique constraint:

```text
user_id + news_insight_id
```

## REST API

All endpoints require JWT authentication.

### Request current symbol news

```http
POST /api/news/insights/refresh
Content-Type: application/json

{
  "instrumentType": "SHARE",
  "symbol": "XFAB.PA"
}
```

The endpoint validates access, performs or starts the on-demand provider/OpenAI analysis, and returns persisted plus newly analyzed insights. It does not require scheduled polling.

Suggested response:

```json
{
  "symbol": "XFAB.PA",
  "refreshedAt": "2026-06-07T10:30:00Z",
  "refreshInProgress": false,
  "insights": []
}
```

### List symbol insights

```http
GET /api/news/insights?instrumentType=SHARE&symbol=XFAB.PA&limit=20
```

Response:

```json
[
  {
    "id": 42,
    "symbol": "XFAB.PA",
    "instrumentName": "X-FAB",
    "direction": "DOWN",
    "confidence": 81,
    "impactScore": 76,
    "timeHorizon": "DAYS",
    "summary": "Short AI-generated summary.",
    "reason": "Reason based only on supplied sources.",
    "publishedAt": "2026-06-07T08:00:00Z",
    "analyzedAt": "2026-06-07T08:03:00Z",
    "sourceName": "Example source",
    "sourceUrl": "https://example.com/article"
  }
]
```

### Read insight details

```http
GET /api/news/insights/{id}
```

### Optional admin reanalysis

```http
POST /api/news/insights/{id}/reanalyze
```

This endpoint must require the `ADMIN` role.

## Chart Dialog UI Change

Add a `News` button to:

- `MarketChartDialog`;
- `CryptoChartDialog` when crypto-news support is enabled.

Recommended chart-dialog header layout:

```text
[Instrument title and metadata]                [News] [Close]
```

The `News` button should:

- use a familiar news/newspaper icon;
- include an accessible label such as `Show news for XFAB.PA`;
- show a small count badge when recent insights are available;
- open a separate `NewsInsightDialog`;
- trigger `POST /api/news/insights/refresh` for the selected symbol when the dialog opens;
- not navigate away from the chart page.

## Lightweight News Browser Dialog

Create a shared frontend component:

```text
frontend/src/features/news/components/NewsInsightDialog.tsx
```

The dialog is a lightweight browser for persisted news information. It should not attempt to reproduce a full web browser.

Layout:

```text
┌─────────────────────────────────────────────────────────────┐
│ News: XFAB.PA                                      [X Close] │
├─────────────────────────────────────────────────────────────┤
│ [All] [Positive] [Negative] [High impact] [Reload]           │
├───────────────────┬─────────────────────────────────────────┤
│ Article list      │ Selected insight details                │
│ - time/source     │ Direction, confidence, impact           │
│ - title           │ AI summary and reason                   │
│ - impact marker   │ Source information                      │
│                   │ [Open original article]                 │
└───────────────────┴─────────────────────────────────────────┘
```

Required behavior:

- request current news and analysis only when the dialog opens;
- display persisted insights immediately when available;
- show a visible `Checking current news` state during refresh;
- provide a `Refresh` button for an explicit repeated request;
- show loading, empty, and error states;
- filter by direction and impact;
- show publication and analysis timestamps;
- clearly distinguish provider text from AI-generated analysis;
- visibly distinguish official company announcements from independent news;
- open original source URLs in a new browser tab using `noopener,noreferrer`;
- never render arbitrary article HTML returned by a provider;
- never place the OpenAI API key in frontend configuration;
- support mobile layout by stacking article list above details.

Avoid embedding external articles in an iframe by default. Many sources block framing, and arbitrary embedded content creates security and privacy concerns. The lightweight browser should show saved summaries and provide an explicit `Open original article` action.

## Frontend Types

Suggested types:

```ts
export type NewsDirection = "UP" | "DOWN" | "NEUTRAL" | "UNKNOWN";

export type NewsInsight = {
  id: number;
  symbol: string;
  instrumentName: string;
  direction: NewsDirection;
  confidence: number;
  impactScore: number;
  timeHorizon: "INTRADAY" | "DAYS" | "WEEKS" | "UNKNOWN";
  summary: string;
  reason: string;
  publishedAt: string;
  analyzedAt: string;
  sourceName: string;
  sourceType: "OFFICIAL_COMPANY" | "INDEPENDENT_NEWS";
  sourceUrl: string;
};
```

## Security and Reliability

- Keep all provider and OpenAI credentials in backend secrets.
- Validate and normalize source URLs before persistence.
- Permit only `http` and `https` source links.
- Protect company-source requests against server-side request forgery: reject localhost, private-network, link-local, and cloud-metadata addresses.
- Apply DNS and redirect validation on every company-source request.
- Respect source terms, robots directives, response-size limits, and supported content types.
- Apply request timeouts and retry only transient provider/OpenAI failures.
- Limit articles per instrument before sending content to OpenAI.
- Store compact summaries/snippets, not full copyrighted article bodies.
- Record model name and prompt version for auditability.
- Apply rate limiting or bounded concurrency to AI calls.
- Treat AI analysis as informational context, not financial advice.

## Implementation Order

1. Add Spring AI OpenAI dependency and configuration.
2. Add news configuration subset to `AppProperties`.
3. Create entities, repositories, and database constraints.
4. Extend the stock catalog with company website, investor-relations, and news-feed URLs.
5. Implement official company RSS/Atom collection first because it is structured and normally does not require an API key.
6. Add constrained investor-relations HTML parsing as a fallback.
7. Implement an independent news provider to balance company-owned announcements.
8. Implement deduplication.
9. Implement Spring AI structured classification.
10. Add on-demand refresh and query REST endpoints.
11. Add `News` button to `MarketChartDialog`.
12. Add lightweight `NewsInsightDialog`.
13. Extend collection and UI support to crypto.
14. Optionally add background monitoring and user-specific news notifications.

## Acceptance Criteria

- On-demand news analysis can be enabled or disabled through configuration.
- Pressing the chart-dialog `News` button requests current information for that symbol/company.
- The first implementation does not require scheduled polling.
- Application startup and market-scanner cycles do not request news.
- Stock catalog records can store official company, investor-relations, and RSS/Atom URLs.
- On-demand share-news refresh requests configured official company sources.
- Official company announcements are visibly distinguished from independent reporting.
- News providers and OpenAI credentials remain backend-only.
- New articles are persisted and deduplicated before analysis.
- Spring AI `ChatClient` returns a structured `NewsImpactAnalysis`.
- Failed analysis does not stop other symbols from being processed.
- Users see news only for symbols they are allowed to view.
- Share chart dialog contains a `News` button.
- News dialog shows persisted source information and AI analysis.
- Source links open safely in a separate browser tab.
- Duplicate user notifications are prevented.
- No code path uses AI output as an automatic trading command.
