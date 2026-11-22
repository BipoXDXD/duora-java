// Para onde vai o foco quando o elemento focado some num redesenho (botão de entrar, convite,
// "Pronto", resultado): o destino lógico da tela atual, em vez de deixá-lo cair no body.

import type { Fase } from "../dominio/visao";

/** Onde procurar os destinos pelos ids da página: o document ou o elemento raiz do app. */
export type Raiz = Document | Element;

export function visivel(el: Element): boolean {
  return el.isConnected && el.getClientRects().length > 0;
}

/** Candidatos de cada fase, na ordem de preferência; o primeiro visível vence. */
function candidatos(fase: Fase, raiz: Raiz): (HTMLElement | null)[] {
  switch (fase) {
    case "AGUARDANDO_PAR":
      return [raiz.querySelector<HTMLElement>("#link-convite")];
    case "REVELADO":
      return [raiz.querySelector<HTMLElement>("#resultado-titulo"), raiz.querySelector<HTMLElement>("#som")];
    case "MONTANDO":
      // A lanterna só existe durante a abertura, quando o inventário está escondido.
      return [
        raiz.querySelector<HTMLElement>("button[data-abertura]"),
        raiz.querySelector<HTMLElement>("#inventario button"),
        raiz.querySelector<HTMLElement>("#pronto"),
      ];
  }
}

export function destinoDoFoco(fase: Fase, raiz: Raiz): HTMLElement | undefined {
  return candidatos(fase, raiz).find((el): el is HTMLElement => el !== null && visivel(el));
}

/**
 * Quando o elemento focado antes do redesenho (ativoAntes) sumiu, ou quando o foco estava no body
 * e a fase mudou, leva o foco ao destino da fase atual. Não mexe em foco que continua visível em
 * outro controle.
 */
export function levarFocoSeSumiu(ativoAntes: Element | null, trocouDeFase: boolean, fase: Fase, raiz: Raiz): void {
  const documento = raiz instanceof Document ? raiz : raiz.ownerDocument;
  const body = documento.body;
  const noBody = ativoAntes === null || ativoAntes === body;
  const sumiu = !noBody && !visivel(ativoAntes);
  if (!sumiu && !(noBody && trocouDeFase)) {
    return;
  }
  const atual = documento.activeElement;
  if (atual && atual !== body && atual !== ativoAntes && visivel(atual)) {
    return; // o foco já foi devolvido ao equivalente
  }
  destinoDoFoco(fase, raiz)?.focus();
}
