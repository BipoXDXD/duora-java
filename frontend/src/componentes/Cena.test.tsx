import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Momento } from "../dominio/dados";
import type { CenarioFront } from "../cenario/tipos";
import type { Visao, VisaoAbertura, VisaoPeca } from "../dominio/visao";
import { cenarioDoTerraco, cenarioDoTerracoComAbertura } from "../teste/cenarioDoTerraco";
import { Cena } from "./Cena";

const CENARIO = cenarioDoTerraco();
const COM_ABERTURA = cenarioDoTerracoComAbertura();

const ABERTURA_ATIVA: VisaoAbertura = { ativa: true, voceTocou: false, outroTocou: false, acesa: false };

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
    abertura: null,
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

const MANTA_DE_BRUNO: VisaoPeca = { peca: "manta", assento: "A", lugar: "L2", destinosSugeriveis: ["L1"] };

/** A lanterna de Bruno em R2, que Ana pode sugerir para L1 ou R1. */
const LANTERNA_DE_BRUNO: VisaoPeca = { peca: "lanterna", assento: "A", lugar: "R2", destinosSugeriveis: ["L1", "R1"] };

interface Extras {
  sugerindo?: string | null;
  destaques?: readonly string[];
  enviando?: boolean;
  momento?: Momento;
  ladoTelescopio?: string | null;
  cenario?: CenarioFront;
  aoTocarAbertura?: () => void;
}

