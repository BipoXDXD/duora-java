import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Momento } from "../dominio/dados";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { Cena } from "./Cena";

const CENARIO = cenarioDoTerraco();

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
    ajuste: null,
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

/** O mesmo array a cada render, como a sessão faz. */
const SEM_DESTAQUES: readonly string[] = [];

const MANTA_DE_BRUNO: VisaoPeca = { peca: "manta", assento: "A", lugar: "L2" };

interface Extras {
  enviando?: boolean;
  momento?: Momento;
  ladoTelescopio?: string | null;
}

function desenhar(
  visao: Visao,
  selecionada: string | null = null,
  { enviando = false, momento = "crepusculo", ladoTelescopio = null }: Extras = {},
) {
  return (
    <Cena
      visao={visao}
      marcas={{ selecionada, sugerindo: null, destaques: SEM_DESTAQUES }}
      enviando={enviando}
      momento={momento}
      ladoTelescopio={ladoTelescopio}
      cenario={CENARIO}
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

  it("focoNumaPecaComEfeitosVoltaAoBotaoDelaNoRedesenho", () => {
    // O brilho no piso vem antes do botão e também tem data-peca.
    const lanterna: VisaoPeca = { peca: "lanterna", assento: "B", lugar: "R1" };
    const { container, rerender } = render(desenhar(visaoDeAna([lanterna])));
    container.querySelector<HTMLElement>("button[data-peca=lanterna]")?.focus();

    rerender(desenhar(visaoDeAna([lanterna], 2)));

    expect(document.activeElement).toBe(container.querySelector("button[data-peca=lanterna]"));
  });

  it("aguardandoParLimpaAsCamadas", () => {
    const { container, rerender } = render(desenhar(visaoDeAna([MANTA_DE_BRUNO])));
    expect(container.querySelector("#pecas")?.children.length).toBeGreaterThan(0);

    rerender(desenhar({ ...visaoDeAna([MANTA_DE_BRUNO], 2), fase: "AGUARDANDO_PAR" }));

    expect(container.querySelector("#pecas")?.children).toHaveLength(0);
    expect(container.querySelector("#lugares")?.children).toHaveLength(0);
  });

  it("enviandoMarcaOPalco", () => {
    const { container, rerender } = render(desenhar(visaoDeAna([]), null, { enviando: true }));
    expect(container.querySelector("#palco")).toHaveAttribute("data-enviando", "true");

    rerender(desenhar(visaoDeAna([]), null, { enviando: false }));

    expect(container.querySelector("#palco")).toHaveAttribute("data-enviando", "false");
  });

  it("movimentoReduzidoLigadoComARevelacaoAbertaParaOsMeteoros", () => {
    const preferencia = { matches: false };
    const ouvintes: (() => void)[] = [];
    vi.stubGlobal("matchMedia", (consulta: string) => ({
      get matches() {
        return preferencia.matches;
      },
      media: consulta,
      addEventListener: (_: string, ouvinte: () => void) => {
        ouvintes.push(ouvinte);
      },
      removeEventListener: () => {},
    }));
    const { container } = render(desenhar(visaoDeAna([]), null, { momento: "noite", ladoTelescopio: "esquerdo" }));
    expect(container.querySelectorAll(".meteoro-parado")).toHaveLength(0);

    preferencia.matches = true;
    act(() => {
      ouvintes.forEach((ouvinte) => {
        ouvinte();
      });
    });

    expect(container.querySelectorAll(".meteoro-parado")).toHaveLength(4);
    expect(container.querySelector("#meteoros")).toHaveAttribute("data-lado", "esquerdo");
  });
});
