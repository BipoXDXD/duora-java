import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DocumentoLegalPagina } from "./DocumentoLegalPagina";

interface RespostaFalsa {
  status: number;
  corpo: unknown;
}

/** Responde o GET do documento com as respostas em ordem; a última se repete. */
function prepararFetch(primeira: RespostaFalsa, ...seguintes: RespostaFalsa[]) {
  const respostas = [primeira, ...seguintes];
  let leituras = 0;
  const fetchFalso = vi.fn<typeof fetch>(() => {
    const { status, corpo } = respostas[Math.min(leituras, respostas.length - 1)] ?? primeira;
    leituras += 1;
    const tipo = status >= 400 ? "application/problem+json" : "application/json";
    return Promise.resolve(new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": tipo } }));
  });
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

function renderizar(documento: "termos" | "privacidade") {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={cliente}>
      <DocumentoLegalPagina documento={documento} />
    </QueryClientProvider>,
  );
}

const TERMOS = {
  status: 200,
  corpo: { versao: "2026-11-22", html: "<h1>Termos de uso</h1>\n<h2>O serviço</h2>\n<p>Texto dos termos.</p>\n" },
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("DocumentoLegalPagina", () => {
  it("mostraTituloEVersao", async () => {
    const fetchFalso = prepararFetch(TERMOS);

    renderizar("termos");

    expect(await screen.findByText("Texto dos termos.")).toBeInTheDocument();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: "Termos de uso" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "O serviço" })).toBeInTheDocument();
    expect(screen.getByText("Versão de 22/11/2026")).toBeInTheDocument();
    expect(document.title).toBe("Termos de uso · Duora");
    expect(fetchFalso).toHaveBeenCalledWith("/api/legal/termos", expect.objectContaining({ method: "GET" }));
  });

  it("privacidadeTemOTituloDaPolitica", async () => {
    prepararFetch({
      status: 200,
      corpo: { versao: "2026-11-27", html: "<h1>Política de privacidade</h1>\n<p>Texto da política.</p>\n" },
    });

    renderizar("privacidade");

    expect(await screen.findByText("Texto da política.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Política de privacidade" })).toBeInTheDocument();
    expect(screen.getByText("Versão de 27/11/2026")).toBeInTheDocument();
    expect(document.title).toBe("Política de privacidade · Duora");
  });

  it("documentoInexistenteMostraNaoEncontrada", async () => {
    prepararFetch({ status: 404, corpo: { codigo: "DOCUMENTO_INEXISTENTE", mensagem: "Esse documento não existe." } });

    renderizar("termos");

    expect(await screen.findByRole("heading", { level: 1, name: "Página não encontrada" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Termos de uso" })).not.toBeInTheDocument();
  });

  it("falhaDoServidorDeixaTentarDeNovo", async () => {
    prepararFetch({ status: 503, corpo: null }, TERMOS);

    renderizar("termos");

    expect(
      await screen.findByText("Não conseguimos carregar o documento. Tente de novo em instantes."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByText("Texto dos termos.")).toBeInTheDocument();
  });

  it("enquantoCarregaAvisaSemMostrarVersao", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(() => new Promise<Response>(() => {})),
    );

    renderizar("termos");

    expect(screen.getByRole("heading", { level: 1, name: "Termos de uso" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Carregando o documento…");
    expect(screen.queryByText(/Versão de/)).not.toBeInTheDocument();
  });
});
