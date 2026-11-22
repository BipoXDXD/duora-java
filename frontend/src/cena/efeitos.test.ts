import { describe, expect, it } from "vitest";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { criarElementosDoEfeito, type ElementoDeEfeito } from "./efeitos";

const { geometria } = cenarioDoTerraco();
const PONTO = { x: 400, y: 300 };

/** Camada, classe e os dois data-* de cada elemento, na ordem da fábrica. */
const resumo = (elementos: readonly ElementoDeEfeito[]) =>
  elementos.map(({ camada, el }) => ({
    camada,
    classe: el.className,
    efeito: el.dataset.efeito,
    peca: el.dataset.peca,
  }));

describe("criarElementosDoEfeito", () => {
  it("brilhoPisoVaiParaOPisoComDataEfeito", () => {
    const elementos = criarElementosDoEfeito({ tipo: "brilho-piso", largura: 1.2 }, "lanterna", PONTO, geometria);

    expect(resumo(elementos)).toEqual([
      { camada: "piso", classe: "brilho-piso", efeito: "brilho-piso", peca: "lanterna" },
    ]);
  });

  it("haloVaiParaAFrente", () => {
    const elementos = criarElementosDoEfeito(
      { tipo: "halo", alturaProjetada: 1, largura: 0.75 },
      "lanterna",
      PONTO,
      geometria,
    );

    expect(resumo(elementos)).toEqual([{ camada: "frente", classe: "halo", efeito: "halo", peca: "lanterna" }]);
  });
});
