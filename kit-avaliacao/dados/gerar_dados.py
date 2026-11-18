#!/usr/bin/env python3
"""Gera a massa de dados compartilhada do kit de avaliação de ingressos.

A geração é determinística (semente fixa): rodar de novo produz os mesmos
arquivos, para que todos os grupos sejam testados com a mesma carga.

Uso: python3 dados/gerar_dados.py [--semente 2026] [--saida dados]

Escala opcional (os valores padrão geram a massa oficial da avaliação):
  --usuarios N    quantidade de usuários (padrão 50.000)
  --intencoes N   intenções de compra dos cenários carga e pico (padrão 30.000)
  --estadios N    estádios extras de 65.000 lugares, cada um com um evento N1, N2, ... (padrão 0)
"""
import argparse
import json
import random
from pathlib import Path

NOMES = ["Ana", "Bruno", "Carla", "Daniel", "Eduarda", "Felipe", "Gabriela", "Hugo", "Isabela", "João",
         "Karina", "Lucas", "Mariana", "Nicolas", "Olívia", "Pedro", "Quésia", "Rafael", "Sofia", "Tiago",
         "Úrsula", "Vitor", "Wesley", "Ximena", "Yasmin", "Zeca", "Larissa", "Mateus", "Beatriz", "Caio"]
SOBRENOMES = ["Silva", "Santos", "Oliveira", "Souza", "Lima", "Pereira", "Costa", "Rodrigues", "Almeida",
              "Nascimento", "Araújo", "Ferreira", "Carvalho", "Gomes", "Martins", "Rocha", "Barbosa",
              "Ribeiro", "Cavalcante", "Dias", "Monteiro", "Medeiros", "Freitas", "Batista", "Moura"]

CONFIG = {"ttl_reserva_segundos": 20, "atraso_pagamento_ms": 3000}


def setor(local_id, nome, filas, por_fila, preco):
    ids = [f"{local_id}-{nome}-{f:02d}-{n:03d}" for f in range(1, filas + 1) for n in range(1, por_fila + 1)]
    return {"setor": nome, "preco": preco, "filas": filas, "assentos_por_fila": por_fila, "assentos": ids}


def montar_locais():
    arena = [setor("ARENA", s, 25, 100, p) for s, p in
             [("A", 45000), ("B", 45000), ("C", 30000), ("D", 30000),
              ("E", 15000), ("F", 15000), ("G", 15000), ("H", 15000)]]
    teatro = [setor("TEATRO", "PLATEIA", 30, 20, 20000), setor("TEATRO", "BALCAO", 20, 20, 12000),
              setor("TEATRO", "CAMAROTE", 20, 10, 35000)]
    clube = [setor("CLUBE", "PISTA", 10, 20, 8000)]
    vip = [setor("VIP", "SALA", 5, 10, 100000)]
    return [
        {"local_id": "ARENA", "nome": "Arena Multiuso", "setores": arena},
        {"local_id": "TEATRO", "nome": "Teatro Municipal", "setores": teatro},
        {"local_id": "CLUBE", "nome": "Clube da Esquina", "setores": clube},
        {"local_id": "VIP", "nome": "Sala VIP", "setores": vip},
    ]


EVENTOS = [
    {"evento_id": "E1", "nome": "Festival de Verão", "local_id": "ARENA", "data": "2026-12-12T20:00:00-03:00"},
    {"evento_id": "E2", "nome": "Orquestra Sinfônica", "local_id": "TEATRO", "data": "2026-12-13T19:00:00-03:00"},
    {"evento_id": "E3", "nome": "Noite de Stand-up", "local_id": "CLUBE", "data": "2026-12-14T21:00:00-03:00"},
    {"evento_id": "E4", "nome": "Encontro com a Banda", "local_id": "VIP", "data": "2026-12-15T18:00:00-03:00"},
    {"evento_id": "E5", "nome": "Peça de Teatro", "local_id": "TEATRO", "data": "2026-12-16T20:00:00-03:00"},
]


N_USUARIOS = 50000


def uid(i):
    return f"U{i:0{max(6, len(str(N_USUARIOS)))}d}"


def usuario_aleatorio(rng):
    return uid(rng.randint(1, N_USUARIOS))


def usuarios(rng, n):
    out = []
    for i in range(1, n + 1):
        nome = f"{rng.choice(NOMES)} {rng.choice(SOBRENOMES)}"
        out.append({"usuario_id": uid(i), "nome": nome, "email": f"{uid(i).lower()}@teste.ufpb.br"})
    return out


def estadios(n):
    """Estádios extras para a escala opcional: 10 setores x 65 filas x 100 lugares = 65.000."""
    locais, eventos = [], []
    for k in range(1, n + 1):
        lid = f"ESTADIO{k}"
        precos = [90000, 90000, 60000, 60000, 40000, 40000, 25000, 25000, 15000, 15000]
        setores = [setor(lid, chr(ord("A") + j), 65, 100, precos[j]) for j in range(10)]
        locais.append({"local_id": lid, "nome": f"Estádio {k}", "setores": setores})
        eventos.append({"evento_id": f"N{k}", "nome": f"Turnê Nacional — show {k}", "local_id": lid,
                        "data": f"2027-03-{k:02d}T20:00:00-03:00" if k <= 28 else "2027-04-01T20:00:00-03:00"})
    return locais, eventos


