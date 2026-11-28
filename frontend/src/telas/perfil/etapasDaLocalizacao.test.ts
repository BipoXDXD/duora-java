import { describe, expect, it } from "vitest";
import { raioDisponivel, transicao, type EtapaDaLocalizacao } from "./etapasDaLocalizacao";

describe("etapasDaLocalizacao", () => {
  it("raioSoComAtiva", () => {
    const etapas: EtapaDaLocalizacao[] = [
      { tipo: "inativa" },
      { tipo: "inativa", falha: "negada" },
      { tipo: "pedindo" },
      { tipo: "gravando" },
      { tipo: "desativando" },
    ];

    expect(etapas.map(raioDisponivel)).toEqual([false, false, false, false, false]);
    expect(raioDisponivel({ tipo: "ativa" })).toBe(true);
  });

  it("falhaVoltaParaInativaComMotivo", () => {
    const pedindo = transicao({ tipo: "inativa" }, { tipo: "pedir" });

    expect(pedindo).toEqual({ tipo: "pedindo" });
    expect(transicao(pedindo, { tipo: "posicaoFalhou", falha: "semResposta" })).toEqual({
      tipo: "inativa",
      falha: "semResposta",
    });
    expect(transicao({ tipo: "gravando" }, { tipo: "gravacaoFalhou" })).toEqual({
      tipo: "inativa",
      falha: "servidor",
    });
  });

  it("desativarVoltaParaInativa", () => {
    const desativando = transicao({ tipo: "ativa" }, { tipo: "desativar" });

    expect(desativando).toEqual({ tipo: "desativando" });
    expect(transicao(desativando, { tipo: "desativada" })).toEqual({ tipo: "inativa" });
  });
});
