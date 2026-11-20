// Tradução entre os códigos do kit (E1, U000123, ARENA-A-01-001) e os ids inteiros do TicketFlow.
// Usa o mapa compacto gerado por carga/gerar_sql.py.
const MAPA = JSON.parse(open(__ENV.MAPA_IDS || '../carga/saida/mapa_ids.json'));

const porPrefixo = {};
for (const cod of Object.keys(MAPA.eventos)) {
  porPrefixo[cod] = {};
  for (const s of MAPA.eventos[cod].setores) porPrefixo[cod][s.prefixo] = s;
}

function evento(cod) {
  const e = MAPA.eventos[cod];
  if (!e) throw new Error(`evento desconhecido: ${cod}`);
  return e;
}

export function idEvento(cod) {
  return evento(cod).id;
}

export function idUsuario(cod) {
  return parseInt(String(cod).replace(/^\D+/, ''), 10);
}

export function idAssento(codEvento, cod) {
  const m = /^(.+)-(\d{2})-(\d{3})$/.exec(cod);
  const s = m && (porPrefixo[codEvento] || {})[m[1]];
  if (!s) throw new Error(`assento desconhecido: ${cod} (evento ${codEvento})`);
  return s.base + (parseInt(m[2], 10) - 1) * s.por_fila + (parseInt(m[3], 10) - 1);
}

export function codigoAssento(codEvento, id) {
  for (const s of evento(codEvento).setores) {
    if (id >= s.base && id < s.base + s.filas * s.por_fila) {
      const desloc = id - s.base;
      const fila = Math.floor(desloc / s.por_fila) + 1;
      const num = (desloc % s.por_fila) + 1;
      return `${s.prefixo}-${String(fila).padStart(2, '0')}-${String(num).padStart(3, '0')}`;
    }
  }
  return null;
}
