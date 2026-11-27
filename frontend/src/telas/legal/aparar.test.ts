import { describe, expect, it } from "vitest";
import { aparar } from "./aparar";

describe("aparar", () => {
  it.each([
    ["espaço", " "],
    ["tabulação", "\t"],
    ["quebra de linha", "\n"],
    ["tabulação vertical", "\u000B"],
    ["avanço de página", "\f"],
    ["retorno", "\r"],
    ["separador de arquivo", "\u001C"],
    ["separador de unidade", "\u001F"],
    ["espaço ogham", "\u1680"],
    ["espaço em", "\u2003"],
    ["espaço fino", "\u2009"],
    ["separador de linha", "\u2028"],
    ["separador de parágrafo", "\u2029"],
    ["espaço matemático", "\u205F"],
    ["espaço ideográfico", "\u3000"],
  ])("tiraDasPontas %s", (_nome, espaco) => {
    expect(aparar(`${espaco}${espaco}a b${espaco}`)).toBe("a b");
  });

  it.each([
    ["espaço não quebrável", "\u00A0"],
    ["espaço de algarismo", "\u2007"],
    ["espaço fino não quebrável", "\u202F"],
    ["próxima linha", "\u0085"],
    ["marca de ordem de bytes", "\uFEFF"],
  ])("mantem %s, como o strip do Java", (_nome, caractere) => {
    expect(aparar(`${caractere}a${caractere}`)).toBe(`${caractere}a${caractere}`);
  });

  it("mantemOsEspacosDoMeio", () => {
    expect(aparar("\u001F a \u001F b \u001F")).toBe("a \u001F b");
  });

  it("textoSoDeEspacosFicaVazio", () => {
    expect(aparar(" \t\u001F\u3000")).toBe("");
  });
});
