import { render, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Resposta } from "./api/cliente";
import type { Visao } from "./dominio/visao";
import { cenarioDoTerraco } from "./teste/cenarioDoTerraco";

/** Ana no assento B; a manta de Bruno já está no terraço. */
const VISAO: Visao = {
  fase: "MONTANDO",
  versao: 1,
  voce: { assento: "B", nome: "Ana" },
  parceiro: { assento: "A", nome: "Bruno", conectado: true },
  convite: null,
  pecas: [
    { peca: "manta", assento: "A", lugar: "L2" },
    { peca: "banco", assento: "B", lugar: null, lugaresPermitidos: ["C1"], bloqueio: null },
  ],
  prontos: { voce: false, parceiro: false },
  confirmacao: { podeConfirmar: false, faltam: 2 },
  sinal: null,
  resultado: null,
  ajuste: null,
};

const obterSala = vi.fn<(etag: string | null) => Promise<Resposta>>();

vi.mock("./api/cliente", async (original) => {
  const real = await original<typeof import("./api/cliente")>();
  return { ...real, api: { ...real.api, obterSala } };
});

vi.mock("./cena/ceu", async (original) => {
  const real = await original<typeof import("./cena/ceu")>();
  return { ...real, prepararCeuPintado: () => Promise.resolve(false) };
});

const { App } = await import("./App");

beforeEach(() => {
  // O jsdom não implementa matchMedia; a cena só lê prefers-reduced-motion.
  vi.stubGlobal("matchMedia", (consulta: string) => ({
    matches: false,
    media: consulta,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }));
});

afterEach(() => {
  obterSala.mockReset();
  vi.unstubAllGlobals();
});

describe("App", () => {
  it("erro fora da API esvazia #pecas e #lugares, como o cena.limpar() de antes", async () => {
    obterSala
      .mockResolvedValueOnce({ naoMudou: false, visao: VISAO, etag: '"v1"' })
      .mockRejectedValueOnce(new TypeError("quebrou"));
    const { container } = render(<App convite={null} cenario={cenarioDoTerraco()} />);
    const pecas = () => container.querySelector("#pecas");
    await waitFor(() => {
      expect(pecas()?.children.length).toBeGreaterThan(0);
    });

    // A volta à aba consulta a sala na hora; a consulta falha fora da API.
    document.dispatchEvent(new Event("visibilitychange"));

    await waitFor(() => {
      expect(obterSala).toHaveBeenCalledTimes(2);
    });
    await waitFor(() => {
      expect(pecas()?.children).toHaveLength(0);
    });
    expect(container.querySelector("#lugares")?.children).toHaveLength(0);
    // O painel fica parado como estava.
    expect(container.querySelector("#inventario")).not.toBeNull();
  });
});
