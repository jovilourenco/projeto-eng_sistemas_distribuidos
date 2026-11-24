#!/usr/bin/env bash
# Carrega a massa de dados do kit no TicketFlow. Uso: carregar.sh [pasta-dos-dados]
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
RAIZ_REPO="$(cd "$DIR/../.." && pwd)"
set -a; source "$RAIZ_REPO/.env"; set +a
DADOS="${1:-$DIR/../dados}"
mkdir -p "$DIR/saida"
python3 "$DIR/gerar_sql.py" "$DADOS/carga.json" "$DIR/saida"
docker exec -i ticketflow_postgres psql -v ON_ERROR_STOP=1 -q -o /dev/null -U "$DB_USER" -d evento_assento_db < "$DIR/saida/carga.sql"
echo "carga concluída"
