CREATE TABLE IF NOT EXISTS data_key (
    purpose        varchar(12)  NOT NULL CHECK (purpose IN ('FIELD', 'BLIND_INDEX')),
    version        smallint     NOT NULL,
    encrypted_key  bytea        NOT NULL,
    active         boolean      NOT NULL,
    created_at     timestamptz  NOT NULL,
    PRIMARY KEY (purpose, version)
);
CREATE UNIQUE INDEX IF NOT EXISTS data_key_one_active ON data_key (purpose) WHERE active;

CREATE TABLE IF NOT EXISTS probe_customer (
    id         uuid   PRIMARY KEY,
    cpf_enc    bytea  NOT NULL,
    cpf_index  bytea  NOT NULL
);
