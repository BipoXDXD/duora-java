// O pedido de ajuste da noite revelada, do ponto de vista de quem olha.

import type { Visao } from "./visao";

export type SituacaoDoAjuste = "nenhum" | "meu" | "do-outro";

/** Sem pedido; o pedido é seu (espera o aceite); ou é do outro (espera a sua resposta). */
export function situacaoDoAjuste(visao: Visao): SituacaoDoAjuste {
  if (visao.ajuste === null) {
    return "nenhum";
  }
  return visao.ajuste.deAssento === visao.voce.assento ? "meu" : "do-outro";
}

/** O pedido pendente em uma frase, para o resultado e para o aviso; vazio sem pedido. */
export function textoDoPedido(visao: Visao): string {
  // Só há pedido na noite revelada, com os dois no terraço.
  const parceiro = String(visao.parceiro?.nome);
  switch (situacaoDoAjuste(visao)) {
    case "nenhum":
      return "";
    case "meu":
      return `Esperando ${parceiro} aceitar.`;
    case "do-outro":
      return `${parceiro} quer ajustar a composição.`;
  }
}
