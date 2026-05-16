# Market Bot

Market Bot is a Spring Boot command-line application that monitors configured stock market symbols and sends Telegram alerts when share prices move enough to match the configured alert rules.

The application runs without an HTTP server because `spring.main.web-application-type` is set to `none`. It starts a scheduled scanner, reads market quotes, compares every symbol with the previously observed value, and posts an alert message to a configured Telegram chat when the movement is significant.

## How It Works

On startup, Market Bot loads configuration from `src/main/resources/application.yaml` and environment variables. The scanner then runs every `market-bot.poll-interval-ms` milliseconds.

For each symbol in `market-bot.watchlist`, the application:

1. Requests the current quote data.
2. Stores the first observed daily percentage change as the initial baseline.
3. On the next scans, compares the current daily percentage change with the previous value.
4. Adds the delta to a short rolling history for that symbol.
5. Sends a Telegram alert when both conditions are true:
   - the absolute rolling movement is greater than or equal to `market-bot.maximal-delta-price`;
   - the current daily percentage change is outside the configured absolute thresholds:
     - less than or equal to `market-bot.drop-alert-percent`, or
     - greater than or equal to `market-bot.rise-alert-percent`.

After an alert is sent, the rolling history for that symbol is cleared so the next alert requires a new movement sequence.

The current scanner implementation uses Yahoo Finance quote data. The project also contains clients for Finnhub and Twelve Data, and their API keys are present in the configuration.

Yahoo Finance is currently used because other providers are limited for this use case on free plans. Some free APIs have very low request limits, and some do not cover the European market symbols needed by the watchlist.

## Configuration

Main configuration is in `src/main/resources/application.yaml`. Spring Boot is configured as a non-web application, and the bot settings are under the `market-bot` prefix.

```yaml
spring:
  main:
    web-application-type: none
  docker:
    compose:
      enabled: false

market-bot:
  finnhub-api-key: ${FINNHUB_API_KEY:xxxx}
  telegram-bot-token: ${TELEGRAM_BOT_TOKEN:xxx}
  telegram-chat-id: ${TELEGRAM_CHAT_ID:11111111}
  twelve-data-api-key: ${TWELVE_API_KEY:$4k24k24k23kl4}

  watchlist:
    AAPL: Apple
    AMD: Advanced Micro Devices
    NVDA: NVIDIA
    BMW.DE: BMW

  drop-alert-percent: -0.4
  rise-alert-percent: 0.4
  poll-interval-ms: 30000
  maximal-delta-price: 0.8
```

### Environment Variables

Use environment variables for secrets and deployment-specific values:

| Variable | Description |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token used to call the Telegram Bot API. |
| `TELEGRAM_CHAT_ID` | Telegram chat ID where alerts will be sent. |
| `FINNHUB_API_KEY` | Finnhub API key. Present in configuration for the Finnhub client. |
| `TWELVE_API_KEY` | Twelve Data API key. Present in configuration for the Twelve Data client. |

An example file is provided in `.env.example`.

### Watchlist

`market-bot.watchlist` is a map where:

- the key is the market symbol used for quote lookup;
- the value is the company name displayed in logs and Telegram messages.

Examples:

```yaml
watchlist:
  AAPL: Apple
  NVDA: NVIDIA
  BMW.DE: BMW
  ABBN.SW: ABB
```

For non-US shares, use the exchange suffix expected by Yahoo Finance, for example `.DE`, `.PA`, `.SW`, `.ST`, or `.AS`.

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
