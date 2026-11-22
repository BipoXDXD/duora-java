import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { VisaoPeca } from "../dominio/visao";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { criarMeteoros, ladoDosMeteoros } from "./meteoros";

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

describe("ladoDosMeteoros", () => {
  const CENARIO = cenarioDoTerraco();
  const banco: VisaoPeca = { peca: "banco", assento: "B", lugar: "C2" };

  it("com o telescópio colocado, os meteoros passam do lado dele", () => {
    const pecas: VisaoPeca[] = [{ peca: "telescopio", assento: "A", lugar: "C1" }, banco];

    expect(ladoDosMeteoros(CENARIO, pecas, "esquerdo")).toBe("esquerdo");
  });

  it("sem peça colocada que traga meteoros, não há lado", () => {
    const pecas: VisaoPeca[] = [{ peca: "telescopio", assento: "A", lugar: null }, banco];

    expect(ladoDosMeteoros(CENARIO, pecas, "esquerdo")).toBeNull();
  });
});
