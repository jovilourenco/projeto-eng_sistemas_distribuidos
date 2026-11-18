#!/usr/bin/env python3
"""Verifica as invariantes de negócio depois de um cenário e grava o resultado.

Uso: python3 verificador/verificar.py --adaptador adaptadores/grupo_08.json --cenario carga \
        --resultados resultados/grupo_08 [--dados dados]

Lê o estado final em GET /_teste/vendas, o registro de vendas confirmadas pelo k6
(<cenario>.console.log) e o resumo do k6 (<cenario>.resumo.json).
"""
import argparse
import json
import re
import sys
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

EVENTOS_DO_CENARIO = {"carga": ["E1", "E2", "E3"], "pico": ["E1", "E2", "E3"], "corrida": ["E4"],
                      "idempotencia": ["E5"], "expiracao": ["E5"], "compensacao": ["E5"]}


def buscar_vendas(adaptador, evento_id):
    url = f"{adaptador['base_url']}/_teste/vendas?evento_id={evento_id}"
    cab = dict(adaptador.get("cabecalhos") or {})
    cab.update(adaptador.get("cabecalhos_teste") or {})
    req = urllib.request.Request(url, headers=cab)
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode())


def metrica(resumo, nome, campo="count"):
    m = (resumo or {}).get("metrics", {}).get(nome)
    if not m:
        return 0
    return m.get("values", {}).get(campo, 0)


