// Cliente da fila contínua (Etapa 4) sobre o transporte de http.ts. Os esquemas são estritos: chave
// a mais, situação fora da lista ou campo fora da regra da situação vira ErroHttp RESPOSTA_INVALIDA,
// sem o corpo junto. Os leitores servem também aos eventos fila.estado e fila.pareado do SSE.

import * as v from "valibot";
import { ErroHttp, requisitarHttp } from "./http";
import { lerResposta } from "./lerResposta";
import type { Coordenada } from "./perfil";

export const SITUACOES = ["AGUARDANDO_COMPATIVEL", "AGUARDANDO_VAGA", "PAREADO"] as const;

export type SituacaoNaFila = (typeof SITUACOES)[number];

const Inteiro = v.pipe(v.number(), v.integer());

const EsquemaEstadoNaFila = v.pipe(
  v.strictObject({
    situacao: v.picklist(SITUACOES),
    desde: v.string(),
    posicao: v.nullable(v.pipe(Inteiro, v.minValue(1))),
    estimativaMinutos: v.nullable(v.pipe(Inteiro, v.minValue(0))),
    encontroId: v.nullable(v.string()),
    cenario: v.nullable(v.string()),
  }),
  // posicao e estimativaMinutos só em AGUARDANDO_VAGA (a estimativa pode faltar nela); encontroId e
  // cenario só, e sempre, em PAREADO.
  v.check(
    (e) =>
      (e.situacao === "AGUARDANDO_VAGA" ? e.posicao !== null : e.posicao === null && e.estimativaMinutos === null) &&
      (e.situacao === "PAREADO"
        ? e.encontroId !== null && e.cenario !== null
        : e.encontroId === null && e.cenario === null),
  ),
);

const EsquemaPareado = v.strictObject({ encontroId: v.string(), cenario: v.string() });

const EsquemaMovimento = v.strictObject({
  esperaTipicaMinutos: v.nullable(v.pipe(Inteiro, v.minValue(0))),
  horasDePico: v.array(v.pipe(Inteiro, v.minValue(0), v.maxValue(23))),
});

export type EstadoNaFila = v.InferOutput<typeof EsquemaEstadoNaFila>;
export type Pareado = v.InferOutput<typeof EsquemaPareado>;
export type Movimento = v.InferOutput<typeof EsquemaMovimento>;

/** Lê um EstadoNaFila da resposta HTTP ou do evento fila.estado; `status` só vai no erro. */
export function lerEstadoNaFila(dados: unknown, status = 200): EstadoNaFila {
  return lerResposta(EsquemaEstadoNaFila, status, dados);
}

/** Lê os dados do evento fila.pareado. */
export function lerPareado(dados: unknown, status = 200): Pareado {
  return lerResposta(EsquemaPareado, status, dados);
}

/** Sem posição, o corpo vai vazio; com ela, só latitude e longitude. */
export async function entrarNaFila(posicao: Coordenada | null): Promise<EstadoNaFila> {
  const corpo = posicao === null ? {} : { latitude: posicao.latitude, longitude: posicao.longitude };
  const resposta = await requisitarHttp("POST", "/fila", { corpo });
  return lerEstadoNaFila(resposta.dados, resposta.status);
}

/** null quando a conta não está na fila (404 FORA_DA_FILA); outro erro rejeita. */
export async function obterFila(): Promise<EstadoNaFila | null> {
  try {
    const resposta = await requisitarHttp("GET", "/fila");
    return lerEstadoNaFila(resposta.dados, resposta.status);
  } catch (erro) {
    if (erro instanceof ErroHttp && erro.status === 404 && erro.codigo === "FORA_DA_FILA") {
      return null;
    }
    throw erro;
  }
}

/** Idempotente: sair de novo, ou estando fora, também resolve. */
export async function sairDaFila(): Promise<void> {
  await requisitarHttp("DELETE", "/fila");
}

export async function obterMovimento(): Promise<Movimento> {
  const resposta = await requisitarHttp("GET", "/fila/movimento");
  return lerResposta(EsquemaMovimento, resposta.status, resposta.dados);
}
