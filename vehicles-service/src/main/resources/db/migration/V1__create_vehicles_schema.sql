ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO vehicles_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO vehicles_app;

CREATE TABLE vehicle (
    id                uuid         PRIMARY KEY,
    brand             varchar(60)  NOT NULL,
    model             varchar(60)  NOT NULL,
    trim_name         varchar(80),
    manufacture_year  smallint     NOT NULL CHECK (manufacture_year BETWEEN 1900 AND 2100),
    model_year        smallint     NOT NULL,
    color             varchar(30)  NOT NULL,
    mileage_km        integer      NOT NULL CHECK (mileage_km >= 0),
    plate             varchar(7)   NOT NULL,
    vin               char(17)     NOT NULL,
    renavam           char(11)     NOT NULL,
    price_cents       bigint       NOT NULL CHECK (price_cents > 0 AND price_cents <= 1000000000),
    status            varchar(12)  NOT NULL DEFAULT 'AVAILABLE',
    purchase_id       uuid,
    sold_price_cents  bigint       CHECK (sold_price_cents > 0),
    sold_at           timestamptz,
    delivered_at      timestamptz,
    version           integer      NOT NULL DEFAULT 0,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    CONSTRAINT vehicle_status_valid CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD', 'DELIVERED')),
    CONSTRAINT vehicle_model_year_valid CHECK (model_year BETWEEN manufacture_year AND manufacture_year + 1),
    CONSTRAINT vehicle_purchase_matches_status CHECK ((status = 'AVAILABLE') = (purchase_id IS NULL)),
    CONSTRAINT vehicle_sale_matches_status CHECK (
        (status IN ('SOLD', 'DELIVERED')) = (sold_price_cents IS NOT NULL AND sold_at IS NOT NULL)),
    CONSTRAINT vehicle_delivery_matches_status CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL))
);

CREATE UNIQUE INDEX vehicle_plate_key   ON vehicle (plate);
CREATE UNIQUE INDEX vehicle_vin_key     ON vehicle (vin);
CREATE UNIQUE INDEX vehicle_renavam_key ON vehicle (renavam);
CREATE INDEX vehicle_available_by_price ON vehicle (price_cents, id) WHERE status = 'AVAILABLE';
CREATE INDEX vehicle_sold_by_price      ON vehicle (sold_price_cents, id) WHERE status IN ('SOLD', 'DELIVERED');

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
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM vehicles_app;

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
