#!/usr/bin/env bash
# Roda o kit de avaliação contra um grupo.
# Uso: ./executar.sh adaptadores/grupo_08.json [perfil] [cenários...]
#   perfil: leve | padrao (padrão) | pesado
#   cenários: carga pico corrida idempotencia expiracao compensacao (padrão: todos)
# Variáveis: K6=/caminho/do/k6 (padrão: k6 no PATH)
#            DADOS=/outra/massa (padrão: dados/)  FATOR=10 (multiplica taxas e VUs; escala opcional)
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")" && pwd)"
ADAPTADOR="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
PERFIL="${2:-padrao}"
shift $(( $# >= 2 ? 2 : 1 ))
CENARIOS=("$@")
[ ${#CENARIOS[@]} -eq 0 ] && CENARIOS=(carga pico corrida idempotencia expiracao compensacao)
K6="${K6:-k6}"
DADOS="${DADOS:-$RAIZ/dados}"
FATOR="${FATOR:-}"

leia() { python3 -c "import json,sys; d=json.load(open('$ADAPTADOR')); print($1)"; }
GRUPO="$(leia "d['grupo']")"
BASE="$(leia "d['base_url']")"
CAB=()
while IFS= read -r h; do [ -n "$h" ] && CAB+=(-H "$h"); done < <(leia "'\n'.join(f'{k}: {v}' for k,v in {**(d.get('cabecalhos') or {}), **(d.get('cabecalhos_teste') or {})}.items())")
SAIDA="$RAIZ/resultados/$GRUPO"
mkdir -p "$SAIDA"

echo "== Grupo $GRUPO em $BASE, perfil $PERFIL"
for C in "${CENARIOS[@]}"; do
  echo "-- $C: zerando e carregando os dados"
  curl -fsS -X POST "${CAB[@]}" "$BASE/_teste/reset" >/dev/null
  curl -fsS -X POST "${CAB[@]}" -H 'Content-Type: application/json' \
       --data-binary "@$DADOS/carga.json" "$BASE/_teste/carga" >/dev/null
  echo "-- $C: rodando o k6"
  rm -f "$SAIDA/$C.console.log" "$SAIDA/$C.resumo.json" "$SAIDA/$C.verificacao.json"
  ( cd "$RAIZ" && "$K6" run --quiet -e CENARIO="$C" -e PERFIL="$PERFIL" -e ADAPTADOR="$ADAPTADOR" \
      -e DADOS="$DADOS" -e FATOR="$FATOR" -e SAIDA="$SAIDA" --console-output "$SAIDA/$C.console.log" \
      testes/cenarios.js ) || echo "   (k6 terminou com erro; o verificador registra o que houver)"
  echo "-- $C: verificando invariantes"
  python3 "$RAIZ/verificador/verificar.py" --adaptador "$ADAPTADOR" --cenario "$C" \
      --resultados "$SAIDA" --dados "$DADOS" || true
done
python3 "$RAIZ/verificador/consolidar.py" --resultados "$RAIZ/resultados"
