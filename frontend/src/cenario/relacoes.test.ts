import { describe, expect, it } from "vitest";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { frasesDeRelacao } from "./relacoes";

const LANTERNA_E_TELESCOPIO = "A lanterna ilumina o telescópio de perto.";

describe("frasesDeRelacao", () => {
  const c = cenarioDoTerraco();

  it("telescópio em C1 e lanterna em L2 são perto (1,24 m)", () => {
    const frases = c.frasesDeRelacao([
      { peca: "telescopio", lugar: "C1" },
      { peca: "lanterna", lugar: "L2" },
    ]);

    expect(frases).toContain(LANTERNA_E_TELESCOPIO);
  });

  it("lanterna do outro lado não é perto (C1 e R2, 3,23 m)", () => {
    const frases = c.frasesDeRelacao([
      { peca: "telescopio", lugar: "C1" },
      { peca: "lanterna", lugar: "R2" },
    ]);

    expect(frases).not.toContain(LANTERNA_E_TELESCOPIO);
  });

  it("no máximo duas frases, na ordem do JSON", () => {
    const frases = c.frasesDeRelacao([
      { peca: "telescopio", lugar: "C1" },
      { peca: "banco", lugar: "C2" },
      { peca: "manta", lugar: "L1" },
      { peca: "lanterna", lugar: "L2" },
    ]);

    expect(frases).toEqual([LANTERNA_E_TELESCOPIO, "A manta ficou junto do telescópio, para as horas de espera."]);
  });

  it("o par casa sem ordem", () => {
    const relacoes = [{ pecas: ["telescopio", "lanterna"] as const, perto: "Perto." }];
    const colocadas = [
      { peca: "lanterna", lugar: "L2" },
      { peca: "telescopio", lugar: "C1" },
    ];

    expect(frasesDeRelacao(relacoes, 2, c.geometria, colocadas)).toEqual(["Perto."]);
  });
});
