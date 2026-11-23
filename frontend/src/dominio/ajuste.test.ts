import { describe, expect, it } from "vitest";
import { situacaoDoAjuste } from "./ajuste";
import type { Visao } from "./visao";

const REVELADO: Visao = {
  fase: "REVELADO",
  versao: 4,
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

describe("situacaoDoAjuste", () => {
  it.each([
    [null, "nenhum"],
    [{ deAssento: "A" }, "meu"],
    [{ deAssento: "B" }, "do-outro"],
  ] as const)("ajuste %j é %s para quem está no assento A", (ajuste, situacao) => {
    expect(situacaoDoAjuste({ ...REVELADO, ajuste })).toBe(situacao);
  });
});
