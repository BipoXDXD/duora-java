import { renderHook } from "@testing-library/react";
import { StrictMode } from "react";
import { describe, expect, it, vi } from "vitest";
import type { Som } from "./cena/som";
import type { Momento } from "./dominio/dados";
import type { Visao, VisaoPeca } from "./dominio/visao";
import type { EstadoDaSessao } from "./sessao/sessao";
import { cenarioDoTerraco } from "./teste/cenarioDoTerraco";
import { useSomDaRevelacao } from "./useSomDaRevelacao";

const CENARIO = cenarioDoTerraco();
const NOTAS_DO_RADIO = "/assets/cenarios/terraco/audio/radio-notas.mp3";

const comRadio = (lugar: string | null): VisaoPeca[] => [
  { peca: "telescopio", assento: "A", lugar: "C1" },
  { peca: "radio", assento: "A", lugar },
  { peca: "banco", assento: "B", lugar: "C2" },
];

function visao(pecas: VisaoPeca[]): Visao {
  return {
    fase: "MONTANDO",
    versao: 1,
    voce: { assento: "A", nome: "Ana" },
    parceiro: { assento: "B", nome: "Bruno", conectado: true },
    convite: null,
    pecas,
    prontos: { voce: false, parceiro: false },
    confirmacao: { podeConfirmar: false, faltam: 0 },
    sinal: null,
    resultado: null,
    ajuste: null,
    abertura: null,
  };
}

function montagem(momento: Momento, pecas: VisaoPeca[]): EstadoDaSessao {
  return {
    tela: "montagem",
    visao: visao(pecas),
    selecionada: null,
    sugerindo: null,
    destaques: [],
    instrucao: "",
    enviando: null,
    cenaVazia: false,
    confirmandoSaida: false,
    somLigado: false,
    momento,
    ladoTelescopio: momento === "noite" ? "esquerdo" : null,
  };
}

const CARREGANDO: EstadoDaSessao = {
  tela: "carregando",
  enviando: null,
  cenaVazia: true,
  confirmandoSaida: false,
  somLigado: false,
  momento: "crepusculo",
  ladoTelescopio: null,
};

function somFalso() {
  return { revelacao: vi.fn<Som["revelacao"]>(), notas: vi.fn<Som["notas"]>() };
}

function montar(som: ReturnType<typeof somFalso>, inicial: EstadoDaSessao) {
  return renderHook(
    ({ e }) => {
      useSomDaRevelacao(som, CENARIO, e);
    },
    {
      initialProps: { e: inicial },
      wrapper: StrictMode,
    },
  );
}

describe("useSomDaRevelacao", () => {
  it("na passagem ao vivo, o rádio colocado toca as notas uma vez", () => {
    const som = somFalso();
    const { rerender } = montar(som, montagem("crepusculo", comRadio("L2")));

    rerender({ e: montagem("noite", comRadio("L2")) });
    // Nova visão da mesma noite (polling): a passagem continua a mesma.
    rerender({ e: montagem("noite", comRadio("L2")) });

    expect(som.notas.mock.calls).toEqual([[NOTAS_DO_RADIO, 0.4]]);
    expect(som.revelacao).toHaveBeenCalledOnce();
  });

  it("sem o rádio colocado, a passagem não toca notas", () => {
    const som = somFalso();
    const { rerender } = montar(som, montagem("crepusculo", comRadio(null)));

    rerender({ e: montagem("noite", comRadio(null)) });

    expect(som.notas).not.toHaveBeenCalled();
    expect(som.revelacao).toHaveBeenCalledOnce();
  });

  it("abrir a página já na noite não toca notas", () => {
    const som = somFalso();
    const { rerender } = montar(som, CARREGANDO);

    rerender({ e: montagem("noite", comRadio("L2")) });

    expect(som.notas).not.toHaveBeenCalled();
    expect(som.revelacao).not.toHaveBeenCalled();
  });

  it("cada nova passagem para a noite toca de novo", () => {
    const som = somFalso();
    const { rerender } = montar(som, montagem("crepusculo", comRadio("L2")));
    rerender({ e: montagem("noite", comRadio("L2")) });

    rerender({ e: montagem("crepusculo", comRadio("L2")) });
    rerender({ e: montagem("noite", comRadio("L2")) });

    expect(som.notas).toHaveBeenCalledTimes(2);
  });
});
