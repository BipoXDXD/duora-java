// O que o parceiro acabou de fazer: as peças dele que mudaram de lugar entre duas visões ficam em
// destaque na cena por um tempo curto.

import type { Visao } from "../dominio/visao";

/** Quanto tempo a peça que o parceiro mudou fica em destaque. */
export const DURACAO_DESTAQUE_MS = 2400;

/** Agenda a ação para daqui a ms; devolve a função que a cancela. */
export type Agendar = (acao: () => void, ms: number) => () => void;

export const agendarComSetTimeout: Agendar = (acao, ms) => {
  const id = setTimeout(acao, ms);
  return () => {
    clearTimeout(id);
  };
};

/** As peças do parceiro colocadas ou movidas de uma visão para a outra; as tiradas não contam. */
export function pecasQueMudaram(anterior: Visao, visao: Visao): string[] {
  const lugarAntes = new Map(anterior.pecas.map((p) => [p.peca, p.lugar]));
  return visao.pecas
    .filter((p) => p.assento !== visao.voce.assento && p.lugar !== null && p.lugar !== lugarAntes.get(p.peca))
    .map((p) => p.peca);
}
