import { afterEach, describe, expect, it } from "vitest";
import type { CenarioFront } from "../cenario/tipos";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { CENARIO_MINIMO } from "../teste/cenarioMinimo";
import { criarCena } from "./cena";

function visao(pecas: VisaoPeca[]): Visao {
  return {
    fase: "MONTANDO",
    versao: 1,
    voce: { assento: "B", nome: "Ana" },
    parceiro: { assento: "A", nome: "Bruno", conectado: true },
    convite: null,
    pecas,
    prontos: { voce: false, parceiro: false },
    confirmacao: { podeConfirmar: false, faltam: 2 },
    sinal: null,
    resultado: null,
  };
}

/** Monta o #palco com as duas camadas e desenha as peças com o cenário dado. */
function desenhar(cenario: CenarioFront, pecas: VisaoPeca[]): HTMLElement {
  const palco = document.createElement("div");
  palco.id = "palco";
  const camadaPecas = document.createElement("div");
  const camadaLugares = document.createElement("div");
  palco.append(camadaPecas, camadaLugares);
  document.body.append(palco);
  criarCena({
    palco,
    camadaPecas,
    camadaLugares,
    cenario,
    aoEscolherPeca: () => {},
    aoEscolherLugar: () => {},
  }).desenhar(visao(pecas), null);
  return palco;
}

afterEach(() => {
  document.body.replaceChildren();
});

describe("criarCena", () => {
  it("lanterna ganha brilho antes e halo depois do botão", () => {
    const c = cenarioDoTerraco();

    const palco = desenhar(c, [{ peca: "lanterna", assento: "B", lugar: "R1" }]);

    const ordem = [...palco.querySelectorAll(".brilho, button[data-peca='lanterna'], .halo")];
    expect(ordem.map((el) => el.className)).toEqual(["brilho", "peca", "halo"]);
    const { pxPorMetro, largura } = c.geometria;
    expect(palco.querySelector<HTMLElement>(".halo")?.style.width).toBe(
      `${(((pxPorMetro * 0.75) / largura) * 100).toFixed(3)}%`,
    );
  });

  it("peça sem efeito não ganha brilho", () => {
    const palco = desenhar(CENARIO_MINIMO, [{ peca: "pedra", assento: "B", lugar: "P1" }]);

    expect(palco.querySelector("button[data-peca='pedra']")).not.toBeNull();
    expect(palco.querySelector(".brilho")).toBeNull();
    expect(palco.querySelector(".halo")).toBeNull();
  });
});
