import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { criarRotas } from "../../rotas/rotas";
import { stubarFetch } from "../../teste/fetchFalso";

const CONTA = { estado: "AGUARDANDO_VERIFICACAO", cadastroConcluido: false, verificada: false };
const SITUACAO = { estado: "AGUARDANDO", tentativasRestantesHoje: 3, enviosDeDocumentoRestantesHoje: 2, modo: "falso" };

function json(status: number, corpo: unknown): Response {
  const tipo = status >= 400 ? "application/problem+json" : "application/json";
  return new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": tipo } });
}

/** Responde a conta e a situação da verificação; as versões legais vêm do stubarFetch. */
function prepararFetch(situacao: Response) {
  stubarFetch((entrada) => {
    if (entrada === "/api/conta") {
      return Promise.resolve(json(200, CONTA));
    }
    if (entrada === "/api/conta/verificacao") {
      return Promise.resolve(situacao);
    }
    return Promise.reject(new Error("pedido inesperado"));
  });
}

/** A casca inteira, aberta como documento próprio da verificação. */
function abrirAVerificacao() {
  const clienteDeConsulta = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rotas = criarRotas({ clienteDeConsulta, tomarTokenDoLink: () => null, caminhoDaCarga: "/verificacao" });
  const roteador = createMemoryRouter(rotas, { initialEntries: ["/verificacao"] });
  render(
    <QueryClientProvider client={clienteDeConsulta}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("VerificacaoPagina", () => {
  it("mostraOTituloEAAba", async () => {
    prepararFetch(json(200, SITUACAO));

    abrirAVerificacao();

    expect(await screen.findByRole("heading", { level: 1, name: "Confirme sua idade" })).toBeInTheDocument();
    expect(document.title).toBe("Confirmar idade · Duora");
  });

  it("falhaNaLeituraVaiParaOErroDaCasca", async () => {
    prepararFetch(json(503, { codigo: "INDISPONIVEL", mensagem: "Fora do ar." }));

    abrirAVerificacao();

    expect(
      await screen.findByRole("heading", { level: 1, name: "Não conseguimos abrir esta página" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Confirme sua idade" })).not.toBeInTheDocument();
  });
});
