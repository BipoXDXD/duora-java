// As etapas da localização, sem React. A coordenada nunca entra aqui: quem pede a posição a manda
// ao servidor na mesma função e só avisa a máquina do que aconteceu.

import type { FalhaDePosicao } from "../../geo/obterPosicao";

export type FalhaDaLocalizacao = FalhaDePosicao | "servidor";

export type EtapaDaLocalizacao =
  | { tipo: "inativa"; falha?: FalhaDaLocalizacao }
  | { tipo: "pedindo" }
  | { tipo: "gravando" }
  | { tipo: "ativa" }
  | { tipo: "desativando" };

export type EventoDaLocalizacao =
  | { tipo: "pedir" }
  | { tipo: "posicaoObtida" }
  | { tipo: "posicaoFalhou"; falha: FalhaDePosicao }
  | { tipo: "gravada" }
  | { tipo: "gravacaoFalhou" }
  | { tipo: "desativar" }
  | { tipo: "desativada" }
  | { tipo: "desativacaoFalhou" };

/**
 * A próxima etapa. Cada evento só vale na etapa em que ele pode acontecer; fora dela, nada muda
 * (um segundo clique durante o pedido, uma resposta atrasada).
 */
export function transicao(e: EtapaDaLocalizacao, ev: EventoDaLocalizacao): EtapaDaLocalizacao {
  switch (ev.tipo) {
    case "pedir":
      return e.tipo === "inativa" ? { tipo: "pedindo" } : e;
    case "posicaoObtida":
      return e.tipo === "pedindo" ? { tipo: "gravando" } : e;
    case "posicaoFalhou":
      return e.tipo === "pedindo" ? { tipo: "inativa", falha: ev.falha } : e;
    case "gravada":
      return e.tipo === "gravando" ? { tipo: "ativa" } : e;
    case "gravacaoFalhou":
      return e.tipo === "gravando" ? { tipo: "inativa", falha: "servidor" } : e;
    case "desativar":
      return e.tipo === "ativa" ? { tipo: "desativando" } : e;
    case "desativada":
      return e.tipo === "desativando" ? { tipo: "inativa" } : e;
    case "desativacaoFalhou":
      return e.tipo === "desativando" ? { tipo: "ativa" } : e;
  }
}

/** Só com a localização gravada dá para escolher um raio. */
export function raioDisponivel(e: EtapaDaLocalizacao): boolean {
  return e.tipo === "ativa";
}
