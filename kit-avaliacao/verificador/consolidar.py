#!/usr/bin/env python3
"""Junta os resultados de todos os grupos em resultados/comparativo.csv.

Uma linha por grupo e cenário, com as métricas de desempenho, o número de
invariantes violadas e quais foram.
"""
import argparse
import csv
import json
from pathlib import Path

ORDEM = ["carga", "pico", "corrida", "idempotencia", "expiracao", "compensacao"]
COLUNAS = ["grupo", "cenario", "violacoes", "invariantes_violadas", "requisicoes_por_s", "reservar_p95_ms",
           "pagar_p95_ms", "fluxo_p95_ms", "listar_p95_ms", "taxa_erro", "erros", "rejeicoes_controladas",
           "reservas_ok", "conflitos", "pagamentos_aprovados", "pagamentos_recusados", "assentos_vendidos",
           "vendas_confirmadas_cliente", "duracao_s"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--resultados", default=str(Path(__file__).resolve().parent.parent / "resultados"))
    a = ap.parse_args()
    raiz = Path(a.resultados)
    linhas = []
    for arq in sorted(raiz.glob("*/*.verificacao.json")):
        v = json.loads(arq.read_text(encoding="utf-8"))
        linha = {"grupo": v["grupo"], "cenario": v["cenario"], "violacoes": v["violacoes"],
                 "invariantes_violadas": "; ".join(c["invariante"] for c in v["invariantes"] if not c["ok"])}
        linha.update(v["desempenho"])
        linhas.append(linha)
    linhas.sort(key=lambda x: (ORDEM.index(x["cenario"]) if x["cenario"] in ORDEM else 99, x["grupo"]))
    destino = raiz / "comparativo.csv"
    with destino.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=COLUNAS, extrasaction="ignore")
        w.writeheader()
        w.writerows(linhas)
    print(f"{len(linhas)} linhas em {destino}")


if __name__ == "__main__":
    main()
