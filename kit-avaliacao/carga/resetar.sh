#!/usr/bin/env bash
# Zera o estado do TicketFlow entre cenários: eventos e assentos, reservas, pagamentos e Redis.
set -euo pipefail
RAIZ_REPO="$(cd "$(dirname "$0")/../.." && pwd)"
set -a; source "$RAIZ_REPO/.env"; set +a
psql_db() { docker exec -i ticketflow_postgres psql -v ON_ERROR_STOP=1 -q -U "$DB_USER" -d "$1"; }

psql_db evento_assento_db <<'SQL'
TRUNCATE evento_assento.assento, evento_assento.evento RESTART IDENTITY CASCADE;
SQL

# Reservas e pagamentos: esvazia as tabelas dos schemas de negócio (o histórico do Flyway fica no public)
for BANCO in reserva_db pagamento_db; do
  psql_db "$BANCO" <<'SQL'
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT schemaname, tablename FROM pg_tables
           WHERE schemaname NOT IN ('pg_catalog', 'information_schema', 'public') LOOP
    EXECUTE format('TRUNCATE %I.%I RESTART IDENTITY CASCADE', r.schemaname, r.tablename);
  END LOOP;
END $$;
SQL
done

docker exec ticketflow_redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning FLUSHALL >/dev/null
echo "estado zerado"
