#!/usr/bin/env python3
"""Converte dados/carga.json em SQL para o evento_assento_db e gera o mapa de ids (texto -> inteiro).

Uso: gerar_sql.py <carga.json> <pasta-de-saida>
Saídas:
  carga.sql      COPY de eventos e assentos, com ids explícitos
  mapa_ids.json  mapa compacto: por evento, o id e, por setor, o id do primeiro assento.
                 Os códigos do kit seguem LOCAL-SETOR-FF-NNN (fila e número em ordem), então o id de um
                 assento é base + (fila - 1) * assentos_por_fila + (numero - 1).
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

    mapa = {"eventos": {}}
    linhas_evento, linhas_assento = [], []
    proximo = 1
    for i, ev in enumerate(d["eventos"], start=1):
        local = ev["local_id"]
        data = ev["data"][:19].replace("T", " ")
        linhas_evento.append(f"{i}\t{ev['nome']}\t{ev['nome']}\t{data}\t{TIPO_EVENTO_SHOW}")
        setores_mapa = []
        for setor in locais[local]["setores"]:
            prefixo = f"{local}-{setor['setor']}"
            esperado = [f"{prefixo}-{f:02d}-{n:03d}"
                        for f in range(1, setor["filas"] + 1)
                        for n in range(1, setor["assentos_por_fila"] + 1)]
            if setor["assentos"] != esperado:
                sys.exit(f"padrão de códigos inesperado no setor {prefixo}: o mapa compacto não se aplica")
            setores_mapa.append({"setor": setor["setor"], "prefixo": prefixo, "filas": setor["filas"],
                                 "por_fila": setor["assentos_por_fila"], "base": proximo})
            valor = centavos_para_reais(setor["preco"])
            for _ in setor["assentos"]:
                linhas_assento.append(f"{proximo}\t{i}\t{setor['setor']}\t{valor}")
                proximo += 1
        mapa["eventos"][ev["evento_id"]] = {"id": i, "setores": setores_mapa}

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
