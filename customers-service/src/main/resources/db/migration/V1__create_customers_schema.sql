ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO customers_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO customers_app;

CREATE TABLE data_key (
    purpose        varchar(12)  NOT NULL CHECK (purpose IN ('FIELD', 'BLIND_INDEX')),
    version        smallint     NOT NULL,
    encrypted_key  bytea        NOT NULL,
    active         boolean      NOT NULL,
    created_at     timestamptz  NOT NULL,
    PRIMARY KEY (purpose, version)
);
CREATE UNIQUE INDEX data_key_one_active ON data_key (purpose) WHERE active;
COMMENT ON COLUMN data_key.encrypted_key IS '[segredo] chave de dados cifrada pela CMK do KMS (envelope); nunca em claro no banco';

CREATE TABLE customer (
    id                     uuid         PRIMARY KEY,
    identity_subject       varchar(100),
    full_name              varchar(150),
    cpf_enc                bytea,
    cpf_index              bytea,
    email_enc              bytea,
    phone_enc              bytea,
    birth_date_enc         bytea,
    address_enc            bytea,
    status                 varchar(12)  NOT NULL DEFAULT 'ACTIVE',
    deletion_requested_at  timestamptz,
    anonymized_at          timestamptz,
    version                integer      NOT NULL DEFAULT 0,
    created_at             timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL,
    CONSTRAINT customer_status_valid CHECK (status IN ('ACTIVE', 'ANONYMIZED')),
    CONSTRAINT customer_active_is_complete CHECK (
        status <> 'ACTIVE' OR (identity_subject IS NOT NULL AND full_name IS NOT NULL AND cpf_enc IS NOT NULL
            AND cpf_index IS NOT NULL AND email_enc IS NOT NULL AND phone_enc IS NOT NULL
            AND birth_date_enc IS NOT NULL AND address_enc IS NOT NULL)),
    CONSTRAINT customer_anonymized_is_empty CHECK (
        status <> 'ANONYMIZED' OR (anonymized_at IS NOT NULL AND identity_subject IS NULL AND full_name IS NULL
            AND cpf_enc IS NULL AND cpf_index IS NULL AND email_enc IS NULL AND phone_enc IS NULL
            AND birth_date_enc IS NULL AND address_enc IS NULL))
);

CREATE UNIQUE INDEX customer_subject_key   ON customer (identity_subject) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX customer_cpf_index_key ON customer (cpf_index)        WHERE status = 'ACTIVE';

COMMENT ON COLUMN customer.identity_subject IS '[pessoal] sub do provedor de identidade (pseudônimo)';
COMMENT ON COLUMN customer.full_name        IS '[pessoal] nome completo';
COMMENT ON COLUMN customer.cpf_enc          IS '[pessoal] CPF cifrado (AES-256-GCM, envelope KMS)';
COMMENT ON COLUMN customer.cpf_index        IS '[pessoal] blind index do CPF (HMAC-SHA256), para unicidade e conferência';
COMMENT ON COLUMN customer.email_enc        IS '[pessoal] e-mail cifrado, vindo do token';
COMMENT ON COLUMN customer.phone_enc        IS '[pessoal] telefone cifrado';
COMMENT ON COLUMN customer.birth_date_enc   IS '[pessoal] data de nascimento cifrada';
COMMENT ON COLUMN customer.address_enc      IS '[pessoal] endereço completo (JSON) cifrado';

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
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM customers_app;

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
