import { describe, expect, it } from "vitest";
import { porArtigo } from "./artigos";

describe("porArtigo", () => {
  it.each([
    ["o rádio", "pelo rádio"],
    ["a garrafa térmica", "pela garrafa térmica"],
    ["os bancos", "pelos bancos"],
    ["as mantas", "pelas mantas"],
  ])("contrai %s em %s", (artigo, esperado) => {
    expect(porArtigo(artigo)).toBe(esperado);
  });

  it("sem artigo definido no começo, usa por", () => {
    expect(porArtigo("um telescópio")).toBe("por um telescópio");
  });
});
