import { describe, expect, it } from "vitest";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import {
  mensagemDeErro,
  textoBloqueio,
  textoConexaoDoParceiro,
  textoConfirmarSaida,
  textoEncerrado,
  textoMotivoConfirmacao,
  textoPecasDoParceiro,
  textoResultado,
} from "./mensagens";
import type { Visao, VisaoPeca } from "./visao";

const CENARIO = cenarioDoTerraco();

function visao(mudancas: Partial<Visao> = {}): Visao {
  return {
    fase: "MONTANDO",
    versao: 3,
    voce: { assento: "A", nome: "Ana" },
    parceiro: { assento: "B", nome: "Bruno", conectado: true },
    convite: null,
    pecas: [],
    prontos: { voce: false, parceiro: false },
    confirmacao: { podeConfirmar: false, faltam: 0 },
    sinal: null,
    resultado: null,
    ajuste: null,
    ...mudancas,
  };
}

const erro = (codigo: string, status = 409) => ({ codigo, status, motivo: null });

describe("mensagemDeErro", () => {
  it.each([
    ["REDE", "Sem conexão com o servidor. Confira se ele está rodando e tente de novo."],
    ["SERVIDOR", "O servidor falhou ao responder. Tente de novo em instantes."],
    ["RESPOSTA_INVALIDA", "O servidor falhou ao responder. Tente de novo em instantes."],
    ["SEM_ASSENTO", "Este navegador não está em nenhum terraço. Crie um ou abra um convite."],
    ["CONVITE_INVALIDO", "Este convite não vale mais. Peça um link novo ou crie seu próprio terraço."],
    ["PECA_ALHEIA", "Essa peça é de Bruno. Toque nela no terraço para sugerir que mova."],
    ["ORIGEM_RECUSADA", "O servidor recusou o pedido. Recarregue a página e tente de novo."],
    ["LUGAR_OCUPADO", "Bruno acabou de usar este lugar. Escolha outro."],
    ["VERSAO_OBSOLETA", "A composição mudou; veja a versão atual."],
    ["ESPERE_PRIMEIRA_PECA", "Espere Bruno colocar a primeira peça."],
    ["FASE_INVALIDA", "Essa ação não vale nesta etapa. O terraço foi atualizado."],
    ["INCOMPLETA", "Ainda faltam peças no terraço para confirmar."],
    ["LUGAR_INVALIDO", "Esse lugar não serve para essa peça. Escolha um dos lugares marcados."],
    ["PECA_INVALIDA", "Peça desconhecida. Recarregue a página."],
    ["APELIDO_INVALIDO", "Use de 1 a 20 caracteres no apelido."],
    ["LIMITE_SALAS", "O servidor já tem 20 terraços abertos. Tente de novo em alguns minutos."],
  ])("%s", (codigo, texto) => {
    expect(mensagemDeErro(erro(codigo), visao())).toBe(texto);
  });

  it("código desconhecido mostra o status", () => {
    expect(mensagemDeErro(erro("TEAPOT", 418), null)).toBe("O servidor não aceitou o pedido (418). Tente de novo.");
  });

  it("sem visão, fala da outra pessoa", () => {
    expect(mensagemDeErro(erro("LUGAR_OCUPADO"), null)).toBe(
      "A outra pessoa acabou de usar este lugar. Escolha outro.",
    );
  });
});

describe("textoBloqueio", () => {
  it("portão fechado sem visão espera a outra pessoa", () => {
    expect(textoBloqueio("ESPERE_PRIMEIRA_PECA", null)).toBe("Espere a outra pessoa colocar a primeira peça.");
  });

  it("outro bloqueio fica genérico", () => {
    expect(textoBloqueio("OUTRO", visao())).toBe("Indisponível agora.");
  });
});

describe("textoEncerrado", () => {
  it.each([
    ["SAIU", "Bruno", "Bruno saiu e o terraço foi encerrado."],
    ["SAIU", null, "A outra pessoa saiu e o terraço foi encerrado."],
    ["EXPIROU", "Bruno", "O terraço expirou depois de 30 minutos sem ações."],
    ["VOCE_SAIU", null, "Você saiu e o terraço foi encerrado."],
    ["SALA_INEXISTENTE", "Bruno", "Este terraço não existe mais (o servidor reiniciou ou ele expirou)."],
  ])("%s com parceiro %s", (motivo, parceiro, texto) => {
    expect(textoEncerrado(motivo, parceiro)).toBe(texto);
  });
});

