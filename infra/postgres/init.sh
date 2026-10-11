#!/bin/bash
set -euo pipefail
for service in accounts payments customers batch; do
  variable="DB_${service^^}_PASSWORD"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v user="bank_$service" -v pass="${!variable}" -v db="bank_$service" <<'SQL'
CREATE USER :"user" PASSWORD :'pass';
CREATE DATABASE :"db" OWNER :"user";
REVOKE CONNECT ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"user";
SQL
done
