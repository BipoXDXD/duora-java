import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Momento } from "./dominio/dados";
import type { EstadoDaSessao } from "./sessao/sessao";
import { usePassagemAoVivo } from "./usePassagemAoVivo";

function estado(tela: "carregando" | "encerrado", momento: Momento): EstadoDaSessao {
  const base = {
    enviando: null,
    cenaVazia: true,
    confirmandoSaida: false,
    somLigado: false,
    momento,
    ladoTelescopio: null,
  };
  return tela === "carregando"
    ? { ...base, tela }
    : { ...base, tela, titulo: "Terraço encerrado", texto: "Você saiu e o terraço foi encerrado." };
}

function montar(inicial: EstadoDaSessao) {
  return renderHook(({ e }) => usePassagemAoVivo(e), { initialProps: { e: inicial } });
}

describe("usePassagemAoVivo", () => {
  it("passagemDoCrepusculoParaANoiteEhAoVivo", () => {
    const { result, rerender } = montar(estado("encerrado", "crepusculo"));
    expect(result.current).toBe(false);

    rerender({ e: estado("encerrado", "noite") });

    expect(result.current).toBe(true);
  });

  it("recarregarJaReveladoNaoEhAoVivo", () => {
    const { result, rerender } = montar(estado("carregando", "crepusculo"));

    rerender({ e: estado("encerrado", "noite") });

    expect(result.current).toBe(false);
  });

  it("voltarAoCrepusculoEncerraAPassagem", () => {
    const { result, rerender } = montar(estado("encerrado", "crepusculo"));
    rerender({ e: estado("encerrado", "noite") });

    rerender({ e: estado("encerrado", "crepusculo") });

    expect(result.current).toBe(false);
  });
});
