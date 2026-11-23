// Os elementos de cada efeito noturno da revelação, sem tocar no DOM da cena: a cena.ts põe a
// camada "piso" antes do botão da peça (a luz no chão fica atrás dela) e a "frente" depois (a luz
// sobre ela). Aparecem só à noite, por CSS (revelacao.css), e são decorativos: aria-hidden.

import type { Efeito } from "../cenario/tipos";
import { type Geometria, type Ponto, pct } from "../dominio/dados";

export type CamadaDoEfeito = "piso" | "frente";

export interface ElementoDeEfeito {
  readonly camada: CamadaDoEfeito;
  readonly el: HTMLElement;
}

/**
 * Medidas, em metros, dos efeitos que o cenario.json não mede. O vapor nasce na tampa da garrafa
 * (0,8 m acima do ponto, já projetado) e sobe; o tremular envolve a chama da lanterna, na altura
 * do halo (0,4 m × cos 30°), mais largo que ele.
 */
const VAPOR = { altura: 0.8, largura: 0.45 } as const;
const TREMULAR = { altura: 0.3464, largura: 1.5 } as const;

/** Div com a classe do tipo, centrada no ponto do palco, com data-efeito e data-peca para os testes. */
function criarDiv(tipo: Efeito["tipo"], peca: string, centro: Ponto, largura: number, g: Geometria): HTMLDivElement {
  const div = document.createElement("div");
  div.className = tipo;
  div.dataset.efeito = tipo;
  div.dataset.peca = peca;
  div.setAttribute("aria-hidden", "true");
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
  /** O ponto da peça erguido `metros` na projeção da câmera. */
  const acima = (metros: number): Ponto => ({ x: ponto.x, y: ponto.y - g.pxPorMetro * metros });
  switch (efeito.tipo) {
    case "brilho-piso": {
      // Poço de luz no piso, no próprio ponto da peça.
      return [{ camada: "piso", el: criarDiv(efeito.tipo, peca, ponto, efeito.largura, g) }];
    }
    case "halo": {
      // Luz sobre a peça (o modelo vem apagado), na altura já projetada pela câmera inclinada.
      return [{ camada: "frente", el: criarDiv(efeito.tipo, peca, acima(efeito.alturaProjetada), efeito.largura, g) }];
    }
    case "brilho": {
      // Poço de luz na cor da peça; estilo dinâmico só por CSSOM (CSP).
      const el = criarDiv(efeito.tipo, peca, ponto, efeito.largura, g);
      el.style.setProperty("--cor-efeito", efeito.cor);
      return [{ camada: "piso", el }];
    }
    case "vapor": {
      // Fios de vapor subindo da tampa; o CSS ancora o elemento pela base.
      return [{ camada: "frente", el: criarDiv(efeito.tipo, peca, acima(VAPOR.altura), VAPOR.largura, g) }];
    }
    case "tremular": {
      // Luz que treme em volta da chama, sobre o halo.
      return [{ camada: "frente", el: criarDiv(efeito.tipo, peca, acima(TREMULAR.altura), TREMULAR.largura, g) }];
    }
    case "meteoros":
    case "notas": {
      // Sem elemento por peça: os meteoros e as notas do rádio são ligados fora da fábrica.
      return [];
    }
  }
}
