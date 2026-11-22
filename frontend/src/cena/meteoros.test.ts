import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { criarMeteoros } from "./meteoros";

describe("criarMeteoros", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function camadaComMovimentoReduzido() {
    const camada = document.createElement("div");
    const meteoros = criarMeteoros(camada, { aoMeteoro: () => {}, movimentoReduzido: () => true });
    return { camada, meteoros };
  }

  it("com movimento reduzido, mostra quatro riscos parados no lado pedido", () => {
    const { camada, meteoros } = camadaComMovimentoReduzido();

    meteoros.mostrar("esquerdo");

    expect(camada.querySelectorAll(".meteoro-parado")).toHaveLength(4);
    expect(camada.dataset.lado).toBe("esquerdo");
  });

  it("limpar tira os riscos e o lado", () => {
    const { camada, meteoros } = camadaComMovimentoReduzido();
    meteoros.mostrar("esquerdo");

    meteoros.limpar();

    expect(camada.querySelectorAll(".meteoro")).toHaveLength(0);
    expect(camada.hasAttribute("data-lado")).toBe(false);
  });
});
