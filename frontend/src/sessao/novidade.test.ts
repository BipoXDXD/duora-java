import { describe, expect, it } from "vitest";
import type { Visao, VisaoAbertura } from "../dominio/visao";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { instrucao, novidade } from "./novidade";

const CENARIO = cenarioDoTerraco();

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
    ajuste: null,
    abertura: null,
    ...mudancas,
  };
}

const ABERTURA_ATIVA: VisaoAbertura = { ativa: true, voceTocou: false, outroTocou: false, acesa: false };

describe("novidade", () => {
  it("sinal novo do parceiro", () => {
    const agora = visao({ sinal: { peca: "manta", deAssento: "B", lugar: "L2" } });

    expect(novidade(CENARIO, visao(), agora, false)).toBe("Bruno sugeriu mover a manta: frente, à esquerda.");
  });

  it("novidadeDoSinalCitaODestino", () => {
    const deBruno = visao({
      voce: { assento: "B", nome: "Bruno" },
      parceiro: { assento: "A", nome: "Ana", conectado: true },
    });
    const agora = { ...deBruno, sinal: { peca: "lanterna", deAssento: "A", lugar: "R1" } } as const;

    expect(novidade(CENARIO, deBruno, agora, false)).toBe("Ana sugeriu mover a lanterna: fundo, à direita.");
  });

  it("o mesmo sinal para outro lugar é novidade", () => {
    const antes = visao({ sinal: { peca: "manta", deAssento: "B", lugar: "L2" } });
    const agora = visao({ sinal: { peca: "manta", deAssento: "B", lugar: "R1" } });

    expect(novidade(CENARIO, antes, agora, false)).toBe("Bruno sugeriu mover a manta: fundo, à direita.");
  });

  it("o próprio sinal não é novidade", () => {
    const agora = visao({ sinal: { peca: "banco", deAssento: "A", lugar: "C1" } });

    expect(novidade(CENARIO, visao(), agora, false)).toBeNull();
  });

  it("parceiro entrou", () => {
    const antes = visao({ fase: "AGUARDANDO_PAR", parceiro: null });

    expect(novidade(CENARIO, antes, visao(), false)).toBe("Bruno entrou no terraço. Escolham as primeiras peças.");
  });

  it("os dois confirmaram", () => {
    expect(novidade(CENARIO, visao(), visao({ fase: "REVELADO" }), false)).toBe("Os dois confirmaram. Olhem o céu.");
  });

  it("REVELADO → MONTANDO: o parceiro voltou a ajustar", () => {
    expect(novidade(CENARIO, visao({ fase: "REVELADO" }), visao(), false)).toBe("Bruno voltou a ajustar o terraço.");
  });

  it("confirmação desfeita pela mudança", () => {
    const antes = visao({ prontos: { voce: true, parceiro: false } });

    expect(novidade(CENARIO, antes, visao(), false)).toBe(
      "A composição mudou e sua confirmação foi desfeita. Confirme de novo quando quiser.",
    );
  });

  it("o parceiro confirmou, junto com a peça que ele moveu", () => {
    const agora = visao({
      prontos: { voce: false, parceiro: true },
      pecas: [{ peca: "banco", assento: "B", lugar: "C1" }],
    });

    expect(novidade(CENARIO, visao(), agora, false)).toBe(
      "Bruno colocou o banco: centro, à esquerda. Bruno confirmou esta composição.",
    );
  });

  it("visão da própria ação só conta as peças do parceiro", () => {
    const antes = visao({ fase: "AGUARDANDO_PAR", parceiro: null });

    expect(novidade(CENARIO, antes, visao(), true)).toBeNull();
  });

  it("nada mudou", () => {
    expect(novidade(CENARIO, visao(), visao(), false)).toBeNull();
  });
});