def ler_log(caminho):
    vendas, reservas = {}, {}
    if not caminho.exists():
        return vendas, reservas
    padrao = re.compile(r'msg="(VENDA|RESERVA) (\S+) (\S+) (\S+)"')
    for linha in caminho.open(encoding="utf-8", errors="replace"):
        m = padrao.search(linha)
        if m:
            (vendas if m.group(1) == "VENDA" else reservas)[m.group(2)] = (m.group(3), m.group(4))
    return vendas, reservas


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--adaptador", required=True)
    ap.add_argument("--cenario", required=True)
    ap.add_argument("--resultados", required=True)
    ap.add_argument("--dados", default=str(Path(__file__).resolve().parent.parent / "dados"))
    a = ap.parse_args()

    adaptador = json.loads(Path(a.adaptador).read_text(encoding="utf-8"))
    dados = json.loads((Path(a.dados) / "carga.json").read_text(encoding="utf-8"))
    roteiro_nome = "carga" if a.cenario == "pico" else a.cenario
    roteiro = json.loads((Path(a.dados) / f"roteiro_{roteiro_nome}.json").read_text(encoding="utf-8"))
    resultados = Path(a.resultados)
    resumo_arq = resultados / f"{a.cenario}.resumo.json"
    resumo = json.loads(resumo_arq.read_text(encoding="utf-8")) if resumo_arq.exists() else None
    vendas_cliente, reservas_cliente = ler_log(resultados / f"{a.cenario}.console.log")

    preco, capacidade, assentos_do_local = {}, {}, {}
    for l in dados["locais"]:
        ids = set()
        for s in l["setores"]:
            for x in s["assentos"]:
                preco[x] = s["preco"]
                ids.add(x)
        assentos_do_local[l["local_id"]] = ids
    local_do_evento = {e["evento_id"]: e["local_id"] for e in dados["eventos"]}

    checks = []

    def check(nome, ok, valor, detalhe=""):
        checks.append({"invariante": nome, "ok": bool(ok), "valor": valor, "detalhe": detalhe})

    vendidos_total = 0
    todas_vendas_servidor = set()
    estados = {}
    eventos_alvo = sorted({it["evento_id"] for it in roteiro}) or EVENTOS_DO_CENARIO[a.cenario]
    for ev in eventos_alvo:
        try:
            st = buscar_vendas(adaptador, ev)
        except Exception as e:  # noqa: BLE001
            check(f"{ev}: rota /_teste/vendas responde", False, None, str(e))
            continue
        estados[ev] = st
        vendas = st.get("vendas", [])
        pags = st.get("pagamentos_aprovados", [])
        reservas = st.get("reservas", [])
        validos = assentos_do_local[local_do_evento[ev]]

        por_assento = Counter(v["assento_id"] for v in vendas)
        duplicados = {k: n for k, n in por_assento.items() if n > 1}
        check(f"{ev}: nenhum assento vendido duas vezes", not duplicados, len(duplicados),
              ", ".join(list(duplicados)[:5]))
        inexistentes = [x for x in por_assento if x not in validos]
        check(f"{ev}: só assentos que existem foram vendidos", not inexistentes, len(inexistentes))
        check(f"{ev}: vendas dentro da capacidade", len(por_assento) <= len(validos),
              f"{len(por_assento)}/{len(validos)}")
        vendidos_total += len(por_assento)

        reservas_com_venda = {v["reserva_id"] for v in vendas}
        todas_vendas_servidor |= reservas_com_venda
        pags_por_reserva = Counter(p["reserva_id"] for p in pags)
        dupla = {k: n for k, n in pags_por_reserva.items() if n > 1}
        check(f"{ev}: nenhuma cobrança duplicada", not dupla, len(dupla))
        sem_venda = [k for k in pags_por_reserva if k not in reservas_com_venda]
        check(f"{ev}: toda cobrança aprovada tem ingresso", not sem_venda, len(sem_venda))
        sem_pag = [k for k in reservas_com_venda if k not in pags_por_reserva]
        check(f"{ev}: todo ingresso vendido foi pago", not sem_pag, len(sem_pag))

        assentos_por_reserva = defaultdict(list)
        for v in vendas:
            assentos_por_reserva[v["reserva_id"]].append(v["assento_id"])
        valor_errado = [p["reserva_id"] for p in pags if p["reserva_id"] in assentos_por_reserva and
                        p["valor"] != sum(preco.get(x, 0) for x in assentos_por_reserva[p["reserva_id"]])]
        check(f"{ev}: valor cobrado igual à soma dos preços", not valor_errado, len(valor_errado))

        por_chave = Counter(r.get("idempotency_key") for r in reservas if r.get("idempotency_key"))
        repetidas = {k: n for k, n in por_chave.items() if n > 1}
        check(f"{ev}: uma reserva por chave de idempotência", not repetidas, len(repetidas))

    perdidas = [rid for rid, (_, ev) in vendas_cliente.items()
                if ev in estados and rid not in todas_vendas_servidor]
    check("nenhuma venda confirmada ao cliente se perdeu", not perdidas, len(perdidas),
          ", ".join(perdidas[:5]))

    if a.cenario == "corrida":
        check("corrida: no máximo 50 assentos vendidos no E4", vendidos_total <= 50, vendidos_total)
    if a.cenario == "idempotencia":
        st = estados.get("E5", {})
        n_res = len(st.get("reservas", []))
        n_pag = len(st.get("pagamentos_aprovados", []))
        # Compras que não terminaram por erro já aparecem em "erros"; aqui só conta excesso.
        check(f"idempotência: no máximo {len(roteiro)} reservas (uma por compra)", n_res <= len(roteiro), n_res)
        check(f"idempotência: no máximo {len(roteiro)} cobranças (uma por compra)", n_pag <= len(roteiro), n_pag)
        check("idempotência: repetições devolvem a mesma reserva",
              metrica(resumo, "idempotencia_reserva_divergente") == 0,
              metrica(resumo, "idempotencia_reserva_divergente"))
        check("idempotência: repetição recebe a reserva original, não um conflito",
              metrica(resumo, "idempotencia_repeticao_recusada") == 0,
              metrica(resumo, "idempotencia_repeticao_recusada"))
        check("idempotência: repetições devolvem o mesmo resultado de pagamento",
              metrica(resumo, "idempotencia_pagamento_divergente") == 0,
              metrica(resumo, "idempotencia_pagamento_divergente"))
    if a.cenario in ("expiracao", "compensacao"):
        alvo = {x for it in roteiro for x in it["assento_ids"]}
        st = estados.get("E5", {})
        vend = [v for v in st.get("vendas", []) if v["assento_id"] in alvo]
        check(f"{a.cenario}: nenhum assento vendido", not vend, len(vend))
        abertas = [r for r in st.get("reservas", []) if r.get("status") in ("reservada", "pendente")]
        check(f"{a.cenario}: nenhuma reserva ficou aberta", not abertas, len(abertas))
        presos = metrica(resumo, "assentos_nao_liberados")
        check(f"{a.cenario}: assentos voltaram a ficar disponíveis", presos == 0 and resumo is not None, presos)
        if a.cenario == "compensacao":
            pags = st.get("pagamentos_aprovados", [])
            check("compensação: nenhuma cobrança aprovada", not pags, len(pags))

    dur = (resumo or {}).get("state", {}).get("testRunDurationMs", 0) / 1000
    desempenho = {
        "duracao_s": round(dur, 1),
        "requisicoes_por_s": round(metrica(resumo, "http_reqs", "rate"), 1),
        "reservar_p95_ms": round(metrica(resumo, "t_reservar", "p(95)"), 1),
        "pagar_p95_ms": round(metrica(resumo, "t_pagar", "p(95)"), 1),
        "listar_p95_ms": round(metrica(resumo, "t_listar", "p(95)"), 1),
        "fluxo_p95_ms": round(metrica(resumo, "t_fluxo_compra", "p(95)"), 1),
        "taxa_erro": round(metrica(resumo, "taxa_erro", "rate"), 4),
        "erros": metrica(resumo, "erro"),
        "rejeicoes_controladas": metrica(resumo, "rejeicao_controlada"),
        "reservas_ok": metrica(resumo, "reserva_ok"),
        "conflitos": metrica(resumo, "reserva_conflito"),
        "pagamentos_aprovados": metrica(resumo, "pagamento_aprovado"),
        "pagamentos_recusados": metrica(resumo, "pagamento_recusado"),
        "assentos_vendidos": vendidos_total,
        "vendas_confirmadas_cliente": len(vendas_cliente),
    }
    saida = {"grupo": adaptador.get("grupo"), "cenario": a.cenario, "resumo_k6_encontrado": resumo is not None,
             "invariantes": checks, "violacoes": sum(1 for c in checks if not c["ok"]), "desempenho": desempenho}
    (resultados / f"{a.cenario}.verificacao.json").write_text(json.dumps(saida, ensure_ascii=False, indent=1),
                                                              encoding="utf-8")
    for c in checks:
        print(("OK    " if c["ok"] else "FALHA ") + f"{c['invariante']} ({c['valor']})")
    print(json.dumps(desempenho, ensure_ascii=False))
    return 0 if saida["violacoes"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
