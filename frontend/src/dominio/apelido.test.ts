import { describe, expect, it } from "vitest";
import { validarApelido } from "./apelido";

describe("validarApelido", () => {
  it.each([
    ["", "Escreva um apelido."],
    ["a".repeat(21), "Use no máximo 20 caracteres."],
    ["a\u0007", "Use só letras, números e símbolos comuns."],
    ["Ana", ""],
  ])("%j → %j", (apelido, esperado) => {
    expect(validarApelido(apelido)).toBe(esperado);
  });

  it("conta caracteres, não unidades UTF-16: 20 emojis cabem", () => {
    expect(validarApelido("🌙".repeat(20))).toBe("");
  });
});
