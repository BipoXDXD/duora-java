import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Conta, EstadoDaConta } from "../../api/estadoDaConta";
import { MinhaConta } from "./MinhaConta";

const PROXIMO_PASSO = "Falta confirmar sua idade. Essa etapa chega em breve.";
const SEM_CONTEUDO = { status: 204 };

interface RespostaFalsa {
  status: number;
  corpo?: unknown;
}

function resposta({ status, corpo }: RespostaFalsa): Response {
  if (corpo === undefined) {
    return new Response(null, { status });
  }
  return new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": "application/problem+json" } });
}

function erro(status: number, codigo: string, mensagem: string): RespostaFalsa {
  return { status, corpo: { codigo, mensagem } };
}

interface Pedido {
  url: string;
  corpo: string | null;
}

/** Responde o sair e a exclusão; guarda cada pedido feito, com o corpo cru. */
function prepararFetch({
  sair = SEM_CONTEUDO,
  exclusao = SEM_CONTEUDO,
}: { sair?: RespostaFalsa; exclusao?: RespostaFalsa } = {}) {
  const pedidos: Pedido[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      const url = typeof entrada === "string" ? entrada : "";
      pedidos.push({ url, corpo: typeof opcoes?.body === "string" ? opcoes.body : null });
      if (url === "/api/conta/sair" && opcoes?.method === "POST") {
        return Promise.resolve(resposta(sair));
      }
      if (url === "/api/conta/exclusao" && opcoes?.method === "POST") {
        return Promise.resolve(resposta(exclusao));
      }
      return Promise.reject(new Error(`pedido inesperado: ${url}`));
    }),
  );
  return pedidos;
}

function renderizar(estado: EstadoDaConta = "ATIVA") {
  const conta: Conta = { estado, cadastroConcluido: estado === "ATIVA", verificada: false };
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  cliente.setQueryData(["conta"], conta);
  const roteador = createMemoryRouter(
    [
      { path: "/conta", loader: () => conta, Component: MinhaConta },
      { path: "/entrar", element: <h1>Tela de entrar</h1> },
    ],
    { initialEntries: ["/conta"] },
  );
  render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
  return { cliente, roteador };
}

async function botaoSair(): Promise<HTMLElement> {
  return screen.findByRole("button", { name: "Sair da conta" });
}

/** O botão da seção, fora do diálogo. */
async function botaoExcluir(): Promise<HTMLElement> {
  const secao = await screen.findByRole("region", { name: "Excluir conta" });
  return within(secao).getByRole("button", { name: "Excluir conta" });
}

