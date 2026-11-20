// Teste da tradução de ids. Uso: k6 run -e MAPA_IDS=<caminho> testes/testar_ids.js
import { idEvento, idUsuario, idAssento, codigoAssento } from './ids.js';

export const options = { vus: 1, iterations: 1 };

function igual(obtido, esperado, descricao) {
  if (obtido !== esperado) throw new Error(`${descricao}: esperado ${esperado}, obtido ${obtido}`);
}

export default function () {
  igual(idEvento('E1'), 1, 'evento E1');
  igual(idEvento('E5'), 5, 'evento E5');
  igual(idUsuario('U000123'), 123, 'usuário');
  igual(idAssento('E1', 'ARENA-A-01-001'), 1, 'primeiro assento do E1');
  igual(idAssento('E1', 'ARENA-H-25-100'), 20000, 'último assento do E1');
  igual(idAssento('E2', 'TEATRO-PLATEIA-01-001'), 20001, 'primeiro assento do E2');
  igual(idAssento('E2', 'TEATRO-BALCAO-01-001'), 20601, 'primeiro do balcão no E2');
  igual(idAssento('E4', 'VIP-SALA-01-001'), 21401, 'primeiro assento do E4');
  igual(idAssento('E5', 'TEATRO-PLATEIA-01-001'), 21451, 'primeiro assento do E5 (mesmo teatro do E2)');
  igual(idAssento('E5', 'TEATRO-CAMAROTE-20-010'), 22650, 'último assento do E5');
  const amostra = [['E1', 'ARENA-C-12-057'], ['E2', 'TEATRO-CAMAROTE-07-003'], ['E3', 'CLUBE-PISTA-10-020'],
                   ['E4', 'VIP-SALA-05-010'], ['E5', 'TEATRO-BALCAO-20-020']];
  for (const [ev, cod] of amostra) igual(codigoAssento(ev, idAssento(ev, cod)), cod, `ida e volta ${ev} ${cod}`);
  console.log('traducao de ids: OK');
}
