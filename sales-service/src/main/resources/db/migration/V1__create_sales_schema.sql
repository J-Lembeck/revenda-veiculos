ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sales_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO sales_app;

CREATE TABLE purchase (
    id                 uuid         PRIMARY KEY,
    customer_id        uuid         NOT NULL,
    customer_subject   varchar(100) NOT NULL,
    vehicle_id         uuid         NOT NULL,
    price_cents        bigint       CHECK (price_cents > 0),
    status             varchar(20)  NOT NULL DEFAULT 'PENDING',
    payment_status     varchar(20)  NOT NULL DEFAULT 'NONE',
    charge_id          varchar(64),
    payment_code       text,
    payment_due_at     timestamptz,
    paid_at            timestamptz,
    paid_amount_cents  bigint,
    completed_at       timestamptz,
    closed_reason      varchar(30),
    document_key       varchar(200),
    version            integer      NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT purchase_status_valid CHECK (status IN ('PENDING', 'RESERVED', 'AWAITING_PAYMENT', 'PAID',
        'COMPLETED', 'REJECTED', 'FAILED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT purchase_payment_status_valid CHECK (payment_status IN ('NONE', 'PENDING', 'PAID', 'CANCELLED',
        'REFUND_REQUESTED', 'REFUNDED')),
    CONSTRAINT purchase_closed_reason_valid CHECK (closed_reason IN ('VEHICLE_UNAVAILABLE', 'RESERVATION_FAILED',
        'CHARGE_FAILED', 'PAYMENT_EXPIRED', 'PAYMENT_MISMATCH', 'CUSTOMER_GAVE_UP', 'RECOVERY_TIMEOUT')),
    CONSTRAINT purchase_reserved_has_price CHECK (
        status IN ('PENDING', 'REJECTED') OR price_cents IS NOT NULL
        OR (closed_reason IS NOT NULL AND closed_reason IN ('RECOVERY_TIMEOUT', 'RESERVATION_FAILED'))),
    CONSTRAINT purchase_awaiting_has_charge CHECK (
        status <> 'AWAITING_PAYMENT' OR (charge_id IS NOT NULL AND payment_code IS NOT NULL AND payment_due_at IS NOT NULL))
);

CREATE UNIQUE INDEX purchase_one_active_per_customer ON purchase (customer_id)
    WHERE status IN ('PENDING', 'RESERVED', 'AWAITING_PAYMENT');
CREATE UNIQUE INDEX purchase_one_holder_per_vehicle ON purchase (vehicle_id)
    WHERE status IN ('RESERVED', 'AWAITING_PAYMENT', 'PAID', 'COMPLETED');
CREATE UNIQUE INDEX purchase_charge_key ON purchase (charge_id) WHERE charge_id IS NOT NULL;
CREATE INDEX purchase_payment_due   ON purchase (payment_due_at) WHERE status = 'AWAITING_PAYMENT';
CREATE INDEX purchase_in_flight     ON purchase (updated_at)     WHERE status IN ('PENDING', 'RESERVED');
CREATE INDEX purchase_by_customer   ON purchase (customer_id, created_at DESC);
CREATE INDEX purchase_by_status     ON purchase (status, created_at DESC);

COMMENT ON COLUMN purchase.customer_subject IS '[pessoal] sub do provedor de identidade do dono da compra (pseudônimo)';

CREATE TABLE purchase_transition (
    id              bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id     uuid         NOT NULL REFERENCES purchase (id),
    from_status     varchar(20),
    to_status       varchar(20)  NOT NULL,
    payment_status  varchar(20)  NOT NULL,
    reason          varchar(60),
    actor_type      varchar(10)  NOT NULL CHECK (actor_type IN ('USER', 'SERVICE', 'SYSTEM')),
    actor_id        varchar(100),
    occurred_at     timestamptz  NOT NULL
);
CREATE INDEX purchase_transition_by_purchase ON purchase_transition (purchase_id, id);
REVOKE UPDATE, DELETE, TRUNCATE ON purchase_transition FROM sales_app;

CREATE TABLE outbox_message (
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
CREATE INDEX outbox_message_due ON outbox_message (available_at) WHERE status = 'PENDING';
CREATE INDEX outbox_message_group ON outbox_message (destination, group_key, seq) WHERE status <> 'DONE';
CREATE INDEX outbox_message_failed ON outbox_message (created_at) WHERE status = 'FAILED';

CREATE TABLE audit_log (
    id           bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at  timestamptz  NOT NULL,
    actor_type   varchar(10)  NOT NULL CHECK (actor_type IN ('USER', 'SERVICE', 'SYSTEM')),
    actor_id     varchar(100),
    actor_role   varchar(20),
    action       varchar(60)  NOT NULL,
    entity_type  varchar(40)  NOT NULL,
    entity_id    uuid,
    summary      varchar(300) NOT NULL,
    reason       varchar(300),
    data         jsonb,
    request_id   varchar(64),
    trace_id     varchar(40)
);
CREATE INDEX audit_log_entity ON audit_log (entity_type, entity_id, occurred_at);
CREATE INDEX audit_log_actor  ON audit_log (actor_id, occurred_at);
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM sales_app;

CREATE TABLE idempotency_key (
    actor_id      varchar(100) NOT NULL,
    key           varchar(80)  NOT NULL,
    scope         varchar(80)  NOT NULL,
    request_hash  varchar(64)  NOT NULL,
    response_body jsonb,
    created_at    timestamptz  NOT NULL,
    PRIMARY KEY (actor_id, key)
);
CREATE INDEX idempotency_key_created ON idempotency_key (created_at);

CREATE TABLE processed_message (
    message_id    varchar(100) PRIMARY KEY,
    source        varchar(30)  NOT NULL,
    processed_at  timestamptz  NOT NULL
);
CREATE INDEX processed_message_processed ON processed_message (processed_at);
