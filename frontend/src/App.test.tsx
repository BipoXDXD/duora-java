import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Resposta } from "./api/cliente";
import type { Visao } from "./dominio/visao";
import { cenarioDoTerraco } from "./teste/cenarioDoTerraco";
import { stubarFetch } from "./teste/fetchFalso";

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
  abertura: null,
  conviteDaSala: "resumo-da-sala",
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
const { ErroApi } = await import("./api/cliente");

beforeEach(() => {
  // Sem resposta para quem não espiona o fetch: o rodapé da entrada não vai à rede.
  stubarFetch();
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

/**
 * fetch falso que responde 204 a GET /api/conta (obterSala é mock) e as versões legais ao rodapé da
 * entrada.
 */
function espionarFetch() {
  return stubarFetch(() => Promise.resolve(new Response(null, { status: 204 })));
}

describe("App", () => {
  it("durante o carregando não há link da conta nem consulta da conta", async () => {
    const fetchFalso = espionarFetch();
    obterSala.mockReturnValue(new Promise<Resposta>(() => {}));
    render(<App convite={null} cenario={cenarioDoTerraco()} />);

    await waitFor(() => {
      expect(obterSala).toHaveBeenCalled();
    });
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(fetchFalso).not.toHaveBeenCalled();
  });

  it("depois do carregando, sem sala, a entrada mostra o link da conta", async () => {
    const fetchFalso = espionarFetch();
    obterSala.mockRejectedValue(new ErroApi({ status: 401, codigo: "SEM_ASSENTO" }));
    render(<App convite={null} cenario={cenarioDoTerraco()} />);

    expect(await screen.findByRole("link", { name: "Entrar" })).toHaveAttribute("href", "/entrar");
    expect(fetchFalso.mock.calls[0]?.[0]).toBe("/api/conta");
  });

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
