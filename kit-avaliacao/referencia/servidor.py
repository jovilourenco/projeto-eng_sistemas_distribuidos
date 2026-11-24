#!/usr/bin/env python3
"""Servidor de referência do contrato de teste (só biblioteca padrão).

Serve para os grupos verem o comportamento esperado e para validar o kit.
Não é um sistema distribuído: usa um único lock em memória.

Uso: python3 referencia/servidor.py [--porta 8080] [--bug NOME]
Bugs injetáveis, para conferir que o verificador os detecta:
  overbooking   verifica e marca o assento sem exclusão mútua (corrida)
  idempotencia  ignora a Idempotency-Key
  expiracao     reservas nunca expiram
  compensacao   pagamento recusado não libera os assentos
  cobranca      pagamento repetido cobra de novo
"""
import argparse
import json
import threading
import time
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

BUG = set()
L = threading.Lock()
S = {}


def zerar():
    S.update(config={"ttl_reserva_segundos": 20, "atraso_pagamento_ms": 3000}, precos={}, setor_de={},
             assentos={}, reservas={}, idem_reserva={}, idem_pag={}, pagamentos=[])


zerar()
S["eventos"] = {}
S["locais"] = {}


def carregar(dados):
    with L:
        zerar()
        S["config"] = dados.get("config", S["config"])
        S["locais"] = {l["local_id"]: l for l in dados["locais"]}
        S["eventos"] = {e["evento_id"]: e for e in dados["eventos"]}
        for ev in dados["eventos"]:
            local = S["locais"][ev["local_id"]]
            st = {}
            for s in local["setores"]:
                for a in s["assentos"]:
                    st[a] = "disponivel"
                    S["precos"][a] = s["preco"]
                    S["setor_de"][a] = s["setor"]
            S["assentos"][ev["evento_id"]] = st


def expirar():
    if "expiracao" in BUG:
        return
    agora = time.time()
    for r in S["reservas"].values():
        if r["status"] == "reservada" and r["expira"] <= agora:
            r["status"] = "expirada"
            for a in r["assento_ids"]:
                S["assentos"][r["evento_id"]][a] = "disponivel"


