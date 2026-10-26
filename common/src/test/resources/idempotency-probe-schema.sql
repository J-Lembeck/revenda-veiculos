CREATE TABLE IF NOT EXISTS idempotency_key (
    actor_id      varchar(100) NOT NULL,
    key           varchar(80)  NOT NULL,
    scope         varchar(80)  NOT NULL,
    request_hash  varchar(64)  NOT NULL,
    response_body jsonb,
    created_at    timestamptz  NOT NULL,
    PRIMARY KEY (actor_id, key)
);
CREATE INDEX IF NOT EXISTS idempotency_key_created ON idempotency_key (created_at);
