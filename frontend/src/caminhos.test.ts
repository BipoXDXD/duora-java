import { describe, expect, it } from "vitest";

/** Todo o código do front (sem os testes), como texto. */
const fontes: Record<string, string> = import.meta.glob(["./**/*.{ts,tsx,css}", "!./**/*.test.*"], {
  query: "?raw",
  import: "default",
  eager: true,
});

describe("caminhos dos assets", () => {
  it("nenhum código usa o caminho antigo de asset", () => {
    const antigo = /\/assets\/(cena|ceu|audio)\//;
    const comCaminhoAntigo = Object.entries(fontes)
      .filter(([, conteudo]) => antigo.test(conteudo))
      .map(([arquivo]) => arquivo);

    expect(Object.keys(fontes).length).toBeGreaterThan(0);
    expect(comCaminhoAntigo).toEqual([]);
  });
});
