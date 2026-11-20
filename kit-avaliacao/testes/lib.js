// Cliente do contrato de teste, configurado pelo adaptador do grupo.
import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { idEvento, idUsuario, idAssento, codigoAssento } from './ids.js';

const caminhoAdaptador = __ENV.ADAPTADOR || '../adaptadores/referencia.json';
export const adaptador = JSON.parse(open(caminhoAdaptador));
const R = adaptador.rotas;

export const m = {
  tListar: new Trend('t_listar', true),
  tReservar: new Trend('t_reservar', true),
  tPagar: new Trend('t_pagar', true),
  tFluxo: new Trend('t_fluxo_compra', true),
  reservaOk: new Counter('reserva_ok'),
  reservaConflito: new Counter('reserva_conflito'),
  pagamentoAprovado: new Counter('pagamento_aprovado'),
  pagamentoRecusado: new Counter('pagamento_recusado'),
  pagamentoPendenteSemFim: new Counter('pagamento_pendente_sem_fim'),
  rejeicaoControlada: new Counter('rejeicao_controlada'),
  erro: new Counter('erro'),
  taxaErro: new Rate('taxa_erro'),
  vendaConfirmada: new Counter('venda_confirmada'),
  reservaCriada: new Counter('reserva_criada'),
};

function pegar(obj, caminho) {
  if (!caminho) return obj;
  return caminho.split('.').reduce((o, k) => (o == null ? undefined : o[k]), obj);
}

function preencher(modelo, vars, codificar) {
  if (typeof modelo === 'string') {
    const inteiro = modelo.match(/^\{(\w+)\}$/);
    if (inteiro && !codificar) return vars[inteiro[1]];
    return modelo.replace(/\{(\w+)\}/g, (_, k) => (codificar ? encodeURIComponent(vars[k]) : String(vars[k])));
  }
  if (Array.isArray(modelo)) return modelo.map((x) => preencher(x, vars, codificar));
  if (modelo && typeof modelo === 'object') {
    const out = {};
    for (const k of Object.keys(modelo)) out[k] = preencher(modelo[k], vars, codificar);
    return out;
  }
  return modelo;
}

function json(res) {
  try { return res.json(); } catch (e) { return null; }
}

// Classifica respostas que não são o caso esperado: 429/503 = rejeição controlada; o resto conta como erro.
export function registrarFalha(res, operacao) {
  if (res.status === 429 || res.status === 503) {
    m.rejeicaoControlada.add(1, { operacao });
    m.taxaErro.add(false);
    return 'rejeitada';
  }
  m.erro.add(1, { operacao, status: String(res.status) });
  m.taxaErro.add(true);
  return 'erro';
}

export function requisicao(rota, vars, chave, extraHeaders) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, adaptador.cabecalhos || {}, extraHeaders || {});
  if (chave) headers[adaptador.cabecalho_idempotencia || 'Idempotency-Key'] = chave;
  const url = adaptador.base_url + preencher(rota.caminho, vars, true);
  const corpo = rota.corpo ? JSON.stringify(preencher(rota.corpo, vars, false)) : null;
  return {
    method: rota.metodo,
    url,
    body: corpo,
    params: { headers, timeout: adaptador.timeout_requisicao || '30s', tags: { name: rota.nome || rota.caminho } },
  };
}

function enviar(req) {
  return http.request(req.method, req.url, req.body, req.params);
}

export function listarAssentos(eventoId, setor) {
  const res = enviar(requisicao(Object.assign({ nome: 'listar_assentos' }, R.listar_assentos), { evento_id: idEvento(eventoId), setor }));
  m.tListar.add(res.timings.duration);
  if (res.status !== 200) { registrarFalha(res, 'listar'); return null; }
  m.taxaErro.add(false);
  const lista = pegar(json(res), R.listar_assentos.campo_lista) || [];
  const disp = R.listar_assentos.valores_disponivel;
  return lista.map((a) => ({ id: codigoAssento(eventoId, pegar(a, R.listar_assentos.campo_id)),
    disponivel: disp.indexOf(pegar(a, R.listar_assentos.campo_status)) >= 0 }));
}

