# Market Bot

Market Bot is a Spring Boot market-monitoring application. It runs a scheduled backend scanner that monitors configured stock symbols, writes scan information to application output, stores quote history in PostgreSQL, sends Telegram alerts when movement rules are matched, and exposes live scan data for a UI dashboard.

The application can still be run from the command line or as a Docker container, but it is no longer only a command-line process. It also starts an HTTP/WebSocket server for the dashboard frontend.

## How It Works

On startup, Market Bot loads configuration from `src/main/resources/application.yaml` and environment variables. The scanner then runs every `market-bot.poll-interval-ms` milliseconds.

For each symbol from the configured watchlist, the application:

1. Requests the current quote data.
2. Stores the quote in PostgreSQL.
3. Uses recent unsent quote records for the same symbol to calculate the latest delta and rolling movement.
4. Marks persisted quote records as sent after an alert or after the rolling window is processed.
5. Writes relevant scan information to output.
6. Sends a Telegram alert when the absolute rolling movement is greater than or equal to `market-bot.maximal-delta-price`.
7. Publishes structured scan results through REST/WebSocket endpoints for the UI dashboard.

After an alert is sent, the current quote and the recent quote history used for the alert are marked as sent.

The current scanner implementation uses Yahoo Finance quote data. The project also contains clients for Finnhub and Twelve Data, and their API keys are present in the configuration.

Yahoo Finance is currently used because other providers are limited for this use case on free plans. Some free APIs have very low request limits, and some do not cover the European market symbols needed by the watchlist.

## Configuration

Main configuration is in `src/main/resources/application.yaml`. Spring Boot is configured as a servlet web application, database settings are under `app.datasource` and `spring.jpa`, and the bot settings are under the `market-bot` prefix.

```yaml
app:
  datasource:
    url: jdbc:postgresql://localhost:5455/test_db
    username: test
    password: test
    configuration:

spring:
  main:
    web-application-type: servlet
  docker:
    compose:
      enabled: false
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        default_schema: marketbot
        connection:
          pool_size: 30

market-bot:
  telegram-bot-token: ${TELEGRAM_BOT_TOKEN:xxx}
  telegram-chat-id: ${TELEGRAM_CHAT_ID:11111111}
  watchlist-file: ${MARKET_BOT_WATCHLIST_FILE:classpath:/watchlist.yaml}

  watchlist:
    ABB.ST: ABB

  drop-alert-percent: -0.4
  rise-alert-percent: 0.4
  poll-interval-ms: 10000
  maximal-delta-price: 0.8
```

### Database

The application uses PostgreSQL through Spring Data JPA and HikariCP. The datasource is configured with the custom `app.datasource` prefix and connected to Spring Boot in `AppConfiguration`.

| Property | Description |
| --- | --- |
| `app.datasource.url` | PostgreSQL JDBC URL. Example: `jdbc:postgresql://localhost:5455/test_db`. |
| `app.datasource.username` | Database user. |
| `app.datasource.password` | Database password. |
| `app.datasource.configuration` | Prefix reserved for Hikari-specific datasource settings. |
| `spring.jpa.hibernate.ddl-auto` | Hibernate schema action. `update` creates or updates tables for mapped entities. |
| `spring.jpa.properties.hibernate.default_schema` | PostgreSQL schema used by Hibernate. Current value: `marketbot`. |
| `spring.jpa.properties.hibernate.connection.pool_size` | Hibernate connection pool setting. The configured Hikari datasource also sets maximum pool size in code. |

The PostgreSQL database and schema must exist before the application starts. Hibernate can create/update tables, but it does not create the database itself. If `default_schema` is `marketbot`, create the schema first:

```sql
CREATE SCHEMA IF NOT EXISTS marketbot;
```

The current persisted entity is `QuoteEntity`. It stores symbol, price values, percentage change, calculated delta, and a `send` flag used to avoid repeatedly processing the same quote records.

### Environment Variables

Use environment variables for secrets and deployment-specific values:

| Variable | Description |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token used to call the Telegram Bot API. |
| `TELEGRAM_CHAT_ID` | Telegram chat ID where alerts will be sent. |
| `FINNHUB_API_KEY` | Finnhub API key. Present in configuration for the Finnhub client. |
| `TWELVE_API_KEY` | Twelve Data API key. Present in configuration for the Twelve Data client. |
| `MARKET_BOT_WATCHLIST_FILE` | Optional path to an external watchlist file. If set and readable, it replaces `market-bot.watchlist`. |

An example file is provided in `.env.example`.

### Watchlist

The scanner can load symbols from an external file. This is the recommended setup for local/private watchlists because the real list can stay out of git.

Configure the file path with:

```yaml
market-bot:
  watchlist-file: /absolute/path/to/watchlist.local.yaml
```

The default value is:

```yaml
market-bot:
  watchlist-file: classpath:/watchlist.yaml
```

This loads [src/main/resources/watchlist.yaml](/Users/alexadmin/Desktop/work/WORK_PROJCTS/market-bot/src/main/resources/watchlist.yaml) when no external path is provided.

