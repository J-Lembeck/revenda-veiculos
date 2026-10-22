#!/bin/bash
set -eo pipefail

create_service_database() {
    local service="$1" owner_password="$2" app_password="$3"

    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -v db="$service" \
        -v owner="${service}_owner" \
        -v app="${service}_app" \
        -v owner_password="$owner_password" \
        -v app_password="$app_password" <<'SQL'
CREATE ROLE :"owner" LOGIN PASSWORD :'owner_password';
CREATE ROLE :"app" LOGIN PASSWORD :'app_password';
CREATE DATABASE :"db" OWNER :"owner";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"app";
SQL
}

create_service_database vehicles "$VEHICLES_DB_OWNER_PASSWORD" "$VEHICLES_DB_APP_PASSWORD"
create_service_database customers "$CUSTOMERS_DB_OWNER_PASSWORD" "$CUSTOMERS_DB_APP_PASSWORD"
create_service_database sales "$SALES_DB_OWNER_PASSWORD" "$SALES_DB_APP_PASSWORD"
