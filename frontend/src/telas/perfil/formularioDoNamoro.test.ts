import { describe, expect, it } from "vitest";
import { paraEnvio, validarNamoro, type EdicaoDoNamoroNaTela } from "./formularioDoNamoro";

const VALIDA: EdicaoDoNamoroNaTela = { genero: "MULHER", descricaoGenero: "", interesses: ["HOMEM"] };

describe("validarNamoro", () => {
  it("edicaoValidaSemErros", () => {
    expect(validarNamoro(VALIDA)).toEqual({});
  });

  it("semGeneroRecusado", () => {
    expect(validarNamoro({ ...VALIDA, genero: null })).toEqual({ genero: "Escolha seu gênero." });
  });

  it("outroSemDescricaoRecusado", () => {
    expect(validarNamoro({ ...VALIDA, genero: "OUTRO", descricaoGenero: "   " })).toEqual({
      descricaoGenero: "Escreva como você se descreve, com até 40 caracteres.",
    });
  });

  it("descricaoCom41Recusada", () => {
    expect(validarNamoro({ ...VALIDA, genero: "OUTRO", descricaoGenero: "a".repeat(41) })).toEqual({
      descricaoGenero: "Escreva como você se descreve, com até 40 caracteres.",
    });
  });

  it("descricaoCom40EmojisAceita", () => {
    expect(validarNamoro({ ...VALIDA, genero: "OUTRO", descricaoGenero: "🌙".repeat(40) })).toEqual({});
  });

  it("interessesVaziosRecusados", () => {
    expect(validarNamoro({ ...VALIDA, interesses: [] })).toEqual({ interesses: "Marque ao menos uma opção." });
  });
});

describe("paraEnvio", () => {
  it("descricaoSoVaiComOutro", () => {
    expect(paraEnvio({ genero: "MULHER", descricaoGenero: "sobra de antes", interesses: ["HOMEM"] })).toEqual({
      genero: "MULHER",
      descricaoGenero: null,
      interesses: ["HOMEM"],
      consentimento: true,
    });
    expect(paraEnvio({ genero: "OUTRO", descricaoGenero: "  fluide  ", interesses: ["HOMEM"] })).toEqual({
      genero: "OUTRO",
      descricaoGenero: "fluide",
      interesses: ["HOMEM"],
      consentimento: true,
    });
  });
});
