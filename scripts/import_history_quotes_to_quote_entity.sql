-- Import rows from marketbot.history_quote_entity into marketbot.quote_entity.
--
-- Usage examples:
--   psql -U test -d test_db -f scripts/import_history_quotes_to_quote_entity.sql
--
-- Optional psql variables:
--   psql -U test -d test_db \
--     -v history_interval="'DAILY'" \
--     -v from_date="'2026-01-01'" \
--     -v to_date="'2026-05-23'" \
--     -f scripts/import_history_quotes_to_quote_entity.sql
--
-- Notes:
--   - This script uses quote_entity_id_seq explicitly for quote_entity.id.
--   - Existing quote rows for the same symbol and calendar date are skipped.
--   - Keep the default DAILY interval unless you intentionally want to mix weekly/monthly
--     candles into the scanner table.

\if :{?history_interval}
\else
  \set history_interval '''DAILY'''
\endif

\if :{?from_date}
\else
  \set from_date '''1900-01-01'''
\endif

\if :{?to_date}
\else
  \set to_date '''2999-12-31'''
\endif

BEGIN;

SET LOCAL search_path TO marketbot;

INSERT INTO quote_entity (
    id,
    version,
    created,
    modified,
    symbol,
    current,
    change,
    percent_change,
    high,
    low,
    open,
    previous_close,
    delta,
    send
)
SELECT
    nextval('quote_entity_id_seq') AS id,
    0 AS version,
    h.trading_date::timestamp AS created,
    now() AS modified,
    h.symbol,
    h.current,
    h.change,
    h.percent_change,
    h.high,
    h.low,
    h.open,
    h.previous_close,
    h.delta,
    h.send
FROM history_quote_entity h
WHERE h.interval_type = :history_interval
  AND h.trading_date BETWEEN :from_date::date AND :to_date::date
  AND NOT EXISTS (
      SELECT 1
      FROM quote_entity q
      WHERE q.symbol = h.symbol
        AND q.created >= h.trading_date::timestamp
        AND q.created < (h.trading_date + 1)::timestamp
  )
ORDER BY h.symbol, h.trading_date;

COMMIT;
