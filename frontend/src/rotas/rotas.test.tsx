import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { stubarFetch } from "../teste/fetchFalso";
import { CAMINHOS_DA_CASCA } from "./caminhosDaCasca";
import { criarRotas } from "./rotas";

// O chunk da confirmação não baixa: o import() da rota rejeita, como num deploy novo com a aba velha.
vi.mock("../telas/conta/ConfirmarEntrada", () => {
  throw new TypeError("Failed to fetch dynamically imported module");
});

beforeEach(() => {
  // O rodapé lê as versões legais; os outros pedidos (a conta, nas rotas com guarda) ficam sem resposta.
  stubarFetch();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function abrirEm(caminho: string): void {
  const clienteDeConsulta = new QueryClient();
  const roteador = createMemoryRouter(criarRotas({ clienteDeConsulta, tomarTokenDoLink: () => null }), {
    initialEntries: [caminho],
  });
  render(
    <QueryClientProvider client={clienteDeConsulta}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
}

describe("criarRotas", () => {
  it("caminhosDaCascaSaoOsDasRotas", () => {
    const [raiz] = criarRotas({ clienteDeConsulta: new QueryClient(), tomarTokenDoLink: () => null });
    const caminhos = (raiz?.children ?? []).map((rota) => rota.path).filter((caminho) => caminho !== "*");

    expect(caminhos.map((caminho) => `/${caminho ?? ""}`)).toEqual([...CAMINHOS_DA_CASCA]);
  });

  it("primeiraCargaMostraCarregandoNaMolduraAteATelaChegar", async () => {
    // A leitura da conta, no guarda do /cadastro, nunca responde: a casca fica na primeira carga.
    abrirEm("/cadastro");

    const principal = await screen.findByRole("main");
    expect(within(principal).getByRole("status")).toHaveTextContent("Carregando…");
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
  });

  it("falhaDoChunkDaConfirmacaoMostraOErroDaCascaNaMoldura", async () => {
    abrirEm("/entrar/confirmar");

    const titulo = await screen.findByRole("heading", { level: 1, name: "Não conseguimos abrir esta página" });
    expect(screen.getByRole("main")).toContainElement(titulo);
    expect(screen.getByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
  });
});
