import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ConfirmarEntrada } from "./ConfirmarEntrada";

const TOKEN = "abc";
const LINK_INVALIDO = "Este link expirou ou já foi usado. Peça um novo.";
const CONTA_INDISPONIVEL = "Esta conta não está disponível.";

interface RespostaFalsa {
  status: number;
  corpo?: unknown;
  cabecalhos?: Record<string, string>;
}

function json({ status, corpo, cabecalhos = {} }: RespostaFalsa): Response {
  const tipo = status >= 400 ? "application/problem+json" : "application/json";
  return new Response(JSON.stringify(corpo ?? null), { status, headers: { "Content-Type": tipo, ...cabecalhos } });
}

function contaNoEstado(estado: string): RespostaFalsa {
  return { status: 200, corpo: { estado, cadastroConcluido: false, verificada: false } };
}

/** Responde o POST de confirmação e o GET da conta; devolve os corpos dos POSTs e as URLs pedidas. */
function prepararFetch(confirmacao: RespostaFalsa, conta: RespostaFalsa = contaNoEstado("PENDENTE")) {
  const corposDaConfirmacao: unknown[] = [];
  const urls: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      const url = typeof entrada === "string" ? entrada : "";
      urls.push(url);
      if (url === "/api/conta/confirmar" && opcoes?.method === "POST") {
        corposDaConfirmacao.push(typeof opcoes.body === "string" ? JSON.parse(opcoes.body) : null);
        return Promise.resolve(json(confirmacao));
      }
      if (url === "/api/conta" && opcoes?.method === "GET") {
        return Promise.resolve(json(conta));
      }
      return Promise.reject(new Error(`pedido inesperado: ${url}`));
    }),
  );
  return { corposDaConfirmacao, urls };
}

function renderizar(token: string | null = TOKEN) {
  const navegarParaPagina = vi.fn<(url: string) => void>();
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const roteador = createMemoryRouter(
    [
      { path: "/entrar/confirmar", element: <ConfirmarEntrada token={token} navegarParaPagina={navegarParaPagina} /> },
      { path: "/cadastro", element: <h1>Tela de cadastro</h1> },
      { path: "/entrar", element: <h1>Tela de entrar</h1> },
    ],
    { initialEntries: ["/entrar/confirmar"] },
  );
  render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
  return { navegarParaPagina, roteador };
}

