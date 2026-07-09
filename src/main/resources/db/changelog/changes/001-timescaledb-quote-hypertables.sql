--liquibase formatted sql

--changeset market-bot:001-enable-timescaledb runInTransaction:false
--comment Enable TimescaleDB extension for PostgreSQL 17 / TimescaleDB deployments.
CREATE EXTENSION IF NOT EXISTS timescaledb;

--changeset market-bot:002-convert-quote-entity-to-hypertable runInTransaction:false splitStatements:false
--comment Convert marketbot.quote_entity to a TimescaleDB hypertable on created.
DO $$
DECLARE
    table_exists boolean;
    already_hypertable boolean;
    pk_name text;
    fk_name text;
BEGIN
    SELECT to_regclass('marketbot.quote_entity') IS NOT NULL INTO table_exists;
    IF NOT table_exists THEN
        RAISE NOTICE 'Skipping marketbot.quote_entity hypertable conversion because the table does not exist.';
        RETURN;
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM timescaledb_information.hypertables
        WHERE hypertable_schema = 'marketbot'
          AND hypertable_name = 'quote_entity'
    ) INTO already_hypertable;
    IF already_hypertable THEN
        RAISE NOTICE 'Skipping marketbot.quote_entity because it is already a hypertable.';
        RETURN;
    END IF;

    UPDATE marketbot.quote_entity
    SET created = now()
    WHERE created IS NULL;

    ALTER TABLE marketbot.quote_entity
        ALTER COLUMN created SET NOT NULL;

    IF to_regclass('marketbot.user_symbol_alert_state') IS NOT NULL THEN
        ALTER TABLE marketbot.user_symbol_alert_state
            ADD COLUMN IF NOT EXISTS last_sent_quote_created timestamp(6) with time zone;

        UPDATE marketbot.user_symbol_alert_state alert_state
        SET last_sent_quote_created = quote.created
        FROM marketbot.quote_entity quote
        WHERE alert_state.last_sent_quote_id = quote.id
          AND alert_state.last_sent_quote_created IS NULL;

        ALTER TABLE marketbot.user_symbol_alert_state
            ALTER COLUMN last_sent_quote_created SET NOT NULL;

        FOR fk_name IN
            SELECT con.conname
            FROM pg_constraint con
            WHERE con.contype = 'f'
              AND con.conrelid = 'marketbot.user_symbol_alert_state'::regclass
              AND con.confrelid = 'marketbot.quote_entity'::regclass
        LOOP
            EXECUTE format('ALTER TABLE marketbot.user_symbol_alert_state DROP CONSTRAINT %I', fk_name);
        END LOOP;
    END IF;

    SELECT tc.constraint_name
    INTO pk_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_schema = kcu.constraint_schema
     AND tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
     AND tc.table_name = kcu.table_name
    WHERE tc.constraint_type = 'PRIMARY KEY'
      AND tc.table_schema = 'marketbot'
      AND tc.table_name = 'quote_entity'
    GROUP BY tc.constraint_name
    HAVING array_agg(kcu.column_name::text ORDER BY kcu.ordinal_position) = ARRAY['id']::text[];

    IF pk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE marketbot.quote_entity DROP CONSTRAINT %I', pk_name);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_type = 'PRIMARY KEY'
          AND table_schema = 'marketbot'
          AND table_name = 'quote_entity'
    ) THEN
        ALTER TABLE marketbot.quote_entity
            ADD CONSTRAINT pk_quote_entity_id_created PRIMARY KEY (id, created);
    END IF;

    PERFORM create_hypertable('marketbot.quote_entity', 'created', if_not_exists => TRUE, migrate_data => TRUE);

    CREATE INDEX IF NOT EXISTS idx_quote_entity_created_desc
        ON marketbot.quote_entity (created DESC);
    CREATE INDEX IF NOT EXISTS idx_quote_entity_symbol_created_desc
        ON marketbot.quote_entity (symbol, created DESC);

    IF to_regclass('marketbot.user_symbol_alert_state') IS NOT NULL
       AND NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_user_symbol_alert_state_quote_id_created'
              AND conrelid = 'marketbot.user_symbol_alert_state'::regclass
       ) THEN
        ALTER TABLE marketbot.user_symbol_alert_state
            ADD CONSTRAINT fk_user_symbol_alert_state_quote_id_created
            FOREIGN KEY (last_sent_quote_id, last_sent_quote_created)
            REFERENCES marketbot.quote_entity (id, created);
    END IF;
END
$$;