describe("novidade: abertura", () => {
  it("lanternasAcesasAnunciam", () => {
    const antes = visao({ abertura: { ...ABERTURA_ATIVA, outroTocou: true } });
    const agora = visao({ abertura: { ativa: false, voceTocou: true, outroTocou: true, acesa: true } });

    expect(novidade(CENARIO, antes, agora, false)).toBe("As lanternas acenderam.");
  });

  it("lanternasAcesasPeloProprioToqueTambemAnunciam", () => {
    const antes = visao({ abertura: { ...ABERTURA_ATIVA, outroTocou: true } });
    const agora = visao({ abertura: { ativa: false, voceTocou: true, outroTocou: true, acesa: true } });

    expect(novidade(CENARIO, antes, agora, true)).toBe("As lanternas acenderam.");
  });

  it("aberturaPeloTempoNaoAnuncia", () => {
    const antes = visao({ abertura: { ...ABERTURA_ATIVA, voceTocou: true } });
    const agora = visao({ abertura: { ativa: false, voceTocou: false, outroTocou: false, acesa: false } });

    expect(novidade(CENARIO, antes, agora, false)).toBeNull();
  });

  it("lanternasJaAcesasNaoAnunciamDeNovo", () => {
    const acesa = visao({ abertura: { ativa: false, voceTocou: false, outroTocou: false, acesa: true } });

    expect(novidade(CENARIO, acesa, { ...acesa, versao: 4 }, false)).toBeNull();
  });

  it("parceiroEntrouDuranteAAberturaNaoPedePecas", () => {
    const antes = visao({ fase: "AGUARDANDO_PAR", parceiro: null });

    expect(novidade(CENARIO, antes, visao({ abertura: ABERTURA_ATIVA }), false)).toBe("Bruno entrou no terraço.");
  });
});

describe("instrucao", () => {
  it("duranteAAberturaSemInstrucao", () => {
    expect(instrucao(CENARIO, visao({ abertura: ABERTURA_ATIVA }), null, null)).toBe("");
  });

  it("duranteOEnvioDoToqueContinuaSemInstrucao", () => {
    expect(instrucao(CENARIO, visao({ abertura: ABERTURA_ATIVA }), null, "abertura")).toBe("");
  });

  it("depoisDaAberturaAInstrucaoVolta", () => {
    const concluida = visao({ abertura: { ...ABERTURA_ATIVA, ativa: false, acesa: true } });

    expect(instrucao(CENARIO, concluida, null, null)).toBe("Escolha uma das suas peças e depois um lugar no terraço.");
  });

  it("durante um envio", () => {
    expect(instrucao(CENARIO, visao(), null, "pecas")).toBe("Enviando…");
  });

  it("revelado", () => {
    expect(instrucao(CENARIO, visao({ fase: "REVELADO" }), null, null)).toBe("Olhem para cima.");
  });

  it("peça central selecionada", () => {
    expect(instrucao(CENARIO, visao(), "telescopio", null)).toBe(
      "Escolha onde o telescópio fica: na área de observação, no centro. Esc cancela.",
    );
  });

  it("peça lateral selecionada", () => {
    expect(instrucao(CENARIO, visao(), "manta", null)).toBe(
      "Escolha onde a manta fica: em um lugar lateral. Esc cancela.",
    );
  });

  it("sem sufixo de cancelar (toque), a instrução da seleção não cita o Esc", () => {
    expect(instrucao(CENARIO, visao(), "manta", null, null, "")).toBe(
      "Escolha onde a manta fica: em um lugar lateral.",
    );
  });

  it("você já confirmou", () => {
    expect(instrucao(CENARIO, visao({ prontos: { voce: true, parceiro: false } }), null, null)).toBe(
      "Esperando Bruno confirmar a mesma composição.",
    );
  });

  it("você já confirmou, sem parceiro", () => {
    expect(instrucao(CENARIO, visao({ parceiro: null, prontos: { voce: true, parceiro: false } }), null, null)).toBe(
      "Esperando a outra pessoa confirmar a mesma composição.",
    );
  });

  it("tudo no lugar", () => {
    expect(instrucao(CENARIO, visao({ confirmacao: { podeConfirmar: true, faltam: 0 } }), null, null)).toBe(
      "Tudo no lugar. Quando os dois confirmarem, o céu escurece.",
    );
  });

  it("nenhuma peça sua no terraço", () => {
    expect(instrucao(CENARIO, visao(), null, null)).toBe("Escolha uma das suas peças e depois um lugar no terraço.");
  });

  it("alguma peça sua já no terraço", () => {
    const agora = visao({ pecas: [{ peca: "telescopio", assento: "A", lugar: "C1" }] });

    expect(instrucao(CENARIO, agora, null, null)).toBe("Escolha sua segunda peça ou reorganize as que já colocou.");
  });
});