async function abrirODialogo() {
  const usuario = userEvent.setup();
  await usuario.click(await botaoExcluir());
  const dialogo = screen.getByRole("dialog", { name: "Excluir sua conta?" });
  return { usuario, dialogo };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("MinhaConta", () => {
  it("mostraOsTextosDoContrato", async () => {
    prepararFetch();
    renderizar();

    expect(await screen.findByRole("heading", { level: 1, name: "Minha conta" })).toBeInTheDocument();
    expect(document.title).toBe("Minha conta · Duora");
    expect(await botaoSair()).toBeInTheDocument();
    const secao = screen.getByRole("region", { name: "Excluir conta" });
    expect(within(secao).getByText("Apaga seus dados pessoais. Não dá para desfazer.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("aguardandoMostraOProximoPasso", async () => {
    prepararFetch();
    renderizar("AGUARDANDO_VERIFICACAO");

    expect(await screen.findByText(PROXIMO_PASSO)).toBeInTheDocument();
  });

  it("ativaNaoMostraOProximoPasso", async () => {
    prepararFetch();
    renderizar("ATIVA");

    await screen.findByRole("heading", { level: 1, name: "Minha conta" });
    expect(screen.queryByText(PROXIMO_PASSO)).not.toBeInTheDocument();
  });

  it("naoMostraSelo", async () => {
    prepararFetch();
    renderizar("ATIVA");

    await screen.findByRole("heading", { level: 1, name: "Minha conta" });
    expect(screen.queryByText(/verificad/i)).not.toBeInTheDocument();
  });

  it("sairChamaAApiEVaiParaEntrar", async () => {
    const pedidos = prepararFetch();
    const { cliente, roteador } = renderizar();

    await userEvent.click(await botaoSair());

    expect(await screen.findByRole("heading", { name: "Tela de entrar" })).toBeInTheDocument();
    expect(pedidos.map(({ url }) => url)).toEqual(["/api/conta/sair"]);
    expect(cliente.getQueryData(["conta"])).toBeUndefined();
    expect(roteador.state.historyAction).toBe("REPLACE");
  });

  it("duploToqueEmSairEnviaUmaVez", async () => {
    const pedidos = prepararFetch();
    renderizar();
    const botao = await botaoSair();

    // Dois toques no mesmo tique, antes de o isPending chegar ao render.
    fireEvent.click(botao);
    fireEvent.click(botao);

    await screen.findByRole("heading", { name: "Tela de entrar" });
    expect(pedidos).toHaveLength(1);
  });

  it("sessaoJaEncerradaAoSairVaiParaEntrar", async () => {
    prepararFetch({ sair: erro(401, "NAO_AUTENTICADO", "Entre para continuar.") });
    const { cliente } = renderizar();

    await userEvent.click(await botaoSair());

    expect(await screen.findByRole("heading", { name: "Tela de entrar" })).toBeInTheDocument();
    expect(cliente.getQueryData(["conta"])).toBeUndefined();
  });

  it("falhaAoSairAvisaEDeixaTentarDeNovo", async () => {
    const pedidos = prepararFetch({ sair: { status: 503 } });
    renderizar();
    const botao = await botaoSair();

    await userEvent.click(botao);

    expect(await screen.findByText("Não conseguimos encerrar a sessão. Tente de novo em instantes.")).toHaveAttribute(
      "role",
      "alert",
    );
    expect(botao).not.toHaveAttribute("aria-disabled");
    await userEvent.click(botao);
    expect(pedidos).toHaveLength(2);
  });

  it("excluirAbreODialogoSemChamarAApi", async () => {
    const pedidos = prepararFetch();
    renderizar();

    const { dialogo } = await abrirODialogo();

    expect(dialogo).toHaveAccessibleDescription("Apagamos seus dados pessoais agora. Não dá para desfazer.");
    expect(within(dialogo).getByRole("button", { name: "Excluir conta" })).toBeInTheDocument();
    expect(within(dialogo).getByRole("button", { name: "Cancelar" })).toBeInTheDocument();
    expect(pedidos).toEqual([]);
  });

  it("cancelarNaoExclui", async () => {
    const pedidos = prepararFetch();
    renderizar();
    const { usuario, dialogo } = await abrirODialogo();

    await usuario.click(within(dialogo).getByRole("button", { name: "Cancelar" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Minha conta" })).toBeInTheDocument();
    expect(pedidos).toEqual([]);
  });

  it("confirmarEnviaConfirmo", async () => {
    const pedidos = prepararFetch();
    const { cliente } = renderizar();
    const { usuario, dialogo } = await abrirODialogo();

    await usuario.click(within(dialogo).getByRole("button", { name: "Excluir conta" }));

    const titulo = await screen.findByRole("heading", { level: 1, name: "Conta excluída" });
    expect(pedidos).toEqual([{ url: "/api/conta/exclusao", corpo: '{"confirmo":true}' }]);
    expect(titulo).toHaveFocus();
    expect(document.title).toBe("Conta excluída · Duora");
    expect(screen.getByRole("link", { name: "Voltar ao terraço" })).toHaveAttribute("href", "/");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(cliente.getQueryData(["conta"])).toBeUndefined();
  });

  it("duploToqueNaConfirmacaoEnviaUmaVez", async () => {
    const pedidos = prepararFetch();
    renderizar();
    const { dialogo } = await abrirODialogo();
    const acao = within(dialogo).getByRole("button", { name: "Excluir conta" });

    fireEvent.click(acao);
    fireEvent.click(acao);

    await screen.findByRole("heading", { level: 1, name: "Conta excluída" });
    expect(pedidos).toHaveLength(1);
  });

  it("falhaNaExclusaoAvisaEVoltaAoBotao", async () => {
    prepararFetch({ exclusao: { status: 503 } });
    renderizar();
    const { usuario, dialogo } = await abrirODialogo();

    await usuario.click(within(dialogo).getByRole("button", { name: "Excluir conta" }));

    const aviso = await screen.findByText("Não conseguimos excluir a conta. Tente de novo em instantes.");
    expect(aviso).toHaveAttribute("role", "alert");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(await botaoExcluir()).toHaveFocus();
    expect(await botaoExcluir()).toHaveAccessibleDescription(
      "Não conseguimos excluir a conta. Tente de novo em instantes.",
    );
  });

  it("contaIndisponivelNaExclusaoMostraAMensagemDoServidor", async () => {
    prepararFetch({ exclusao: erro(403, "CONTA_INDISPONIVEL", "Esta conta está suspensa.") });
    renderizar();
    const { usuario, dialogo } = await abrirODialogo();

    await usuario.click(within(dialogo).getByRole("button", { name: "Excluir conta" }));

    expect(await screen.findByText("Esta conta está suspensa.")).toHaveAttribute("role", "alert");
  });

  it("sessaoJaEncerradaNaExclusaoVaiParaEntrar", async () => {
    prepararFetch({ exclusao: erro(401, "NAO_AUTENTICADO", "Entre para continuar.") });
    const { cliente } = renderizar();
    const { usuario, dialogo } = await abrirODialogo();

    await usuario.click(within(dialogo).getByRole("button", { name: "Excluir conta" }));

    expect(await screen.findByRole("heading", { name: "Tela de entrar" })).toBeInTheDocument();
    expect(cliente.getQueryData(["conta"])).toBeUndefined();
  });
});
