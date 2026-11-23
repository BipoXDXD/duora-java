import { describe, expect, it } from "vitest";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import type { Efeito } from "../cenario/tipos";
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

  it.each<[Efeito, "piso" | "frente"]>([
    [{ tipo: "brilho-piso", largura: 2.4 }, "piso"],
    [{ tipo: "halo", alturaProjetada: 0.35, largura: 0.75 }, "frente"],
    [{ tipo: "brilho", cor: "#f2bd70", largura: 1.6 }, "piso"],
    [{ tipo: "vapor" }, "frente"],
    [{ tipo: "tremular" }, "frente"],
  ])("cadaTipoCriaSeuElemento: %o vai para a camada %s", (efeito, camada) => {
    const elementos = criarElementosDoEfeito(efeito, "peca", PONTO, geometria);

    expect(resumo(elementos)).toEqual([{ camada, classe: efeito.tipo, efeito: efeito.tipo, peca: "peca" }]);
    // Decorativo: fora da árvore de acessibilidade.
    expect(elementos.map(({ el }) => el.getAttribute("aria-hidden"))).toEqual(["true"]);
  });

  it.each<Efeito>([{ tipo: "meteoros" }, { tipo: "notas", som: "radio-notas", volume: 0.4 }])(
    "meteorosENotasNaoCriamElemento: %o",
    (efeito) => {
      expect(criarElementosDoEfeito(efeito, "peca", PONTO, geometria)).toEqual([]);
    },
  );

  it.each<Efeito>([{ tipo: "halo", alturaProjetada: 0.35, largura: 0.75 }, { tipo: "vapor" }, { tipo: "tremular" }])(
    "efeitoErguidoFicaAcimaDoPontoNaMesmaVertical: %o",
    (efeito) => {
      const [noPiso] = criarElementosDoEfeito({ tipo: "brilho-piso", largura: 1 }, "peca", PONTO, geometria);
      const [erguido] = criarElementosDoEfeito(efeito, "peca", PONTO, geometria);

      expect(erguido?.el.style.left).toBe(noPiso?.el.style.left);
      expect(Number.parseFloat(erguido?.el.style.top ?? "")).toBeLessThan(
        Number.parseFloat(noPiso?.el.style.top ?? ""),
      );
    },
  );

  it("brilhoUsaACorPorCssom", () => {
    const [elemento] = criarElementosDoEfeito(
      { tipo: "brilho", cor: "#7a2a3c", largura: 1.2 },
      "manta",
      PONTO,
      geometria,
    );

    expect(elemento?.el.style.getPropertyValue("--cor-efeito")).toBe("#7a2a3c");
  });
});