function desenhar(
  visao: Visao,
  selecionada: string | null = null,
  {
    sugerindo = null,
    destaques = SEM_DESTAQUES,
    enviando = false,
    momento = "crepusculo",
    ladoTelescopio = null,
    cenario = CENARIO,
    aoTocarAbertura = () => {},
  }: Extras = {},
) {
  return (
    <Cena
      visao={visao}
      marcas={{ selecionada, sugerindo, destaques }}
      enviando={enviando}
      momento={momento}
      ladoTelescopio={ladoTelescopio}
      cenario={cenario}
      aoEscolherPeca={() => {}}
      aoEscolherLugar={() => {}}
      aoTocarAbertura={aoTocarAbertura}
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

  it("semDestinoAPecaFicaIndisponivel", () => {
    render(desenhar(visaoDeAna([{ ...MANTA_DE_BRUNO, destinosSugeriveis: [] }])));

    const manta = screen.getByRole("button", { name: "Manta de Bruno, frente, à esquerda" });
    expect(manta).toHaveAttribute("aria-disabled", "true");
  });

  it("sugerindoMostraQueTalAquiEmCadaDestinoESoNeles", () => {
    const { container } = render(desenhar(visaoDeAna([LANTERNA_DE_BRUNO]), null, { sugerindo: "lanterna" }));

    const destinos = screen.getAllByRole("button", { name: /^Que tal aqui\? Sugerir a lanterna: / });
    expect(destinos.map((d) => d.getAttribute("aria-label"))).toEqual([
      "Que tal aqui? Sugerir a lanterna: fundo, à esquerda",
      "Que tal aqui? Sugerir a lanterna: fundo, à direita",
    ]);
    expect(destinos.every((d) => d.dataset.sugestao === "true" && d.textContent === "Que tal aqui?")).toBe(true);
    expect(container.querySelectorAll("#lugares .lugar")).toHaveLength(2);
    expect(screen.getByRole("button", { name: /^Lanterna de Bruno/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("quemRecebeVeOLugarSugeridoComContorno", () => {
    const lanterna: VisaoPeca = { peca: "lanterna", assento: "A", lugar: "R2", lugaresPermitidos: ["R1"] };
    const comSinal: Visao = { ...visaoDeBruno([lanterna]), sinal: { peca: "lanterna", deAssento: "B", lugar: "R1" } };

    const { container } = render(desenhar(comSinal));

    const marca = container.querySelector(".lugar-sugerido");
    expect(marca).toHaveAttribute("data-lugar", "R1");
    expect(marca).toHaveAttribute("aria-hidden", "true");
    expect(
      screen.getByRole("button", { name: /^Lanterna de Bruno, frente, à direita\..*Sugerido mover/ }),
    ).toBeVisible();
  });

  it("quemSugeriuNaoVeOLugarSugerido", () => {
    const comSinal: Visao = {
      ...visaoDeAna([LANTERNA_DE_BRUNO]),
      sinal: { peca: "lanterna", deAssento: "B", lugar: "R1" },
    };

    const { container } = render(desenhar(comSinal));

    expect(container.querySelector(".lugar-sugerido")).toBeNull();
  });

  it("pecaEmDestaqueTemSeloENome", () => {
    render(desenhar(visaoDeAna([MANTA_DE_BRUNO]), null, { destaques: ["manta"] }));

    const peca = screen.getByRole("button", { name: /^Manta de Bruno, frente, à esquerda\..*\. Mudou agora$/ });
    expect(peca).toHaveAttribute("data-destaque", "true");
    const selo = peca.querySelector(".selo-mudou");
    expect(selo).toHaveTextContent("Mudou");
    expect(selo).toHaveAttribute("aria-hidden", "true");
  });

  it("anelDoDestaqueSoPulsaNoPrimeiroDesenho", () => {
    const destaques = ["manta"];
    const { container, rerender } = render(desenhar(visaoDeAna([MANTA_DE_BRUNO]), null, { destaques }));
    expect(container.querySelector(".anel-destaque")).toHaveAttribute("data-pulsar", "true");

    rerender(desenhar(visaoDeAna([MANTA_DE_BRUNO], 2), null, { destaques }));

    expect(container.querySelector(".anel-destaque")).not.toHaveAttribute("data-pulsar");
  });

  it("selecionadaMostraLugaresPermitidos", () => {
    const telescopio: VisaoPeca = { peca: "telescopio", assento: "A", lugar: null, lugaresPermitidos: ["C1"] };

    render(desenhar(visaoDeBruno([telescopio]), "telescopio"));

    expect(screen.getByRole("button", { name: "Colocar o telescópio: centro, à esquerda" })).toBeInTheDocument();
  });

  it("focoNumaPecaSobreviveAoRedesenho", () => {
    const { container, rerender } = render(desenhar(visaoDeAna([MANTA_DE_BRUNO])));
    const antes = container.querySelector<HTMLElement>("button[data-peca=manta]");
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

  it("lanternaPropriaEUmBotaoComNomeESemFrase", () => {
    render(desenhar({ ...visaoDeAna([]), abertura: ABERTURA_ATIVA }, null, { cenario: COM_ABERTURA }));

    const lanterna = screen.getByRole("button", { name: "Acender sua lanterna" });
    expect(lanterna).toHaveAttribute("data-abertura");
    expect(lanterna).toHaveTextContent("");
    expect(screen.getAllByRole("button")).toHaveLength(1);
  });

  it("tocarALanternaPedeOToque", () => {
    const aoTocarAbertura = vi.fn();
    render(desenhar({ ...visaoDeAna([]), abertura: ABERTURA_ATIVA }, null, { cenario: COM_ABERTURA, aoTocarAbertura }));

    act(() => {
      screen.getByRole("button", { name: "Acender sua lanterna" }).click();
    });

    expect(aoTocarAbertura).toHaveBeenCalledOnce();
  });

  it("lanternaDoOutroTremulaQuandoEleToca", () => {
    // Ana está no assento B: a lanterna de Bruno é a do lado A.
    const { container, rerender } = render(
      desenhar({ ...visaoDeAna([]), abertura: ABERTURA_ATIVA }, null, { cenario: COM_ABERTURA }),
    );
    expect(container.querySelector('[data-lanterna="A"] [data-efeito="tremular"]')).toBeNull();

    rerender(
      desenhar({ ...visaoDeAna([]), abertura: { ...ABERTURA_ATIVA, outroTocou: true } }, null, {
        cenario: COM_ABERTURA,
      }),
    );

    expect(container.querySelector('[data-lanterna="A"] [data-efeito="tremular"]')).not.toBeNull();
    expect(container.querySelector('[data-lanterna="B"] [data-efeito="tremular"]')).toBeNull();
  });

  it("focoNaLanternaSobreviveAoRedesenho", () => {
    const { rerender } = render(
      desenhar({ ...visaoDeAna([]), abertura: ABERTURA_ATIVA }, null, { cenario: COM_ABERTURA }),
    );
    screen.getByRole("button", { name: "Acender sua lanterna" }).focus();

    rerender(
      desenhar({ ...visaoDeAna([]), abertura: { ...ABERTURA_ATIVA, voceTocou: true } }, null, {
        cenario: COM_ABERTURA,
      }),
    );

    expect(document.activeElement).toBe(screen.getByRole("button", { name: "Acender sua lanterna" }));
  });

  it("lanternasAcesasBrilhamSemBotao", () => {
    const acesa: VisaoAbertura = { ativa: false, voceTocou: true, outroTocou: true, acesa: true };
    const { container } = render(desenhar({ ...visaoDeAna([]), abertura: acesa }, null, { cenario: COM_ABERTURA }));

    expect(screen.queryByRole("button", { name: "Acender sua lanterna" })).toBeNull();
    expect(container.querySelectorAll('[data-lanterna] [data-efeito="brilho"]')).toHaveLength(2);
  });

  it("aberturaPeloTempoNaoDesenhaLanternas", () => {
    const apagada: VisaoAbertura = { ativa: false, voceTocou: false, outroTocou: false, acesa: false };
    const { container } = render(desenhar({ ...visaoDeAna([]), abertura: apagada }, null, { cenario: COM_ABERTURA }));

    expect(container.querySelector("[data-lanterna]")).toBeNull();
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
