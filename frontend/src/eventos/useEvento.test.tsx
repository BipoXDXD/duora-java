import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Canal, EstadoDaConexao } from "./canal";
import { useEstadoDaConexao, useEvento } from "./useEvento";

/** Um Canal em memória: guarda quem assinou e deixa o teste emitir e mudar o estado. */
function canalFalso() {
  const assinantes = new Map<string, Set<(dados: unknown) => void>>();
  const aoMudar = new Set<() => void>();
  let atual: EstadoDaConexao = "conectando";
  const assinar = vi.fn((nome: string, fn: (dados: unknown) => void) => {
    const doNome = assinantes.get(nome) ?? new Set();
    doNome.add(fn);
    assinantes.set(nome, doNome);
    return () => {
      doNome.delete(fn);
    };
  });
  const canal: Canal = {
    assinar,
    aoReconectar: () => () => undefined,
    aoMudarEstado: (fn) => {
      aoMudar.add(fn);
      return () => {
        aoMudar.delete(fn);
      };
    },
    estado: () => atual,
    retomar: () => undefined,
  };
  return {
    canal,
    assinar,
    assinantes: (nome: string) => assinantes.get(nome)?.size ?? 0,
    emitir: (nome: string, dados: unknown) => {
      for (const fn of assinantes.get(nome) ?? []) {
        fn(dados);
      }
    },
    mudar: (estado: EstadoDaConexao) => {
      atual = estado;
      for (const fn of aoMudar) {
        fn();
      }
    },
  };
}

describe("useEvento", () => {
  it("desmontarCancela", () => {
    const falso = canalFalso();
    const { unmount } = renderHook(() => {
      useEvento("fila.estado", vi.fn(), falso.canal);
    });
    expect(falso.assinantes("fila.estado")).toBe(1);

    unmount();

    expect(falso.assinantes("fila.estado")).toBe(0);
  });

  it("trocarOCallbackNaoReassina", () => {
    const falso = canalFalso();
    const primeiro = vi.fn();
    const segundo = vi.fn();
    const { rerender } = renderHook(
      ({ fn }: { fn: (dados: unknown) => void }) => {
        useEvento("fila.estado", fn, falso.canal);
      },
      { initialProps: { fn: primeiro } },
    );

    rerender({ fn: segundo });
    falso.emitir("fila.estado", { situacao: "PAREADO" });

    expect(falso.assinar).toHaveBeenCalledTimes(1);
    expect(primeiro).not.toHaveBeenCalled();
    expect(segundo).toHaveBeenCalledWith({ situacao: "PAREADO" });
  });
});

describe("useEstadoDaConexao", () => {
  it("estadoDaConexaoAcompanhaOCanal", () => {
    const falso = canalFalso();
    const { result } = renderHook(() => useEstadoDaConexao(falso.canal));
    expect(result.current).toBe("conectando");

    act(() => {
      falso.mudar("reconectando");
    });

    expect(result.current).toBe("reconectando");
  });
});
