// Os elementos de cada efeito noturno da revelação, sem tocar no DOM da cena: a cena.ts põe a
// camada "piso" antes do botão da peça (a luz no chão fica atrás dela) e a "frente" depois (a luz
// sobre ela). Aparecem só à noite, por CSS.

import type { Efeito } from "../cenario/tipos";
import { type Geometria, type Ponto, pct } from "../dominio/dados";

export type CamadaDoEfeito = "piso" | "frente";

export interface ElementoDeEfeito {
  readonly camada: CamadaDoEfeito;
  readonly el: HTMLElement;
}

/** Div com a classe do tipo, centrada no ponto do palco, com data-efeito e data-peca para os testes. */
function criarDiv(tipo: Efeito["tipo"], peca: string, centro: Ponto, largura: number, g: Geometria): HTMLDivElement {
  const div = document.createElement("div");
  div.className = tipo;
  div.dataset.efeito = tipo;
  div.dataset.peca = peca;
  div.style.left = pct(centro.x, g.largura);
  div.style.top = pct(centro.y, g.altura);
  div.style.width = pct(g.pxPorMetro * largura, g.largura);
  return div;
}

/** Fábrica pura: os elementos do efeito, centrados no ponto da peça, cada um com a camada dele. */
export function criarElementosDoEfeito(
  efeito: Efeito,
  peca: string,
  ponto: Ponto,
  g: Geometria,
): readonly ElementoDeEfeito[] {
  switch (efeito.tipo) {
    case "brilho-piso": {
      // Poço de luz no piso, no próprio ponto da peça.
      return [{ camada: "piso", el: criarDiv(efeito.tipo, peca, ponto, efeito.largura, g) }];
    }
    case "halo": {
      // Luz sobre a peça (o modelo vem apagado), na altura já projetada pela câmera inclinada.
      const centro = { x: ponto.x, y: ponto.y - g.pxPorMetro * efeito.alturaProjetada };
      return [{ camada: "frente", el: criarDiv(efeito.tipo, peca, centro, efeito.largura, g) }];
    }
  }
}
