import { describe, expect, it } from "vitest";
import type { Visao } from "../dominio/visao";
import { instrucao, novidade } from "./novidade";

const BRUNO = { assento: "B", nome: "Bruno", conectado: true } as const;

function visao(mudancas: Partial<Visao> = {}): Visao {
  return {
    fase: "MONTANDO",
    versao: 3,
    voce: { assento: "A", nome: "Ana" },
    parceiro: BRUNO,
    convite: null,
    pecas: [
      { peca: "telescopio", assento: "A", lugar: null },
      { peca: "manta", assento: "A", lugar: null },
      { peca: "banco", assento: "B", lugar: null },
    ],
    prontos: { voce: false, parceiro: false },
    confirmacao: { podeConfirmar: false, faltam: 4 },
    sinal: null,
    resultado: null,
    ...mudancas,
  };
}

describe("novidade", () => {
  it("sinal novo do parceiro", () => {
    const agora = visao({ sinal: { peca: "manta", deAssento: "B" } });

    expect(novidade(visao(), agora, false)).toBe("Bruno sugeriu mover a manta.");
  });

  it("o próprio sinal não é novidade", () => {
    const agora = visao({ sinal: { peca: "banco", deAssento: "A" } });

    expect(novidade(visao(), agora, false)).toBeNull();
  });

  it("parceiro entrou", () => {
    const antes = visao({ fase: "AGUARDANDO_PAR", parceiro: null });

    expect(novidade(antes, visao(), false)).toBe("Bruno entrou no terraço. Escolham as primeiras peças.");
  });

  it("os dois confirmaram", () => {
    expect(novidade(visao(), visao({ fase: "REVELADO" }), false)).toBe("Os dois confirmaram. Olhem o céu.");
  });

  it("REVELADO → MONTANDO: o parceiro voltou a ajustar", () => {
    expect(novidade(visao({ fase: "REVELADO" }), visao(), false)).toBe("Bruno voltou a ajustar o terraço.");
  });

  it("confirmação desfeita pela mudança", () => {
    const antes = visao({ prontos: { voce: true, parceiro: false } });

    expect(novidade(antes, visao(), false)).toBe(
      "A composição mudou e sua confirmação foi desfeita. Confirme de novo quando quiser.",
    );
  });

  it("o parceiro confirmou, junto com a peça que ele moveu", () => {
    const agora = visao({
      prontos: { voce: false, parceiro: true },
      pecas: [{ peca: "banco", assento: "B", lugar: "C1" }],
    });

    expect(novidade(visao(), agora, false)).toBe(
      "Bruno colocou o banco: centro, à esquerda. Bruno confirmou esta composição.",
    );
  });

  it("visão da própria ação só conta as peças do parceiro", () => {
    const antes = visao({ fase: "AGUARDANDO_PAR", parceiro: null });

    expect(novidade(antes, visao(), true)).toBeNull();
  });

  it("nada mudou", () => {
    expect(novidade(visao(), visao(), false)).toBeNull();
  });
});

describe("instrucao", () => {
  it("durante um envio", () => {
    expect(instrucao(visao(), null, "pecas")).toBe("Enviando…");
  });

  it("revelado", () => {
    expect(instrucao(visao({ fase: "REVELADO" }), null, null)).toBe("Olhem para cima.");
  });

  it("peça central selecionada", () => {
    expect(instrucao(visao(), "telescopio", null)).toBe(
      "Escolha onde o telescópio fica: na área de observação, no centro. Esc cancela.",
    );
  });

  it("peça lateral selecionada", () => {
    expect(instrucao(visao(), "manta", null)).toBe("Escolha onde a manta fica: em um lugar lateral. Esc cancela.");
  });

  it("você já confirmou", () => {
    expect(instrucao(visao({ prontos: { voce: true, parceiro: false } }), null, null)).toBe(
      "Esperando Bruno confirmar a mesma composição.",
    );
  });

  it("você já confirmou, sem parceiro", () => {
    expect(instrucao(visao({ parceiro: null, prontos: { voce: true, parceiro: false } }), null, null)).toBe(
      "Esperando a outra pessoa confirmar a mesma composição.",
    );
  });

  it("tudo no lugar", () => {
    expect(instrucao(visao({ confirmacao: { podeConfirmar: true, faltam: 0 } }), null, null)).toBe(
      "Tudo no lugar. Quando os dois confirmarem, o céu escurece.",
    );
  });

  it("nenhuma peça sua no terraço", () => {
    expect(instrucao(visao(), null, null)).toBe("Escolha uma das suas peças e depois um lugar no terraço.");
  });

  it("alguma peça sua já no terraço", () => {
    const agora = visao({ pecas: [{ peca: "telescopio", assento: "A", lugar: "C1" }] });

    expect(instrucao(agora, null, null)).toBe("Escolha sua segunda peça ou reorganize as que já colocou.");
  });
});
