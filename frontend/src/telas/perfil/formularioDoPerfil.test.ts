import { describe, expect, it } from "vitest";
import type { EdicaoDoPerfil, MeuPerfil } from "../../api/perfil";
import { deMeuPerfil, tiraNamoro, validar } from "./formularioDoPerfil";

const VALIDA: EdicaoDoPerfil = {
  versao: 3,
  apelido: "Lua",
  pronomes: null,
  descricaoPronomes: null,
  intencoes: ["AMIZADE"],
  bio: "",
};

const SALVO: MeuPerfil = {
  versao: 3,
  apelido: "Lua",
  pronomes: "ELU_DELU",
  descricaoPronomes: null,
  intencoes: ["AMIZADE", "NAMORO"],
  bio: "Gosto de céu.",
  pausada: false,
  prontoParaFila: true,
  pendencias: [],
  foto: { aprovada: null, pendente: null, recusadaEm: null },
};

describe("validar", () => {
  it("edicaoValidaSemErros", () => {
    expect(validar(VALIDA)).toEqual({});
  });

  it("apelidoSoComEspacosRecusado", () => {
    expect(validar({ ...VALIDA, apelido: "   " })).toEqual({ apelido: "Escreva um apelido." });
  });

  it("apelidoCom21CaracteresRecusado", () => {
    expect(validar({ ...VALIDA, apelido: "a".repeat(21) })).toEqual({ apelido: "Use até 20 caracteres." });
  });

  it("apelidoCom20EmojisAceito", () => {
    expect(validar({ ...VALIDA, apelido: "🌙".repeat(20) })).toEqual({});
  });

  it("pronomesOutrosSemDescricaoRecusados", () => {
    expect(validar({ ...VALIDA, pronomes: "OUTROS", descricaoPronomes: " " })).toEqual({
      descricaoPronomes: "Escreva seus pronomes com até 20 caracteres.",
    });
  });

  it("descricaoDePronomesCom21Recusada", () => {
    expect(validar({ ...VALIDA, pronomes: "OUTROS", descricaoPronomes: "x".repeat(21) })).toEqual({
      descricaoPronomes: "Escreva seus pronomes com até 20 caracteres.",
    });
  });

  it("intencoesVaziasRecusadas", () => {
    expect(validar({ ...VALIDA, intencoes: [] })).toEqual({ intencoes: "Marque Amizade, Namoro ou as duas." });
  });

  it.each(["\u3164", "\u200B", "\u00A0\u3000"])("apelidoSoComInvisiveisRecusado %#", (apelido) => {
    expect(validar({ ...VALIDA, apelido }).apelido).toBe("Escreva um apelido.");
  });

  it("apelidoComFormatacaoNoMeioRecusado", () => {
    expect(validar({ ...VALIDA, apelido: "Lu\u202Ea" }).apelido).toMatch(/caracteres invisíveis/);
  });

  it("bioComFormatacaoRecusada", () => {
    expect(validar({ ...VALIDA, bio: "oi\u2066oi" }).bio).toMatch(/caracteres invisíveis/);
  });

  it("bioComQuebraDeLinhaAceita", () => {
    expect(validar({ ...VALIDA, bio: "oi\nlinha" }).bio).toBeUndefined();
  });

  it("bioCom281Recusada", () => {
    expect(validar({ ...VALIDA, bio: "b".repeat(281) })).toEqual({ bio: "Use até 280 caracteres." });
  });

  it("bioCom280EmojisAceita", () => {
    expect(validar({ ...VALIDA, bio: "🌙".repeat(280) })).toEqual({});
  });
});

describe("deMeuPerfil", () => {
  it("copiaOsCamposDaEdicao", () => {
    expect(deMeuPerfil(SALVO)).toEqual({
      versao: 3,
      apelido: "Lua",
      pronomes: "ELU_DELU",
      descricaoPronomes: null,
      intencoes: ["AMIZADE", "NAMORO"],
      bio: "Gosto de céu.",
    });
  });

  it("perfilNaoSalvoViraApelidoVazio", () => {
    const novo: MeuPerfil = { ...SALVO, versao: null, apelido: null, pronomes: null, intencoes: [], bio: "" };

    expect(deMeuPerfil(novo)).toEqual({
      versao: null,
      apelido: "",
      pronomes: null,
      descricaoPronomes: null,
      intencoes: [],
      bio: "",
    });
  });
});

describe("tiraNamoro", () => {
  it("tiraNamoroSoQuandoOSalvoTinha", () => {
    const semNamoro: EdicaoDoPerfil = { ...deMeuPerfil(SALVO), intencoes: ["AMIZADE"] };

    expect(tiraNamoro(SALVO, semNamoro)).toBe(true);
    expect(tiraNamoro({ ...SALVO, intencoes: ["AMIZADE"] }, semNamoro)).toBe(false);
    expect(tiraNamoro(SALVO, deMeuPerfil(SALVO))).toBe(false);
  });
});