--changeset market-bot:003-convert-crypto-quote-to-hypertable runInTransaction:false splitStatements:false
--comment Convert marketbot.crypto_quote to a TimescaleDB hypertable on created.
DO $$
DECLARE
    table_exists boolean;
    already_hypertable boolean;
    pk_name text;
    fk_name text;
BEGIN
    SELECT to_regclass('marketbot.crypto_quote') IS NOT NULL INTO table_exists;
    IF NOT table_exists THEN
        RAISE NOTICE 'Skipping marketbot.crypto_quote hypertable conversion because the table does not exist.';
        RETURN;
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM timescaledb_information.hypertables
        WHERE hypertable_schema = 'marketbot'
          AND hypertable_name = 'crypto_quote'
    ) INTO already_hypertable;
    IF already_hypertable THEN
        RAISE NOTICE 'Skipping marketbot.crypto_quote because it is already a hypertable.';
        RETURN;
    END IF;

    UPDATE marketbot.crypto_quote
    SET created = now()
    WHERE created IS NULL;

    ALTER TABLE marketbot.crypto_quote
        ALTER COLUMN created SET NOT NULL;

    IF to_regclass('marketbot.crypto_user_symbol_alert_state') IS NOT NULL THEN
        ALTER TABLE marketbot.crypto_user_symbol_alert_state
            ADD COLUMN IF NOT EXISTS last_sent_crypto_quote_created timestamp(6) with time zone;

        UPDATE marketbot.crypto_user_symbol_alert_state alert_state
        SET last_sent_crypto_quote_created = quote.created
        FROM marketbot.crypto_quote quote
        WHERE alert_state.last_sent_crypto_quote_id = quote.id
          AND alert_state.last_sent_crypto_quote_created IS NULL;

        ALTER TABLE marketbot.crypto_user_symbol_alert_state
            ALTER COLUMN last_sent_crypto_quote_created SET NOT NULL;

        FOR fk_name IN
            SELECT con.conname
            FROM pg_constraint con
            WHERE con.contype = 'f'
              AND con.conrelid = 'marketbot.crypto_user_symbol_alert_state'::regclass
              AND con.confrelid = 'marketbot.crypto_quote'::regclass
        LOOP
            EXECUTE format('ALTER TABLE marketbot.crypto_user_symbol_alert_state DROP CONSTRAINT %I', fk_name);
        END LOOP;
    END IF;

    SELECT tc.constraint_name
    INTO pk_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_schema = kcu.constraint_schema
     AND tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
     AND tc.table_name = kcu.table_name
    WHERE tc.constraint_type = 'PRIMARY KEY'
      AND tc.table_schema = 'marketbot'
      AND tc.table_name = 'crypto_quote'
    GROUP BY tc.constraint_name
    HAVING array_agg(kcu.column_name::text ORDER BY kcu.ordinal_position) = ARRAY['id']::text[];

    IF pk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE marketbot.crypto_quote DROP CONSTRAINT %I', pk_name);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_type = 'PRIMARY KEY'
          AND table_schema = 'marketbot'
          AND table_name = 'crypto_quote'
    ) THEN
        ALTER TABLE marketbot.crypto_quote
            ADD CONSTRAINT pk_crypto_quote_id_created PRIMARY KEY (id, created);
    END IF;

    PERFORM create_hypertable('marketbot.crypto_quote', 'created', if_not_exists => TRUE, migrate_data => TRUE);

    CREATE INDEX IF NOT EXISTS idx_crypto_quote_created_desc
        ON marketbot.crypto_quote (created DESC);
    CREATE INDEX IF NOT EXISTS idx_crypto_quote_symbol_created_desc
        ON marketbot.crypto_quote (symbol, created DESC);
    CREATE INDEX IF NOT EXISTS idx_crypto_quote_base_asset_created_desc
        ON marketbot.crypto_quote (base_asset, created DESC);

    IF to_regclass('marketbot.crypto_user_symbol_alert_state') IS NOT NULL
       AND NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_crypto_user_symbol_alert_state_quote_id_created'
              AND conrelid = 'marketbot.crypto_user_symbol_alert_state'::regclass
       ) THEN
        ALTER TABLE marketbot.crypto_user_symbol_alert_state
            ADD CONSTRAINT fk_crypto_user_symbol_alert_state_quote_id_created
            FOREIGN KEY (last_sent_crypto_quote_id, last_sent_crypto_quote_created)
            REFERENCES marketbot.crypto_quote (id, created);
    END IF;
END
$$;
