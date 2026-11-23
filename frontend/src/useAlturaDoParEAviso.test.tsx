import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAlturaDoParEAviso } from "./useAlturaDoParEAviso";

/** ResizeObserver falso: guarda o último observador para o teste mandar uma nova medida. */
class ObservadorFalso {
  static ultimo: ObservadorFalso | null = null;
  readonly observados: Element[] = [];
  desligado = false;

  constructor(readonly avisar: () => void) {
    ObservadorFalso.ultimo = this;
  }

  observe(el: Element) {
    this.observados.push(el);
  }

  disconnect() {
    this.desligado = true;
  }
}

function blocoComAltura(altura: number): HTMLElement {
  const bloco = document.createElement("div");
  bloco.className = "par-e-aviso";
  medir(bloco, altura);
  document.body.append(bloco);
  return bloco;
}

function medir(bloco: HTMLElement, altura: number): void {
  bloco.getBoundingClientRect = () => ({ height: altura }) as DOMRect;
}

const variavel = () => document.documentElement.style.getPropertyValue("--altura-par-e-aviso");

describe("useAlturaDoParEAviso", () => {
  beforeEach(() => {
    ObservadorFalso.ultimo = null;
    vi.stubGlobal("ResizeObserver", ObservadorFalso);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.body.replaceChildren();
    document.documentElement.style.removeProperty("--altura-par-e-aviso");
  });

  it("grava a altura medida do bloco, mais a folga do anel de foco", () => {
    const bloco = blocoComAltura(72);

    renderHook(() => {
      useAlturaDoParEAviso();
    });

    expect(ObservadorFalso.ultimo?.observados).toEqual([bloco]);
    expect(variavel()).toBe("calc(72px + var(--e-2))");
  });

  it("acompanha o bloco quando o aviso quebra em mais linhas", () => {
    const bloco = blocoComAltura(72);
    renderHook(() => {
      useAlturaDoParEAviso();
    });

    medir(bloco, 120);
    ObservadorFalso.ultimo?.avisar();

    expect(variavel()).toBe("calc(120px + var(--e-2))");
  });

  it("ao desmontar, para de observar e devolve a estimativa do CSS", () => {
    blocoComAltura(72);
    const { unmount } = renderHook(() => {
      useAlturaDoParEAviso();
    });

    unmount();

    expect(ObservadorFalso.ultimo?.desligado).toBe(true);
    expect(variavel()).toBe("");
  });

  it("sem ResizeObserver, fica a estimativa do CSS", () => {
    vi.stubGlobal("ResizeObserver", undefined);
    blocoComAltura(72);

    renderHook(() => {
      useAlturaDoParEAviso();
    });

    expect(variavel()).toBe("");
  });
});
