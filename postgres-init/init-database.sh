#!/bin/bash

set -e
set -u

function create_order_database() {
    local database=$ORDER_DB_NAME
    local user=$ORDER_DB_USER
    local password=$ORDER_DB_PASS

    echo "  Checking if database '$database' exists for user '$user'"

    USER_EXISTS=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -tAc "SELECT 1 FROM pg_roles WHERE rolname='$user'")

    if [ "$USER_EXISTS" != "1" ]; then
        echo "  Creating user '$user'"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
            CREATE USER "$user" WITH PASSWORD '$password';
EOSQL
    else
        echo "  User '$user' already exists"
    fi

    DB_EXISTS=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -tAc "SELECT 1 FROM pg_database WHERE datname='$database'")

    if [ "$DB_EXISTS" != "1" ]; then
        echo "  Creating database '$database'"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
            CREATE DATABASE "$database";
            GRANT ALL PRIVILEGES ON DATABASE "$database" TO "$user";

            ALTER DATABASE "$database" OWNER TO "$user";
EOSQL
    else
        echo "  Database '$database' already exists"
    fi
}

if [ -n "${ORDER_DB_NAME:-}" ] && [ -n "${ORDER_DB_USER:-}" ] && [ -n "${ORDER_DB_PASS:-}" ]; then
    create_order_database
else
    echo "  ORDER_DB variables not set, skipping custom DB creation."
fi