def escolher_k(rng):
    return rng.choices([1, 2, 3, 4], weights=[40, 35, 15, 10])[0]


def assentos_contiguos(rng, st, k):
    """Escolhe k assentos lado a lado, com preferência pelas filas da frente."""
    fila = min(int(rng.expovariate(1 / (st["filas"] / 4))) + 1, st["filas"])
    k = min(k, st["assentos_por_fila"])
    inicio = rng.randint(1, st["assentos_por_fila"] - k + 1)
    base = st["assentos"][0].rsplit("-", 2)[0]
    return [f"{base}-{fila:02d}-{n:03d}" for n in range(inicio, inicio + k)]


def roteiro_carga(rng, locais, eventos, n=30000):
    por_local = {l["local_id"]: l for l in locais}
    ev = {e["evento_id"]: e for e in eventos}
    pesos_setor = {"ARENA": [20, 18, 14, 12, 10, 10, 8, 8], "TEATRO": [55, 25, 20], "CLUBE": [1]}
    extras = [e["evento_id"] for e in eventos if e["evento_id"].startswith("N")]
    for e in extras:
        pesos_setor[ev[e]["local_id"]] = [16, 14, 12, 11, 10, 9, 8, 7, 7, 6]
    ids_ev = ["E1", "E2", "E3"] + extras
    # sem estádios extras, os pesos são os da massa oficial; com eles, os estádios recebem 80% da demanda
    pesos_ev = [70, 20, 10] if not extras else [10, 6, 4] + [80 / len(extras)] * len(extras)
    largura = max(6, len(str(n)))
    out = []
    for i in range(1, n + 1):
        evento_id = rng.choices(ids_ev, weights=pesos_ev)[0]
        local = por_local[ev[evento_id]["local_id"]]
        st = rng.choices(local["setores"], weights=pesos_setor[local["local_id"]])[0]
        out.append({
            "id": f"C{i:0{largura}d}",
            "usuario_id": usuario_aleatorio(rng),
            "evento_id": evento_id,
            "setor": st["setor"],
            "assento_ids": assentos_contiguos(rng, st, escolher_k(rng)),
            "consultar_antes": rng.random() < 0.2,
            "simulacao": rng.choices(["aprovar", "recusar", "atrasar"], weights=[90, 7, 3])[0],
        })
    return out


def roteiro_corrida(rng, locais, n=5000):
    sala = next(l for l in locais if l["local_id"] == "VIP")["setores"][0]["assentos"]
    out = []
    for i in range(1, n + 1):
        k = rng.choices([1, 2], weights=[70, 30])[0]
        out.append({"id": f"R{i:05d}", "usuario_id": usuario_aleatorio(rng), "evento_id": "E4",
                    "setor": "SALA", "assento_ids": sorted(rng.sample(sala, k)), "simulacao": "aprovar"})
    return out


def assentos_e5(locais):
    teatro = next(l for l in locais if l["local_id"] == "TEATRO")
    return {s["setor"]: s["assentos"] for s in teatro["setores"]}


def roteiro_unitario(prefixo, assentos, simulacao, rng):
    return [{"id": f"{prefixo}{i:04d}", "usuario_id": usuario_aleatorio(rng), "evento_id": "E5",
             "setor": a.split("-")[1], "assento_ids": [a], "simulacao": simulacao}
            for i, a in enumerate(assentos, start=1)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--semente", type=int, default=2026)
    ap.add_argument("--saida", default=str(Path(__file__).parent))
    ap.add_argument("--usuarios", type=int, default=50000)
    ap.add_argument("--intencoes", type=int, default=30000)
    ap.add_argument("--estadios", type=int, default=0)
    args = ap.parse_args()
    global N_USUARIOS
    N_USUARIOS = args.usuarios
    rng = random.Random(args.semente)
    saida = Path(args.saida)
    saida.mkdir(parents=True, exist_ok=True)

    locais = montar_locais()
    locais_extra, eventos_extra = estadios(args.estadios)
    locais += locais_extra
    eventos = EVENTOS + eventos_extra
    carga = {"config": CONFIG, "locais": locais, "eventos": eventos, "usuarios": usuarios(rng, args.usuarios)}
    e5 = assentos_e5(locais)
    roteiros = {
        "carga": roteiro_carga(rng, locais, eventos, args.intencoes),
        "corrida": roteiro_corrida(rng, locais),
        "idempotencia": roteiro_unitario("I", e5["PLATEIA"][:400], "aprovar", rng),
        "expiracao": roteiro_unitario("X", e5["PLATEIA"][400:] + e5["BALCAO"][:200], "nao_pagar", rng),
        "compensacao": roteiro_unitario("P", e5["BALCAO"][200:] + e5["CAMAROTE"], "recusar", rng),
    }

    (saida / "carga.json").write_text(json.dumps(carga, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    for nome, itens in roteiros.items():
        (saida / f"roteiro_{nome}.json").write_text(
            json.dumps(itens, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    total = sum(len(s["assentos"]) for l in locais for s in l["setores"])
    print(f"locais: {len(locais)}, assentos: {total}, eventos: {len(eventos)}, usuarios: {len(carga['usuarios'])}")
    for nome, itens in roteiros.items():
        print(f"roteiro_{nome}: {len(itens)} intenções")


if __name__ == "__main__":
    main()
