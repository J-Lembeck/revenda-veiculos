ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO vehicles_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO vehicles_app;

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

CREATE TABLE probe_vehicle (
    id     uuid        PRIMARY KEY,
    plate  varchar(10) NOT NULL
);
