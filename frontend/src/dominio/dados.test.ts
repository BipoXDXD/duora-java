import { describe, expect, it } from "vitest";
import { escolherVariante } from "./dados";

describe("escolherVariante", () => {
  it.each([
    [390, 1, 900],
    [390, 3, 1800],
    [1440, 2, 2700],
    [4000, 1, 2700],
  ])("largura %i com dpr %i usa a família %i", (larguraCss, dpr, esperada) => {
    expect(escolherVariante(larguraCss, dpr)).toBe(esperada);
  });
});
