import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Conta, EstadoDaConta } from "../../api/estadoDaConta";
import { MinhaConta } from "./MinhaConta";

const FALTA_CONFIRMAR = "Falta confirmar sua idade.";
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
/** A resposta pronta ou, para rede caída ou resposta segura pelo teste, a promessa de cada pedido. */
type Responder = RespostaFalsa | (() => Promise<Response>);

function responderCom(responder: Responder): Promise<Response> {
  return typeof responder === "function" ? responder() : Promise.resolve(resposta(responder));
}

function prepararFetch({
  sair = SEM_CONTEUDO,
  exclusao = SEM_CONTEUDO,
}: { sair?: Responder; exclusao?: Responder } = {}) {
  const pedidos: Pedido[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      const url = typeof entrada === "string" ? entrada : "";
      pedidos.push({ url, corpo: typeof opcoes?.body === "string" ? opcoes.body : null });
      if (url === "/api/conta/sair" && opcoes?.method === "POST") {
        return responderCom(sair);
      }
      if (url === "/api/conta/exclusao" && opcoes?.method === "POST") {
        return responderCom(exclusao);
      }
      return Promise.reject(new Error(`pedido inesperado: ${url}`));
    }),
  );
  return pedidos;
}

function renderizar(estado: EstadoDaConta = "ATIVA", verificada = false) {
  const conta: Conta = { estado, cadastroConcluido: estado === "ATIVA", verificada };
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
  it("contaAtivaTemLinksDoPerfil", async () => {
    prepararFetch();
    renderizar("ATIVA", true);

    const secao = await screen.findByRole("region", { name: "Seu perfil" });
    expect(within(secao).getByRole("heading", { level: 2, name: "Seu perfil" })).toBeInTheDocument();
    expect(within(secao).getByRole("link", { name: "Meu perfil" })).toHaveAttribute("href", "/perfil");
    expect(within(secao).getByRole("link", { name: "Preferências de encontro" })).toHaveAttribute(
      "href",
      "/perfil/preferencias",
    );
    // Antes do Sair, na ordem do documento.
    expect(secao.compareDocumentPosition(await botaoSair()) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("contaAguardandoNaoTemLinksDoPerfil", async () => {
    prepararFetch();
    renderizar("AGUARDANDO_VERIFICACAO");

    expect(await screen.findByText(FALTA_CONFIRMAR)).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Seu perfil" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Meu perfil" })).not.toBeInTheDocument();
  });

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

  it("aguardandoMostraLinkParaConfirmarIdade", async () => {
    prepararFetch();
    renderizar("AGUARDANDO_VERIFICACAO");

    expect(await screen.findByText(FALTA_CONFIRMAR)).toBeInTheDocument();
    // Um <a> comum, não o Link do roteador: a verificação abre num documento próprio.
    const link = screen.getByRole("link", { name: "Confirmar minha idade" });
    expect(link).toHaveAttribute("href", "/verificacao");
    expect(link).not.toHaveAttribute("data-discover");
  });

  it("ativaNaoMostraOLinkParaConfirmarIdade", async () => {
    prepararFetch();
    renderizar("ATIVA", true);

    await screen.findByRole("heading", { level: 1, name: "Minha conta" });
    expect(screen.queryByText(FALTA_CONFIRMAR)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Confirmar minha idade" })).not.toBeInTheDocument();
  });

  it("verificadaMostraOSelo", async () => {
    prepararFetch();
    renderizar("ATIVA", true);

    expect(await screen.findByText("Conta verificada")).toBeInTheDocument();
    expect(screen.getByText("Confirmamos que você tem 18 anos ou mais.")).toBeInTheDocument();
  });

  it("naoVerificadaNaoMostraOSelo", async () => {
    prepararFetch();
    renderizar("AGUARDANDO_VERIFICACAO");

    await screen.findByRole("heading", { level: 1, name: "Minha conta" });
    expect(screen.queryByText("Conta verificada")).not.toBeInTheDocument();
    expect(screen.queryByText("Confirmamos que você tem 18 anos ou mais.")).not.toBeInTheDocument();
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

  it("falhaDeRedeNaExclusaoAvisaEDestrava", async () => {
    let tentativas = 0;
    const pedidos = prepararFetch({
      exclusao: () => {
        tentativas += 1;
        return tentativas === 1
          ? Promise.reject(new TypeError("Failed to fetch"))
          : Promise.resolve(resposta(SEM_CONTEUDO));
      },
    });
    renderizar();
    const { usuario, dialogo } = await abrirODialogo();
    await usuario.click(within(dialogo).getByRole("button", { name: "Excluir conta" }));
    expect(await screen.findByText("Não conseguimos excluir a conta. Tente de novo em instantes.")).toHaveAttribute(
      "role",
      "alert",
    );

    const segunda = await abrirODialogo();
    await segunda.usuario.click(within(segunda.dialogo).getByRole("button", { name: "Excluir conta" }));

    expect(await screen.findByRole("heading", { level: 1, name: "Conta excluída" })).toBeInTheDocument();
    expect(pedidos).toHaveLength(2);
  });

  /** O Safari e o Firefox do macOS não focam o botão no clique; o foco ainda precisa voltar a ele. */
  it("focoVoltaAoExcluirMesmoSemFocoNoClique", async () => {
    prepararFetch();
    renderizar();
    const botao = await botaoExcluir();

    fireEvent.click(botao);
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancelar" }));

    expect(botao).toHaveFocus();
  });

  it("excluindoMostraOEnvioNoDialogo", async () => {
    prepararFetch({ exclusao: () => new Promise<Response>(() => undefined) });
    renderizar();
    const { usuario, dialogo } = await abrirODialogo();

    await usuario.click(within(dialogo).getByRole("button", { name: "Excluir conta" }));

    expect(await within(dialogo).findByRole("button", { name: "Excluindo…" })).toHaveAttribute("aria-disabled", "true");
    expect(dialogo).toHaveAttribute("aria-busy", "true");
  });

  /** Com o diálogo fechado à força no meio da exclusão, o Sair aparece indisponível e não envia. */
  it("sairFicaIndisponivelComExclusaoPendente", async () => {
    const pedidos = prepararFetch({ exclusao: () => new Promise<Response>(() => undefined) });
    renderizar();
    const { usuario, dialogo } = await abrirODialogo();
    await usuario.click(within(dialogo).getByRole("button", { name: "Excluir conta" }));
    await within(dialogo).findByRole("button", { name: "Excluindo…" });

    act(() => {
      (dialogo as HTMLDialogElement).close();
    });
    const sair = await botaoSair();
    await usuario.click(sair);

    expect(sair).toHaveAttribute("aria-disabled", "true");
    expect(pedidos.map(({ url }) => url)).toEqual(["/api/conta/exclusao"]);
  });
});