export function reqReservar(it) {
  return requisicao(Object.assign({ nome: 'reservar' }, R.reservar),
    { evento_id: idEvento(it.evento_id), usuario_id: idUsuario(it.usuario_id),
      assento_ids: it.assento_ids.map((a) => idAssento(it.evento_id, a)) }, `${it.id}-r`);
}

export function reqPagar(it, reservaId) {
  return requisicao(Object.assign({ nome: 'pagar' }, R.pagar),
    { reserva_id: reservaId, simulacao: it.simulacao }, `${it.id}-p`);
}

// Interpreta a resposta da reserva: {resultado: 'ok'|'conflito'|'rejeitada'|'erro', reservaId}
export function lerReserva(res) {
  m.tReservar.add(res.timings.duration);
  if (R.reservar.codigos_sucesso.indexOf(res.status) >= 0) {
    const id = pegar(json(res), R.reservar.campo_reserva_id);
    if (id === undefined || id === null) { registrarFalha(res, 'reservar'); return { resultado: 'erro' }; }
    m.taxaErro.add(false);
    return { resultado: 'ok', reservaId: String(id) };
  }
  if (R.reservar.codigos_conflito.indexOf(res.status) >= 0) { m.taxaErro.add(false); return { resultado: 'conflito' }; }
  return { resultado: registrarFalha(res, 'reservar') };
}

function statusCanonico(valor) {
  const mapa = R.consultar.valores_status;
  for (const k of Object.keys(mapa)) if (mapa[k].indexOf(valor) >= 0) return k;
  return null;
}

function aguardarFinal(reservaId) {
  const limite = Date.now() + 30000;
  while (Date.now() < limite) {
    sleep(0.25);
    const res = enviar(requisicao(Object.assign({ nome: 'consultar' }, R.consultar), { reserva_id: reservaId }));
    if (res.status === 200) {
      const st = statusCanonico(pegar(json(res), R.consultar.campo_status));
      if (st) return st;
    }
  }
  return null;
}

// Interpreta a resposta do pagamento: 'aprovado' | 'recusado' | 'rejeitada' | 'erro'
export function lerPagamento(res, reservaId, aguardar) {
  m.tPagar.add(res.timings.duration);
  if (R.pagar.codigos_sucesso.indexOf(res.status) >= 0) { m.taxaErro.add(false); return 'aprovado'; }
  if (R.pagar.codigos_recusa.indexOf(res.status) >= 0) { m.taxaErro.add(false); return 'recusado'; }
  if ((R.pagar.codigos_pendente || []).indexOf(res.status) >= 0) {
    m.taxaErro.add(false);
    if (!aguardar) return 'pendente';
    const st = aguardarFinal(reservaId);
    if (st === 'confirmada') return 'aprovado';
    if (st === 'recusada' || st === 'expirada') return 'recusado';
    m.pagamentoPendenteSemFim.add(1);
    return 'erro';
  }
  return registrarFalha(res, 'pagar');
}

export function enviarLote(reqs) {
  return http.batch(reqs.map((r) => [r.method, r.url, r.body, r.params]));
}

export { enviar };

// Fluxo completo de uma intenção de compra.
export function fluxoCompra(it, cenario) {
  const inicio = Date.now();
  if (it.consultar_antes) listarAssentos(it.evento_id, it.setor);
  const r = lerReserva(enviar(reqReservar(it)));
  if (r.resultado === 'conflito') { m.reservaConflito.add(1, { evento_id: it.evento_id }); return; }
  if (r.resultado !== 'ok') return;
  m.reservaOk.add(1, { evento_id: it.evento_id });
  m.reservaCriada.add(1, { evento_id: it.evento_id });
  console.log(`RESERVA ${r.reservaId} ${it.id} ${it.evento_id}`);
  if (it.simulacao === 'nao_pagar') return;
  const p = lerPagamento(enviar(reqPagar(it, r.reservaId)), r.reservaId, true);
  if (p === 'aprovado') {
    m.pagamentoAprovado.add(1, { evento_id: it.evento_id });
    m.vendaConfirmada.add(1, { evento_id: it.evento_id });
    console.log(`VENDA ${r.reservaId} ${it.id} ${it.evento_id}`);
    m.tFluxo.add(Date.now() - inicio);
  } else if (p === 'recusado') {
    m.pagamentoRecusado.add(1, { evento_id: it.evento_id });
  }
}
