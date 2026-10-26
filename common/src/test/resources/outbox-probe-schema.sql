CREATE TABLE IF NOT EXISTS outbox_message (
    id            uuid         PRIMARY KEY,
    seq           bigint       GENERATED ALWAYS AS IDENTITY UNIQUE,
    destination   varchar(20)  NOT NULL CHECK (destination IN ('VEHICLE_COMMANDS', 'PAYMENT_GATEWAY', 'DOCUMENTS')),
    type          varchar(40)  NOT NULL,
    aggregate_id  uuid         NOT NULL,
    group_key     varchar(64)  NOT NULL,
    payload       jsonb        NOT NULL,
    status        varchar(12)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    attempts      smallint     NOT NULL DEFAULT 0,
    available_at  timestamptz  NOT NULL,
    locked_until  timestamptz,
    last_error    varchar(500),
    created_at    timestamptz  NOT NULL,
    processed_at  timestamptz
);
CREATE INDEX IF NOT EXISTS outbox_message_due ON outbox_message (available_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS outbox_message_group ON outbox_message (destination, group_key, seq) WHERE status <> 'DONE';
CREATE INDEX IF NOT EXISTS outbox_message_failed ON outbox_message (created_at) WHERE status = 'FAILED';

CREATE TABLE IF NOT EXISTS probe_purchase (
    id      uuid        PRIMARY KEY,
    status  varchar(20) NOT NULL
);