def varredor():
    while True:
        time.sleep(1)
        with L:
            expirar()


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def responder(self, cod, corpo=None):
        b = json.dumps(corpo if corpo is not None else {}, ensure_ascii=False).encode()
        self.send_response(cod)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def corpo(self):
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n) or b"{}") if n else {}

    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        p = u.path.strip("/").split("/")
        if p[:1] == ["eventos"] and len(p) == 3 and p[2] == "assentos":
            with L:
                expirar()
                st = S["assentos"].get(p[1])
                if st is None:
                    return self.responder(404, {"erro": "evento inexistente"})
                lista = [{"assento_id": a, "status": s} for a, s in st.items()
                         if not q.get("setor") or S["setor_de"][a] == q["setor"]]
            return self.responder(200, {"evento_id": p[1], "setor": q.get("setor"), "assentos": lista})
        if p[:1] == ["reservas"] and len(p) == 2:
            with L:
                expirar()
                r = S["reservas"].get(p[1])
                if not r:
                    return self.responder(404, {"erro": "reserva inexistente"})
                return self.responder(200, self.publica(r))
        if p == ["_teste", "vendas"]:
            ev = q.get("evento_id")
            with L:
                expirar()
                rs = [r for r in S["reservas"].values() if r["evento_id"] == ev]
                vendas = [{"reserva_id": r["id"], "assento_id": a, "usuario_id": r["usuario_id"],
                           "idempotency_key": r["chave"]} for r in rs if r["status"] == "confirmada"
                          for a in r["assento_ids"]]
                pags = [x for x in S["pagamentos"] if S["reservas"][x["reserva_id"]]["evento_id"] == ev]
                res = [{"reserva_id": r["id"], "status": r["status"], "assento_ids": r["assento_ids"],
                        "idempotency_key": r["chave"]} for r in rs]
            return self.responder(200, {"evento_id": ev, "vendas": vendas, "pagamentos_aprovados": pags,
                                        "reservas": res})
        self.responder(404, {"erro": "rota inexistente"})

    @staticmethod
    def publica(r):
        exp = datetime.fromtimestamp(r["expira"], timezone.utc).isoformat()
        return {"reserva_id": r["id"], "status": r["status"], "valor_total": r["valor"], "expira_em": exp}

    def do_POST(self):
        p = urlparse(self.path).path.strip("/").split("/")
        if p == ["_teste", "reset"]:
            with L:
                for k in ("reservas", "idem_reserva", "idem_pag"):
                    S[k] = {}
                S["pagamentos"] = []
                for st in S["assentos"].values():
                    for a in st:
                        st[a] = "disponivel"
            return self.responder(200, {"ok": True})
        if p == ["_teste", "carga"]:
            carregar(self.corpo())
            return self.responder(201, {"ok": True})
        if p == ["reservas"]:
            return self.reservar(self.corpo(), self.headers.get("Idempotency-Key"))
        if len(p) == 3 and p[0] == "reservas" and p[2] == "pagamento":
            return self.pagar(p[1], self.corpo(), self.headers.get("Idempotency-Key"))
        self.responder(404, {"erro": "rota inexistente"})

    def reservar(self, c, chave):
        ev, ids = c.get("evento_id"), c.get("assento_ids") or []
        if "overbooking" in BUG:
            st = S["assentos"].get(ev)
            if st is None or any(st.get(a) != "disponivel" for a in ids):
                return self.responder(409, {"erro": "indisponivel"})
            time.sleep(0.005)  # janela de corrida
            for a in ids:
                st[a] = "reservado"
            with L:
                return self.criar(c, chave, ev, ids)
        with L:
            expirar()
            if chave and "idempotencia" not in BUG and chave in S["idem_reserva"]:
                return self.responder(200, self.publica(S["reservas"][S["idem_reserva"][chave]]))
            st = S["assentos"].get(ev)
            if st is None:
                return self.responder(404, {"erro": "evento inexistente"})
            if not ids or any(st.get(a) != "disponivel" for a in ids):
                return self.responder(409, {"erro": "assento indisponivel"})
            for a in ids:
                st[a] = "reservado"
            return self.criar(c, chave, ev, ids)

    def criar(self, c, chave, ev, ids):
        r = {"id": uuid.uuid4().hex, "evento_id": ev, "usuario_id": c.get("usuario_id"), "assento_ids": ids,
             "status": "reservada", "valor": sum(S["precos"][a] for a in ids), "chave": chave,
             "expira": time.time() + S["config"]["ttl_reserva_segundos"]}
        S["reservas"][r["id"]] = r
        if chave:
            S["idem_reserva"][chave] = r["id"]
        return self.responder(201, self.publica(r))

    def pagar(self, rid, c, chave):
        sim = c.get("simulacao", "aprovar")
        usa_chave = bool(chave) and "cobranca" not in BUG
        with L:
            expirar()
            if usa_chave and chave in S["idem_pag"]:
                pendente = S["idem_pag"][chave]
            else:
                pendente = None
                entrada = {"pronto": threading.Event(), "res": None}
                if usa_chave:
                    S["idem_pag"][chave] = entrada
        if pendente is not None:  # mesma chave: espera o primeiro terminar e devolve o mesmo resultado
            pendente["pronto"].wait(60)
            return self.responder(*pendente["res"])
        res = self.processar_pagamento(rid, sim, chave)
        entrada["res"] = res
        entrada["pronto"].set()
        return self.responder(*res)

    def processar_pagamento(self, rid, sim, chave):
        with L:
            r = S["reservas"].get(rid)
            if not r:
                return (404, {"erro": "reserva inexistente"})
            if r["status"] == "confirmada" and "cobranca" not in BUG:
                return (200, {"reserva_id": rid, "status": "confirmada", "valor_cobrado": r["valor"]})
            if r["status"] not in ("reservada", "confirmada"):
                return (409, {"reserva_id": rid, "status": r["status"]})
        if sim == "atrasar":
            time.sleep(S["config"]["atraso_pagamento_ms"] / 1000)
        with L:
            if r["status"] not in ("reservada", "confirmada"):
                return (409, {"reserva_id": rid, "status": r["status"]})
            if sim == "recusar":
                r["status"] = "recusada"
                if "compensacao" not in BUG:
                    for a in r["assento_ids"]:
                        S["assentos"][r["evento_id"]][a] = "disponivel"
                return (402, {"reserva_id": rid, "status": "recusada"})
            r["status"] = "confirmada"
            for a in r["assento_ids"]:
                S["assentos"][r["evento_id"]][a] = "vendido"
            S["pagamentos"].append({"reserva_id": rid, "valor": r["valor"], "idempotency_key": chave})
            return (200, {"reserva_id": rid, "status": "confirmada", "valor_cobrado": r["valor"]})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--porta", type=int, default=8080)
    ap.add_argument("--bug", action="append", default=[])
    a = ap.parse_args()
    BUG.update(a.bug)
    threading.Thread(target=varredor, daemon=True).start()
    srv = ThreadingHTTPServer(("0.0.0.0", a.porta), H)
    srv.daemon_threads = True
    srv.request_queue_size = 1024
    print(f"servidor de referência na porta {a.porta}; bugs: {sorted(BUG) or 'nenhum'}", flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    main()