describe("textoConfirmarSaida", () => {
  it("com parceiro, encerra para os dois", () => {
    expect(textoConfirmarSaida("Bruno")).toEqual({
      aviso: "Isso encerra o terraço para Bruno também.",
      acao: "Encerrar para os dois",
    });
  });

  it("sem parceiro, encerra só o terraço", () => {
    expect(textoConfirmarSaida(null)).toEqual({ aviso: "Isso encerra o terraço.", acao: "Encerrar o terraço" });
  });
});

describe("textoMotivoConfirmacao", () => {
  it.each([
    ["falta 1", visao({ confirmacao: { podeConfirmar: false, faltam: 1 } }), "Falta 1 peça no terraço."],
    ["faltam 2", visao({ confirmacao: { podeConfirmar: false, faltam: 2 } }), "Faltam 2 peças no terraço."],
    ["você confirmou", visao({ prontos: { voce: true, parceiro: false } }), "Você confirmou. Esperando Bruno."],
    ["parceiro confirmou", visao({ prontos: { voce: false, parceiro: true } }), "Bruno já confirmou esta composição."],
    ["vazio", visao(), ""],
  ])("%s", (_caso, v, texto) => {
    expect(textoMotivoConfirmacao(v)).toBe(texto);
  });
});

describe("textoResultado", () => {
  it("noite de manta e lanterna", () => {
    const revelada = visao({
      fase: "REVELADO",
      resultado: { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "leste" },
    });

    expect(textoResultado(CENARIO, revelada)).toEqual({
      titulo: "Uma noite de manta e lanterna",
      texto:
        "O telescópio de Ana aponta para o lado leste do céu, e é por ali que os meteoros passam. " +
        "Ana trouxe a manta; Bruno, o banco e a lanterna.",
    });
  });

  it("do lado de B, os nomes seguem os assentos", () => {
    const deB = visao({
      fase: "REVELADO",
      voce: { assento: "B", nome: "Bruno" },
      parceiro: { assento: "A", nome: "Ana", conectado: true },
      resultado: { secundariaA: "radio", secundariaB: "garrafa", ladoTelescopio: "oeste" },
    });

    expect(textoResultado(CENARIO, deB).texto).toBe(
      "O telescópio de Ana aponta para o lado oeste do céu, e é por ali que os meteoros passam. " +
        "Ana trouxe o rádio; Bruno, o banco e a garrafa térmica.",
    );
  });

  it("sem parceiro, a visão revelada é contrato quebrado", () => {
    const semParceiro = visao({
      fase: "REVELADO",
      parceiro: null,
      resultado: { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "leste" },
    });

    expect(() => textoResultado(CENARIO, semParceiro)).toThrow("Visão revelada sem quem está no assento B");
  });
});

describe("textoPecasDoParceiro", () => {
  const comLanterna = (lugar: string | null): Visao => {
    const pecas: VisaoPeca[] = [
      { peca: "manta", assento: "A", lugar: null, lugaresPermitidos: [], bloqueio: null },
      { peca: "lanterna", assento: "B", lugar },
    ];
    return visao({ pecas });
  };

  it.each([
    ["colocou", null, "L1", "Bruno colocou a lanterna: fundo, à esquerda."],
    ["moveu", "L1", "R2", "Bruno moveu a lanterna: frente, à direita."],
    ["tirou", "R2", null, "Bruno tirou a lanterna do terraço."],
  ])("%s", (_caso, antes, agora, texto) => {
    expect(textoPecasDoParceiro(CENARIO, comLanterna(antes), comLanterna(agora))).toBe(texto);
  });

  it("sem mudança devolve null", () => {
    expect(textoPecasDoParceiro(CENARIO, comLanterna("L1"), comLanterna("L1"))).toBeNull();
  });
});

describe("textoConexaoDoParceiro", () => {
  const conectado = (sim: boolean) => visao({ parceiro: { assento: "B", nome: "Bruno", conectado: sim } });

  it("parceiro caiu", () => {
    expect(textoConexaoDoParceiro(conectado(true), conectado(false))).toBe("Bruno ficou sem sinal.");
  });

  it("parceiro voltou", () => {
    expect(textoConexaoDoParceiro(conectado(false), conectado(true))).toBe("Bruno está de volta.");
  });

  it("sem mudança devolve null", () => {
    expect(textoConexaoDoParceiro(conectado(true), conectado(true))).toBeNull();
  });
});
