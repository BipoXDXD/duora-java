import { describe, expect, it, vi } from "vitest";
import { jsonsDoTerraco } from "../teste/cenarioDoTerraco";
import { carregarCenario } from "./carregar";
import { ErroCenario } from "./tipos";

/** fetch falso que responde cada URL com o JSON dado, ou 404. */
function buscarFalso(respostas: Readonly<Record<string, unknown>>) {
  return vi.fn<typeof fetch>((entrada) => {
    const corpo = typeof entrada === "string" ? respostas[entrada] : undefined;
    return Promise.resolve(
      corpo === undefined ? new Response(null, { status: 404 }) : new Response(JSON.stringify(corpo)),
    );
  });
}

describe("carregarCenario", () => {
  it("id com barra não busca", async () => {
    const buscar = buscarFalso({});

    await expect(carregarCenario("../x", buscar)).rejects.toThrow(ErroCenario);
    expect(buscar).not.toHaveBeenCalled();
  });

  it("busca os dois JSON do cenário", async () => {
    const { cenario, geometria } = jsonsDoTerraco();
    const buscar = buscarFalso({
      "/assets/cenarios/terraco/cenario.json": cenario,
      "/assets/cenarios/terraco/cena/cena.json": geometria,
    });

    const c = await carregarCenario("terraco", buscar);

    expect(c.id).toBe("terraco");
    expect(buscar).toHaveBeenCalledWith("/assets/cenarios/terraco/cenario.json");
    expect(buscar).toHaveBeenCalledWith("/assets/cenarios/terraco/cena/cena.json");
  });

  it("404 vira ErroCenario com id e status", async () => {
    const buscar = buscarFalso({});

    const erro: unknown = await carregarCenario("terraco", buscar).catch((e: unknown) => e);

    expect(erro).toBeInstanceOf(ErroCenario);
    expect((erro as ErroCenario).message).toContain("terraco");
    expect((erro as ErroCenario).message).toContain("404");
  });

  it("JSON malformado vira ErroCenario", async () => {
    const buscar = vi.fn<typeof fetch>(() => Promise.resolve(new Response("{ não é JSON")));

    await expect(carregarCenario("terraco", buscar)).rejects.toThrow(ErroCenario);
  });
});
