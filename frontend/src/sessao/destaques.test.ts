import { describe, expect, it } from "vitest";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { pecasQueMudaram } from "./destaques";

/** Ana no assento A; Bruno no B, dono do banco e da lanterna. */
function visao(pecas: VisaoPeca[]): Visao {
  return {
    fase: "MONTANDO",
    versao: 1,
    voce: { assento: "A", nome: "Ana" },
    parceiro: { assento: "B", nome: "Bruno", conectado: true },
    convite: null,
    pecas,
    prontos: { voce: false, parceiro: false },
    confirmacao: { podeConfirmar: false, faltam: 4 },
    sinal: null,
    resultado: null,
    ajuste: null,
    abertura: null,
    conviteDaSala: "resumo-da-sala",
  };
}

const peca = (nome: string, assento: "A" | "B", lugar: string | null): VisaoPeca => ({ peca: nome, assento, lugar });

describe("pecasQueMudaram", () => {
  it("colocadaEMovidaContam", () => {
    const anterior = visao([peca("banco", "B", null), peca("lanterna", "B", "R1")]);
    const nova = visao([peca("banco", "B", "C2"), peca("lanterna", "B", "R2")]);

    expect(pecasQueMudaram(anterior, nova)).toEqual(["banco", "lanterna"]);
  });

  it("tiradaNaoConta", () => {
    const anterior = visao([peca("banco", "B", "C2"), peca("lanterna", "B", "R1")]);
    const nova = visao([peca("banco", "B", null), peca("lanterna", "B", "R1")]);

    expect(pecasQueMudaram(anterior, nova)).toEqual([]);
  });

  it("propriaNaoConta", () => {
    const anterior = visao([peca("telescopio", "A", null), peca("manta", "A", "L1")]);
    const nova = visao([peca("telescopio", "A", "C1"), peca("manta", "A", "L2")]);

    expect(pecasQueMudaram(anterior, nova)).toEqual([]);
  });
});
