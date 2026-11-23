import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Momento } from "./dominio/dados";
import type { Visao } from "./dominio/visao";
import type { EstadoDaSessao } from "./sessao/sessao";
import { useTelaCheia } from "./useTelaCheia";

const VISAO: Visao = {
  fase: "REVELADO",
  versao: 1,
  voce: { assento: "A", nome: "Ana" },
  parceiro: { assento: "B", nome: "Bruno", conectado: true },
  convite: null,
  pecas: [],
  prontos: { voce: true, parceiro: true },
  confirmacao: { podeConfirmar: false, faltam: 0 },
  sinal: null,
  resultado: { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "esquerdo" },
  ajuste: null,
  abertura: null,
  conviteDaSala: "resumo-da-sala",
};

function montagem(momento: Momento): EstadoDaSessao {
  return {
    tela: "montagem",
    visao: momento === "noite" ? VISAO : { ...VISAO, fase: "MONTANDO", resultado: null },
    selecionada: null,
    sugerindo: null,
    destaques: [],
    instrucao: "",
    enviando: null,
    cenaVazia: false,
    confirmandoSaida: false,
    somLigado: false,
    momento,
    ladoTelescopio: null,
  };
}

/** matchMedia falso: só a consulta do celular alto responde, e a tela pode girar. */
const tela = { celularAlto: true };
const ouvintes = new Set<() => void>();

function girar(celularAlto: boolean): void {
  tela.celularAlto = celularAlto;
  ouvintes.forEach((ouvinte) => {
    ouvinte();
  });
}

beforeEach(() => {
  tela.celularAlto = true;
  ouvintes.clear();
  vi.stubGlobal("matchMedia", (consulta: string) => ({
    get matches() {
      return consulta === "(max-width: 860px) and (min-height: 700px)" && tela.celularAlto;
    },
    media: consulta,
    addEventListener: (_: string, ouvinte: () => void) => ouvintes.add(ouvinte),
    removeEventListener: (_: string, ouvinte: () => void) => ouvintes.delete(ouvinte),
  }));
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function montar(inicial: EstadoDaSessao) {
  return renderHook(({ e }) => useTelaCheia(e), { initialProps: { e: inicial } });
}

describe("useTelaCheia", () => {
  it("no celular em pé, a noite revelada ocupa a tela", () => {
    const { result } = montar(montagem("noite"));

    expect(result.current.telaCheia).toBe(true);
  });

  it("o crepúsculo não fica em tela cheia", () => {
    const { result } = montar(montagem("crepusculo"));

    expect(result.current.telaCheia).toBe(false);
  });

  it("fora do celular em pé (deitado, zoom, computador), sem tela cheia", () => {
    tela.celularAlto = false;
    const { result } = montar(montagem("noite"));

    expect(result.current.telaCheia).toBe(false);
  });

  it("ver o painel sai da tela cheia e ver o céu volta", () => {
    const { result } = montar(montagem("noite"));

    act(() => {
      result.current.verPainel();
    });
    expect(result.current.telaCheia).toBe(false);

    act(() => {
      result.current.verCeu();
    });
    expect(result.current.telaCheia).toBe(true);
  });

  it("cada passagem para a noite volta à tela cheia", () => {
    const { result, rerender } = montar(montagem("noite"));
    act(() => {
      result.current.verPainel();
    });

    rerender({ e: montagem("crepusculo") });
    rerender({ e: montagem("noite") });

    expect(result.current.telaCheia).toBe(true);
  });

  it("girar o celular acompanha a tela", () => {
    const { result } = montar(montagem("noite"));

    act(() => {
      girar(false);
    });

    expect(result.current.telaCheia).toBe(false);
  });

  it("na tela cheia, o documento escondido por trás não rola", () => {
    const travado = () => document.documentElement.classList.contains("tela-cheia");
    const { result, unmount } = montar(montagem("noite"));
    expect(travado()).toBe(true);

    act(() => {
      result.current.verPainel();
    });
    expect(travado()).toBe(false);

    act(() => {
      result.current.verCeu();
    });
    unmount();
    expect(travado()).toBe(false);
  });
});
