import { describe, expect, it } from "vitest";
import { cenarioDoTerraco, jsonsDoTerraco } from "../teste/cenarioDoTerraco";
import { CENARIO_MINIMO } from "../teste/cenarioMinimo";
import { lerCenario } from "./ler";
import { ErroCenario } from "./tipos";

/** O objeto no caminho dado de um JSON lido, para os testes o alterarem. */
function em(raiz: unknown, ...caminho: string[]): Record<string, unknown> {
  let atual = raiz;
  for (const chave of caminho) {
    atual = (atual as Record<string, unknown>)[chave];
  }
  return atual as Record<string, unknown>;
}

/** Mensagem do ErroCenario da leitura; qualquer outro desfecho falha o teste. */
function erroAoLer(cenario: unknown, geometria: unknown, id = "terraco"): string {
  try {
    lerCenario(cenario, geometria, id);
  } catch (erro) {
    if (erro instanceof ErroCenario) {
      return erro.message;
    }
    throw erro;
  }
  throw new Error("A leitura deveria ter lançado ErroCenario");
}

describe("lerCenario: validação", () => {
  it("o cenário do terraço é válido", () => {
    const c = cenarioDoTerraco();

    expect(c.id).toBe("terraco");
    expect(c.nome).toBe("Terraço das Estrelas Cadentes");
  });

  it("bate com as peças e os lugares do terraço", () => {
    const c = cenarioDoTerraco();

    expect(c.peca("telescopio")).toEqual({
      nome: "Telescópio",
      artigo: "o telescópio",
      assento: "A",
      zona: "centro",
      obrigatoria: true,
    });
    expect(c.peca("manta")).toEqual({
      nome: "Manta",
      artigo: "a manta",
      assento: "A",
      zona: "lateral",
      obrigatoria: false,
    });
    expect(c.peca("radio")).toEqual({
      nome: "Rádio",
      artigo: "o rádio",
      assento: "A",
      zona: "lateral",
      obrigatoria: false,
    });
    expect(c.peca("banco")).toEqual({
      nome: "Banco",
      artigo: "o banco",
      assento: "B",
      zona: "centro",
      obrigatoria: true,
    });
    expect(c.peca("lanterna")).toEqual({
      nome: "Lanterna",
      artigo: "a lanterna",
      assento: "B",
      zona: "lateral",
      obrigatoria: false,
    });
    expect(c.peca("garrafa")).toEqual({
      nome: "Garrafa térmica",
      artigo: "a garrafa térmica",
      assento: "B",
      zona: "lateral",
      obrigatoria: false,
    });
    expect(["C1", "C2", "L1", "L2", "R1", "R2"].map((id) => c.lugar(id).nome)).toEqual([
      "centro, à esquerda",
      "centro, à direita",
      "fundo, à esquerda",
      "frente, à esquerda",
      "fundo, à direita",
      "frente, à direita",
    ]);
  });

  it("falta de artigo aponta o caminho", () => {
    const { cenario, geometria } = jsonsDoTerraco();
    delete em(cenario, "pecas", "manta").artigo;

    expect(erroAoLer(cenario, geometria)).toContain("pecas.manta.artigo");
  });

  it("chave desconhecida é recusada", () => {
    const { cenario, geometria } = jsonsDoTerraco();
    em(cenario, "pecas", "manta").cor = "azul";

    expect(erroAoLer(cenario, geometria)).toContain("pecas.manta");
  });

  it("sprite de peça inexistente é recusado", () => {
    const { cenario, geometria } = jsonsDoTerraco();
    em(cenario, "sprites").vaso = { padrao: "banco" };

    expect(erroAoLer(cenario, geometria)).toContain("sprites.vaso");
  });

  it("zona inexistente é recusada", () => {
    const { cenario, geometria } = jsonsDoTerraco();
    em(cenario, "pecas", "manta").zona = "teto";

    expect(erroAoLer(cenario, geometria)).toContain("pecas.manta.zona");
  });

  it("lugar sem ponto no cena.json é recusado", () => {
    const { cenario, geometria } = jsonsDoTerraco();
    delete em(geometria, "lugares").C1;

    expect(erroAoLer(cenario, geometria)).toContain("C1");
  });

  it("sprite sem medidas é recusado", () => {
    const { cenario, geometria } = jsonsDoTerraco();
    delete em(geometria, "props")["telescopio-dir"];

    expect(erroAoLer(cenario, geometria)).toContain("telescopio-dir");
  });

  it("id diferente do pedido é recusado", () => {
    const { cenario, geometria } = jsonsDoTerraco();

    expect(erroAoLer(cenario, geometria, "minimo")).toContain('id: esperado "minimo", veio "terraco"');
  });

  it("o cenário mínimo é lido", () => {
    expect(CENARIO_MINIMO.id).toBe("minimo");
    expect(CENARIO_MINIMO.peca("pedra")).toEqual({
      nome: "Pedra",
      artigo: "a pedra",
      assento: "A",
      zona: "unica",
      obrigatoria: true,
    });
    expect(CENARIO_MINIMO.lugar("P2")).toEqual({ nome: "segundo lugar" });
  });
});

describe("lerCenario: consultas", () => {
  const c = cenarioDoTerraco();

  it("peça desconhecida lança como hoje", () => {
    expect(() => c.peca("vaso")).toThrow("Peça desconhecida: vaso");
  });

  it("lugar desconhecido lança como hoje", () => {
    expect(() => c.lugar("Z9")).toThrow("Lugar desconhecido: Z9");
  });

  it("zona traz os textos do cenário", () => {
    expect(c.zona("centro")).toEqual({
      descricao: "Vai na área de observação, no centro",
      instrucao: "na área de observação, no centro",
      checklist: "{nome} no centro",
    });
  });

  it.each([
    ["telescopio", "C2", "telescopio-dir"],
    ["telescopio", "C1", "telescopio-esq"],
    ["telescopio", null, "telescopio-dir"],
    ["banco", "L1", "banco"],
    ["manta", "L2", "manta"],
  ])("sprite(%s, %s) é %s", (peca, lugar, esperado) => {
    expect(c.sprite(peca, lugar)).toBe(esperado);
  });

  it("urlSprite aponta para a cena do cenário", () => {
    expect(c.urlSprite("banco", "noite", 900)).toBe("/assets/cenarios/terraco/cena/banco-noite-900.webp");
  });

  it("urlCeu aponta para o céu do cenário", () => {
    expect(c.urlCeu("crepusculo", 1280)).toBe("/assets/cenarios/terraco/ceu/ceu-crepusculo-1280.webp");
  });

  it("som traz url e volume", () => {
    expect(c.som("meteoro")).toEqual({ url: "/assets/cenarios/terraco/audio/meteoro.mp3", volume: 0.5 });
  });

  it("revelação da lanterna traz os dois efeitos", () => {
    expect(c.revelacao("lanterna")).toEqual([
      { tipo: "brilho-piso", largura: 2.4 },
      { tipo: "halo", alturaProjetada: 0.3464, largura: 0.75 },
    ]);
  });

  it("peça sem efeitos não tem revelação", () => {
    expect(c.revelacao("banco")).toEqual([]);
  });
});