or with an environment variable:

```bash
MARKET_BOT_WATCHLIST_FILE=/absolute/path/to/watchlist.local.yaml
```

The file is reloaded on every scan, so changing it does not require rebuilding or restarting the app.

Recommended YAML format:

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

The local sample is [src/main/resources/watchlist.yaml](/Users/alexadmin/Desktop/work/WORK_PROJCTS/market-bot/src/main/resources/watchlist.yaml). Real watchlist files can stay ignored by git.

Supported watchlist item fields:

| Field | Description |
| --- | --- |
| `name` | Company name shown in logs, Telegram messages, and the dashboard. Required for structured items. |
| `region` | Region label used for future scan/filter controls, for example `US` or `EU`. |
| `sector` | Business sector used for dashboard grouping and future news filtering. |
| `exchange` | Exchange label, for example `NASDAQ`, `NYSE`, `XETRA`, or `SIX`. |
| `currency` | Quote currency, for example `USD`, `EUR`, or `CHF`. |
| `priority` | Favorite/rank level. Supported values: `HIGH`, `NORMAL`, `LOW`. |
| `enabled` | Set to `false` to keep a symbol in the file without scanning it. |

The scanner also supports the old simple format:

```yaml
watchlist:
  AAPL: Apple
  NVDA: NVIDIA
  BMW.DE: BMW
  ABBN.SW: ABB
```

`market-bot.watchlist` remains as a fallback map where:

- the key is the market symbol used for quote lookup;
- the value is the company name displayed in logs and Telegram messages.

For non-US shares, use the exchange suffix expected by Yahoo Finance, for example `.DE`, `.PA`, `.SW`, `.ST`, or `.AS`.

Local private watchlist files matching `watchlist.local.*` or `config/watchlist.*` are ignored by git.

### Alert Thresholds

| Property | Description |
| --- | --- |
| `drop-alert-percent` | Daily percent-change level that allows downward alerts. Example: `-0.4` means alerts can be sent when the symbol is down at least `0.4%` for the day. |
| `rise-alert-percent` | Daily percent-change level that allows upward alerts. Example: `0.4` means alerts can be sent when the symbol is up at least `0.4%` for the day. |
| `maximal-delta-price` | Minimum absolute movement required for the latest rolling movement. Example: `0.8` means the rolling change must be at least `0.8%` up or down. |
| `poll-interval-ms` | Delay between market scans in milliseconds. Example: `30000` scans every 30 seconds. |

The first scan only saves initial values. Alerts can start from the second scan because the application needs a previous value to calculate movement.

## Build

Requirements:

- Java 21
- Maven, or the included Maven wrapper

Create the executable JAR:

```bash
./mvnw clean package
```

This is the normal application build. It creates the Spring Boot executable JAR only and does not build or deploy Docker resources.

The generated JAR is created under `target/`, for example:

```text
target/market-bot-0.0.1-SNAPSHOT.jar
```

## Run From Command Line

Run the application with environment variables:

```bash
export TELEGRAM_BOT_TOKEN=replace-me
export TELEGRAM_CHAT_ID=replace-me
export FINNHUB_API_KEY=replace-me
export TWELVE_API_KEY=replace-me

java -jar target/market-bot-0.0.1-SNAPSHOT.jar
```

You can also override Spring configuration from the command line:

```bash
java -jar target/market-bot-0.0.1-SNAPSHOT.jar \
  --market-bot.poll-interval-ms=60000 \
  --market-bot.drop-alert-percent=-1.0 \
  --market-bot.rise-alert-percent=1.0 \
  --market-bot.maximal-delta-price=1.5
```

To use an external configuration file:

```bash
java -jar target/market-bot-0.0.1-SNAPSHOT.jar \
  --spring.config.location=file:./application.yaml
```

## Docker

Docker image build and deployment are tied to the Maven `docker` profile. Without this profile, Maven works as a normal Spring Boot application build and does not call Docker.

Prepare environment variables first:

```bash
cp .env.example .env
# edit .env with real values
```

For Docker, `MARKET_BOT_WATCHLIST_FILE` must point to a file inside the container. Mount your private watchlist file when starting the container, for example:

```yaml
services:
  market-bot:
    volumes:
      - ./config/watchlist.yaml:/app/config/watchlist.yaml:ro
    environment:
      MARKET_BOT_WATCHLIST_FILE: /app/config/watchlist.yaml
```

`compose.yaml` already passes the `MARKET_BOT_WATCHLIST_FILE` environment variable if it is set in `.env`. Add the bind mount that matches your local file location.

Build the JAR, build the Docker image, and deploy the container:

```bash
./mvnw clean package -Pdocker
```

The `docker` profile builds the image:

```text
market-bot:0.0.1-SNAPSHOT
```

and then starts the service with:

```bash
docker compose up -d
```

`compose.yaml` uses the already built image. It does not build the Docker image by itself.
