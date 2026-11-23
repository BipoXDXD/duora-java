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
    ajuste: null,
    abertura: null,
    conviteDaSala: "resumo-da-sala",
  };
}

/** Monta o #palco com as duas camadas e desenha as peças com o cenário dado. */
function desenhar(cenario: CenarioFront, pecas: VisaoPeca[], fundos: HTMLElement[] = []): HTMLElement {
  return montarCena(cenario, pecas, fundos).palco;
}

function montarCena(cenario: CenarioFront, pecas: VisaoPeca[], fundos: HTMLElement[] = []) {
  const palco = document.createElement("div");
  palco.id = "palco";
  const camadaPecas = document.createElement("div");
  const camadaLugares = document.createElement("div");
  palco.append(...fundos, camadaPecas, camadaLugares);
  document.body.append(palco);
  const cena = criarCena({
    palco,
    camadaPecas,
    camadaLugares,
    cenario,
    aoEscolherPeca: () => {},
    aoEscolherLugar: () => {},
    aoTocarAbertura: () => {},
  });
  cena.desenhar(visao(pecas), { selecionada: null, sugerindo: null, destaques: [] });
  return { palco, cena };
}

afterEach(() => {
  document.body.replaceChildren();
});

describe("criarCena", () => {
  it("lanterna ganha brilho antes e halo depois do botão", () => {
    const c = cenarioDoTerraco();

    const palco = desenhar(c, [{ peca: "lanterna", assento: "B", lugar: "R1" }]);

    const ordem = [...palco.querySelectorAll(".brilho-piso, button[data-peca='lanterna'], .halo")];
    expect(ordem.map((el) => el.className)).toEqual(["brilho-piso", "peca", "halo"]);
    const { pxPorMetro, largura } = c.geometria;
    expect(palco.querySelector<HTMLElement>(".halo")?.style.width).toBe(
      `${(((pxPorMetro * 0.75) / largura) * 100).toFixed(3)}%`,
    );
  });

  it("fundo e sprites usam as variantes do cena.json do cenário", () => {
    // Com a lista padrão (900, 1800, 2700), a largura do jsdom escolheria 1800.
    const c: CenarioFront = { ...CENARIO_MINIMO, geometria: { ...CENARIO_MINIMO.geometria, variantes: [2700] } };
    const fundo = document.createElement("img");
    fundo.className = "terraco-crepusculo";

    const palco = desenhar(c, [{ peca: "pedra", assento: "B", lugar: "P1" }], [fundo]);

    expect(fundo.src).toMatch(/\/chao-crepusculo-2700\.webp$/);
    expect(palco.querySelector<HTMLImageElement>(".sprite-crepusculo")?.src).toMatch(/\/pedra-crepusculo-2700\.webp$/);
  });

  it("focarPecaFocaOBotaoDaPecaNaoOsEfeitos", () => {
    const { palco, cena } = montarCena(cenarioDoTerraco(), [{ peca: "lanterna", assento: "B", lugar: "R1" }]);

    expect(cena.focarPeca("lanterna")).toBe(true);
    expect(document.activeElement).toBe(palco.querySelector("button[data-peca='lanterna']"));
  });

  it("focarPecaForaDaCenaDizQueNaoHa", () => {
    const { cena } = montarCena(cenarioDoTerraco(), []);

    expect(cena.focarPeca("lanterna")).toBe(false);
  });

  it("peça sem efeito não ganha brilho", () => {
    const palco = desenhar(CENARIO_MINIMO, [{ peca: "pedra", assento: "B", lugar: "P1" }]);

    expect(palco.querySelector("button[data-peca='pedra']")).not.toBeNull();
    expect(palco.querySelector(".brilho-piso")).toBeNull();
    expect(palco.querySelector(".halo")).toBeNull();
  });
});