function botaoEntrar(): HTMLElement {
  return screen.getByRole("button", { name: "Entrar" });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ConfirmarEntrada", () => {
  it("naoPostaSemClique", async () => {
    const { urls } = prepararFetch({ status: 200, corpo: { cadastroConcluido: true } });
    renderizar();

    expect(await screen.findByRole("heading", { level: 1, name: "Confirmar entrada" })).toBeInTheDocument();
    expect(botaoEntrar()).toBeInTheDocument();
    expect(urls).toEqual([]);
  });

  it("cliqueEnviaOToken", async () => {
    const { corposDaConfirmacao } = prepararFetch({ status: 200, corpo: { cadastroConcluido: true } });
    const { navegarParaPagina } = renderizar();

    await userEvent.click(botaoEntrar());

    await vi.waitFor(() => {
      expect(navegarParaPagina).toHaveBeenCalled();
    });
    expect(corposDaConfirmacao).toEqual([{ token: TOKEN }]);
  });

  it("duploCliqueEnviaUmPost", async () => {
    const { corposDaConfirmacao } = prepararFetch({ status: 200, corpo: { cadastroConcluido: false } });
    renderizar();

    const botao = botaoEntrar();
    fireEvent.click(botao);
    fireEvent.click(botao);

    expect(await screen.findByRole("heading", { name: "Tela de cadastro" })).toBeInTheDocument();
    expect(corposDaConfirmacao).toHaveLength(1);
  });

  it("pendenteVaiParaCadastro", async () => {
    prepararFetch({ status: 200, corpo: { cadastroConcluido: false } }, contaNoEstado("PENDENTE"));
    const { roteador } = renderizar();

    await userEvent.click(botaoEntrar());

    expect(await screen.findByRole("heading", { name: "Tela de cadastro" })).toBeInTheDocument();
    expect(roteador.state.location.pathname).toBe("/cadastro");
  });

  it("aguardandoVerificacaoAbreAVerificacaoPorPaginaInteira", async () => {
    prepararFetch({ status: 200, corpo: { cadastroConcluido: false } }, contaNoEstado("AGUARDANDO_VERIFICACAO"));
    const { navegarParaPagina, roteador } = renderizar();

    await userEvent.click(botaoEntrar());

    await vi.waitFor(() => {
      expect(navegarParaPagina).toHaveBeenCalledExactlyOnceWith("/verificacao");
    });
    expect(roteador.state.location.pathname).toBe("/entrar/confirmar");
  });

  it("contaConcluidaVaiParaOTerraco", async () => {
    const { urls } = prepararFetch({ status: 200, corpo: { cadastroConcluido: true } });
    const { navegarParaPagina } = renderizar();

    await userEvent.click(botaoEntrar());

    await vi.waitFor(() => {
      expect(navegarParaPagina).toHaveBeenCalledWith("/");
    });
    expect(urls).not.toContain("/api/conta");
  });

  it("linkInvalidoOferecePedirNovo", async () => {
    prepararFetch({ status: 422, corpo: { codigo: "LINK_INVALIDO", mensagem: LINK_INVALIDO } });
    renderizar();

    await userEvent.click(botaoEntrar());

    expect(await screen.findByText(LINK_INVALIDO)).toHaveFocus();
    expect(screen.getByRole("link", { name: "Pedir novo link" })).toHaveAttribute("href", "/entrar");
    expect(screen.queryByRole("button", { name: "Entrar" })).not.toBeInTheDocument();
  });

  it("contaIndisponivelMostraAMensagemSemPedirNovo", async () => {
    prepararFetch({ status: 403, corpo: { codigo: "CONTA_INDISPONIVEL", mensagem: CONTA_INDISPONIVEL } });
    renderizar();

    await userEvent.click(botaoEntrar());

    expect(await screen.findByText(CONTA_INDISPONIVEL)).toHaveFocus();
    expect(screen.queryByRole("link", { name: "Pedir novo link" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Entrar" })).not.toBeInTheDocument();
  });

  it("contaIndisponivelNaConsultaSeguinteMostraAMensagem", async () => {
    prepararFetch(
      { status: 200, corpo: { cadastroConcluido: false } },
      { status: 403, corpo: { codigo: "CONTA_INDISPONIVEL", mensagem: CONTA_INDISPONIVEL } },
    );
    const { navegarParaPagina } = renderizar();

    await userEvent.click(botaoEntrar());

    expect(await screen.findByText(CONTA_INDISPONIVEL)).toHaveFocus();
    expect(screen.queryByRole("link", { name: "Pedir novo link" })).not.toBeInTheDocument();
    expect(navegarParaPagina).not.toHaveBeenCalled();
  });

  it("limiteMostraOsMinutosEDeixaTentarDeNovo", async () => {
    prepararFetch({ status: 429, corpo: { codigo: "LIMITE_DE_TAXA" }, cabecalhos: { "Retry-After": "120" } });
    renderizar();

    await userEvent.click(botaoEntrar());

    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas. Tente de novo em 2 minutos.");
    expect(botaoEntrar()).not.toHaveAttribute("aria-disabled");
  });

  it("falhaDoServidorDeixaTentarDeNovo", async () => {
    const { corposDaConfirmacao } = prepararFetch({ status: 503 });
    renderizar();

    await userEvent.click(botaoEntrar());
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Não conseguimos confirmar a entrada. Tente de novo em instantes.",
    );
    await userEvent.click(botaoEntrar());

    await vi.waitFor(() => {
      expect(corposDaConfirmacao).toHaveLength(2);
    });
  });

  it("falhaDepoisDaEntradaOfereceOTerraco", async () => {
    const { corposDaConfirmacao } = prepararFetch(
      { status: 200, corpo: { cadastroConcluido: false } },
      { status: 503 },
    );
    renderizar();

    await userEvent.click(botaoEntrar());

    expect(await screen.findByText("Sua entrada foi confirmada, mas a próxima página não carregou.")).toHaveFocus();
    expect(screen.getByRole("link", { name: "Continuar no terraço" })).toHaveAttribute("href", "/");
    expect(screen.queryByRole("button", { name: "Entrar" })).not.toBeInTheDocument();
    expect(corposDaConfirmacao).toHaveLength(1);
  });

  it("semTokenExplica", async () => {
    const { urls } = prepararFetch({ status: 200, corpo: { cadastroConcluido: true } });
    renderizar(null);

    expect(
      await screen.findByText("Não encontramos o código do link. Abra o link do e-mail de novo ou peça um novo."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Pedir novo link" })).toHaveAttribute("href", "/entrar");
    expect(screen.queryByRole("button", { name: "Entrar" })).not.toBeInTheDocument();
    expect(urls).toEqual([]);
  });
});
