#!/usr/bin/env bash
# Derruba um serviço do grupo no meio do cenário de carga e o sobe de novo.
# Uso: ./testes/caos.sh adaptadores/grupo_08.json [atraso_s=90] [fora_do_ar_s=30]
# Rode em outro terminal logo depois de iniciar ./executar.sh <adaptador> padrao carga.
# Usa "caos.diretorio_compose" e "caos.servico_para_derrubar" do adaptador.
set -euo pipefail
ADAPTADOR="$1"; ATRASO="${2:-90}"; FORA="${3:-30}"
DIR="$(python3 -c "import json; print(json.load(open('$ADAPTADOR'))['caos']['diretorio_compose'])")"
SVC="$(python3 -c "import json; print(json.load(open('$ADAPTADOR'))['caos']['servico_para_derrubar'])")"
[ -z "$DIR" ] || [ -z "$SVC" ] && { echo "Preencha caos.diretorio_compose e caos.servico_para_derrubar no adaptador."; exit 1; }
sleep "$ATRASO"
echo "$(date +%T) derrubando $SVC"; (cd "$DIR" && docker compose kill "$SVC")
sleep "$FORA"
echo "$(date +%T) subindo $SVC"; (cd "$DIR" && docker compose start "$SVC")
