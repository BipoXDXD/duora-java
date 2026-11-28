import { describe, expect, it } from "vitest";
import { contarCaracteres } from "./contar";

describe("contarCaracteres", () => {
  it.each([
    ["🙂", 1],
    ["a\nb", 3],
    ["🙂".repeat(20), 20],
    ["", 0],
  ])("conta %j como %i", (texto, esperado) => {
    expect(contarCaracteres(texto)).toBe(esperado);
  });
});
