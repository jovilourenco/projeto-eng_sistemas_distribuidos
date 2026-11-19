#!/usr/bin/env python3
"""Converte dados/carga.json em SQL para o evento_assento_db e gera o mapa de ids (texto -> inteiro).

Uso: gerar_sql.py <carga.json> <pasta-de-saida>
Saídas: carga.sql (COPY de eventos e assentos, com ids explícitos) e mapa_ids.json.
"""
import json
import pathlib
import sys

TIPO_EVENTO_SHOW = 4  # todos os eventos do kit usam o tipo SHOW (id já existente em tipo_evento)


def centavos_para_reais(c):
    return f"{c // 100}.{c % 100:02d}"


def main(caminho_carga, pasta_saida):
    d = json.load(open(caminho_carga, encoding="utf-8"))
    saida = pathlib.Path(pasta_saida)
    saida.mkdir(parents=True, exist_ok=True)
    locais = {l["local_id"]: l for l in d["locais"]}

    mapa = {"eventos": {}, "assentos": {}}
    linhas_evento, linhas_assento = [], []
    proximo_assento = 1
    for i, ev in enumerate(d["eventos"], start=1):
        mapa["eventos"][ev["evento_id"]] = i
        data = ev["data"][:19].replace("T", " ")
        linhas_evento.append(f"{i}\t{ev['nome']}\t{ev['nome']}\t{data}\t{TIPO_EVENTO_SHOW}")
        ids = {}
        for setor in locais[ev["local_id"]]["setores"]:
            valor = centavos_para_reais(setor["preco"])
            for codigo in setor["assentos"]:
                ids[codigo] = proximo_assento
                linhas_assento.append(f"{proximo_assento}\t{i}\t{setor['setor']}\t{valor}")
                proximo_assento += 1
        mapa["assentos"][ev["evento_id"]] = ids

    with open(saida / "carga.sql", "w", encoding="utf-8") as f:
        f.write("BEGIN;\n")
        f.write("COPY evento_assento.evento (id_evento, titulo, descricao, data_evento, id_tipo_evento) FROM STDIN;\n")
        f.write("\n".join(linhas_evento) + "\n\\.\n")
        f.write("COPY evento_assento.assento (id_assento, id_evento, tipo_assento, valor) FROM STDIN;\n")
        f.write("\n".join(linhas_assento) + "\n\\.\n")
        f.write("SELECT setval(pg_get_serial_sequence('evento_assento.evento', 'id_evento'), (SELECT max(id_evento) FROM evento_assento.evento));\n")
        f.write("SELECT setval(pg_get_serial_sequence('evento_assento.assento', 'id_assento'), (SELECT max(id_assento) FROM evento_assento.assento));\n")
        f.write("COMMIT;\n")
    json.dump(mapa, open(saida / "mapa_ids.json", "w", encoding="utf-8"), ensure_ascii=False)
    print(f"{len(linhas_evento)} eventos e {len(linhas_assento)} assentos gerados em {saida}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
