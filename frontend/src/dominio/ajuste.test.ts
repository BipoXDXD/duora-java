import { describe, expect, it } from "vitest";
import { situacaoDoAjuste, textoDoPedido } from "./ajuste";
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

describe("textoDoPedido sem parceiro na visão", () => {
  // A invariante diz que há parceiro na noite revelada; se ela quebrar, o texto não diz "undefined".
  it.each([
    [{ deAssento: "A" }, "Esperando a outra pessoa aceitar."],
    [{ deAssento: "B" }, "A outra pessoa quer ajustar a composição."],
  ] as const)("ajuste %j", (ajuste, texto) => {
    expect(textoDoPedido({ ...REVELADO, parceiro: null, ajuste })).toBe(texto);
  });
});
