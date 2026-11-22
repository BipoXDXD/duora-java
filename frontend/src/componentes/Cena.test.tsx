import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Geometria, MedidasSprite } from "../dominio/dados";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { Cena } from "./Cena";

const MEDIDAS: MedidasSprite = { largura: 200, altura: 150, ancoraX: 100, ancoraY: 120 };

/** Dois lugares (um de cada zona) e os sprites que eles usam. */
const GEOMETRIA: Geometria = {
  largura: 1800,
  altura: 1186,
  pxPorMetro: 100,
  variantes: [900],
  lugares: { C1: { x: 700, y: 600 }, L2: { x: 300, y: 900 } },
  props: {
    manta: { crepusculo: MEDIDAS, noite: MEDIDAS },
    "telescopio-esq": { crepusculo: MEDIDAS, noite: MEDIDAS },
  },
};

/** Ana no assento B; Bruno no A, dono do telescópio e da manta. */
function visaoDeAna(pecas: VisaoPeca[], versao = 1): Visao {
  return {
    fase: "MONTANDO",
    versao,
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

/** Bruno, com Ana no assento B. */
function visaoDeBruno(pecas: VisaoPeca[]): Visao {
  return {
    ...visaoDeAna(pecas),
    voce: { assento: "A", nome: "Bruno" },
    parceiro: { assento: "B", nome: "Ana", conectado: true },
  };
}

const MANTA_DE_BRUNO: VisaoPeca = { peca: "manta", assento: "A", lugar: "L2" };

function desenhar(visao: Visao, selecionada: string | null = null) {
  return (
    <Cena
      visao={visao}
      selecionada={selecionada}
      enviando={false}
      momento="crepusculo"
      ladoTelescopio={null}
      geometria={GEOMETRIA}
      aoEscolherPeca={() => {}}
      aoEscolherLugar={() => {}}
      aoMeteoro={() => {}}
    />
  );
}

describe("Cena", () => {
  beforeEach(() => {
    // O jsdom não implementa matchMedia; a cena só lê prefers-reduced-motion.
    vi.stubGlobal("matchMedia", (consulta: string) => ({
      matches: false,
      media: consulta,
      addEventListener: () => {},
      removeEventListener: () => {},
    }));
    return () => {
      vi.unstubAllGlobals();
    };
  });

  it("pecaDoParceiroTemNomeComDonoELugar", () => {
    render(desenhar(visaoDeAna([MANTA_DE_BRUNO])));

    expect(
      screen.getByRole("button", { name: "Manta de Bruno, frente, à esquerda. Ative para sugerir que mova" }),
    ).toBeInTheDocument();
  });

  it("selecionadaMostraLugaresPermitidos", () => {
    const telescopio: VisaoPeca = { peca: "telescopio", assento: "A", lugar: null, lugaresPermitidos: ["C1"] };

    render(desenhar(visaoDeBruno([telescopio]), "telescopio"));

    expect(screen.getByRole("button", { name: "Colocar o telescópio: centro, à esquerda" })).toBeInTheDocument();
  });

  it("focoNumaPecaSobreviveAoRedesenho", () => {
    const { container, rerender } = render(desenhar(visaoDeAna([MANTA_DE_BRUNO])));
    const antes = container.querySelector<HTMLElement>("[data-peca=manta]");
    antes?.focus();

    rerender(desenhar(visaoDeAna([MANTA_DE_BRUNO], 2)));

    const focado = document.activeElement;
    expect(focado).not.toBe(antes);
    expect(focado).toHaveAttribute("data-peca", "manta");
  });
});
