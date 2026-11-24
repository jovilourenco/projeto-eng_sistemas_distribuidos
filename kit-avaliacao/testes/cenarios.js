// Cenários do kit de avaliação. Escolha com -e CENARIO=<nome> e -e PERFIL=leve|padrao|pesado.
//   carga        rampa de chegadas com as 30.000 intenções de compra (E1, E2, E3)
//   pico         abertura de vendas: salto brusco de chegadas, mesmas intenções
//   corrida      5.000 pessoas disputando os 50 assentos do E4 ao mesmo tempo
//   idempotencia 400 compras no E5, cada reserva e cada pagamento enviados 5 vezes em paralelo
//   expiracao    400 reservas no E5 que nunca são pagas; confere a liberação após o TTL
//   compensacao  400 reservas no E5 com pagamento recusado; confere a liberação dos assentos
import exec from 'k6/execution';
import { sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';
import {
  adaptador, m, fluxoCompra, listarAssentos, reqReservar, reqPagar, lerReserva, lerPagamento, enviarLote, enviar,
} from './lib.js';

const CENARIO = __ENV.CENARIO || 'carga';
const PERFIL = __ENV.PERFIL || 'padrao';
// FATOR multiplica taxas e VUs; -e FATOR=10 sobrepõe o perfil (escala opcional, ver README).
const FATOR = Number(__ENV.FATOR) || { leve: 0.2, padrao: 1, pesado: 2 }[PERFIL] || 1;
const DADOS = __ENV.DADOS || '../dados';
const carga = JSON.parse(open(`${DADOS}/carga.json`)).config;

const arquivo = { carga: 'carga', pico: 'carga', corrida: 'corrida', idempotencia: 'idempotencia',
  expiracao: 'expiracao', compensacao: 'compensacao' }[CENARIO];
if (!arquivo) throw new Error(`Cenário desconhecido: ${CENARIO}`);
const intencoes = new SharedArray(`roteiro_${arquivo}`, () => JSON.parse(open(`${DADOS}/roteiro_${arquivo}.json`)));

const idemDivergente = new Counter('idempotencia_reserva_divergente');
const idemPagamentoDivergente = new Counter('idempotencia_pagamento_divergente');
const idemRecusada = new Counter('idempotencia_repeticao_recusada');
const naoLiberados = new Counter('assentos_nao_liberados');
const esgotadas = new Counter('intencoes_esgotadas');

const r = (x) => Math.max(1, Math.round(x * FATOR));

const PERFIS = {
  carga: {
    executor: 'ramping-arrival-rate', startRate: r(5), timeUnit: '1s',
    preAllocatedVUs: r(100), maxVUs: r(800),
    stages: [
      { target: r(50), duration: '30s' }, { target: r(50), duration: '2m' },
      { target: r(120), duration: '1m' }, { target: r(120), duration: '3m' },
    ],
  },
  pico: {
    executor: 'ramping-arrival-rate', startRate: r(10), timeUnit: '1s',
    preAllocatedVUs: r(200), maxVUs: r(1500),
    stages: [
      { target: r(10), duration: '30s' }, { target: r(300), duration: '5s' },
      { target: r(300), duration: '1m' }, { target: r(20), duration: '10s' }, { target: r(20), duration: '30s' },
    ],
  },
  corrida: { executor: 'shared-iterations', vus: r(500), iterations: 5000, maxDuration: '3m' },
  idempotencia: { executor: 'shared-iterations', vus: r(50), iterations: 400, maxDuration: '3m' },
  expiracao: { executor: 'shared-iterations', vus: r(50), iterations: 400, maxDuration: '3m' },
  compensacao: { executor: 'shared-iterations', vus: r(50), iterations: 400, maxDuration: '3m' },
};

export const options = {
  scenarios: { [CENARIO]: Object.assign({ exec: 'executar' }, PERFIS[CENARIO]) },
  teardownTimeout: '3m',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
  tags: { grupo: adaptador.grupo, cenario: CENARIO, perfil: PERFIL },
};

function intencaoAtual() {
  const i = exec.scenario.iterationInTest;
  if (i >= intencoes.length) { esgotadas.add(1); return null; }
  return intencoes[i];
}

function idempotencia(it) {
  // 5 envios em paralelo e mais 1 repetição depois: todos devem devolver a mesma reserva.
  const reservas = enviarLote([0, 1, 2, 3, 4].map(() => reqReservar(it))).map(lerReserva);
  reservas.push(lerReserva(enviar(reqReservar(it))));
  const ids = [...new Set(reservas.filter((x) => x.resultado === 'ok').map((x) => x.reservaId))];
  if (ids.length > 1) idemDivergente.add(1);
  if (ids.length === 1 && reservas.some((x) => x.resultado === 'conflito')) idemRecusada.add(1);
  if (ids.length === 0) return;
  m.reservaOk.add(1, { evento_id: it.evento_id });
  m.reservaCriada.add(1, { evento_id: it.evento_id });
  console.log(`RESERVA ${ids[0]} ${it.id} ${it.evento_id}`);
  const pags = enviarLote([0, 1, 2, 3, 4].map(() => reqPagar(it, ids[0]))).map((res) => lerPagamento(res, ids[0], true));
  const aprovados = pags.filter((p) => p === 'aprovado').length;
  if (aprovados > 0 && aprovados < pags.filter((p) => p !== 'rejeitada' && p !== 'erro').length) {
    idemPagamentoDivergente.add(1);
  }
  if (aprovados > 0) {
    m.pagamentoAprovado.add(1, { evento_id: it.evento_id });
    m.vendaConfirmada.add(1, { evento_id: it.evento_id });
    console.log(`VENDA ${ids[0]} ${it.id} ${it.evento_id}`);
  }
}

export function executar() {
  const it = intencaoAtual();
  if (!it) return;
  if (CENARIO === 'idempotencia') return idempotencia(it);
  return fluxoCompra(it, CENARIO);
}

// Confere, no fim, se os assentos dos cenários de expiração e compensação voltaram a ficar disponíveis.
export function teardown() {
  if (CENARIO !== 'expiracao' && CENARIO !== 'compensacao') return;
  const espera = CENARIO === 'expiracao' ? carga.ttl_reserva_segundos + 15 : 10;
  sleep(espera);
  const alvo = new Set();
  const setores = new Set();
  for (let i = 0; i < intencoes.length; i++) {
    intencoes[i].assento_ids.forEach((a) => alvo.add(a));
    setores.add(intencoes[i].setor);
  }
  let presos = 0;
  for (const s of setores) {
    const lista = listarAssentos('E5', s) || [];
    for (const a of lista) if (alvo.has(a.id) && !a.disponivel) presos++;
  }
  naoLiberados.add(presos);
}

export function handleSummary(data) {
  const destino = __ENV.SAIDA || `resultados/${adaptador.grupo}`;
  return {
    [`${destino}/${CENARIO}.resumo.json`]: JSON.stringify(data, null, 1),
    stdout: `\n[${adaptador.grupo}] cenário ${CENARIO} (${PERFIL}) concluído. Resumo em ${destino}/${CENARIO}.resumo.json\n`,
  };
}
