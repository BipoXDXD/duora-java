import { focusManager, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Cadastro } from "./Cadastro";

const NASCIMENTO = "2000-01-31";
const VERSOES = {
  termos: { versao: "2026-11-23" },
  privacidade: { versao: "2026-11-20" },
  contato: "support@meetduora.com",
  encarregado: { nome: "o responsável pelo Duora", email: "encarregado@exemplo.invalid" },
};
const NOVAS_VERSOES = { ...VERSOES, termos: { versao: "2026-12-01" } };
const CONCLUIDO = { status: 200, corpo: { cadastroConcluido: false, proximoPasso: "VERIFICACAO" } };
/** No lugar das versões: a leitura responde 503. */
const FALHA = Symbol("falha");
const RECUSA_POR_IDADE = "O Duora é só para maiores de 18 anos. Não criamos a conta.";

interface RespostaFalsa {
  status: number;
  corpo?: unknown;
  cabecalhos?: Record<string, string>;
}

function json({ status, corpo, cabecalhos = {} }: RespostaFalsa): Response {
  const tipo = status >= 400 ? "application/problem+json" : "application/json";
  return new Response(JSON.stringify(corpo ?? null), { status, headers: { "Content-Type": tipo, ...cabecalhos } });
}

function erro(status: number, codigo: string, mensagem: string, cabecalhos?: Record<string, string>): RespostaFalsa {
  return { status, corpo: { codigo, mensagem }, cabecalhos };
}

/** Responde as versões legais (uma lista por chamada, a última se repete) e o POST do cadastro. */
function prepararFetch(cadastro: RespostaFalsa = CONCLUIDO, versoes: unknown[] = [VERSOES]) {
  const corposDoCadastro: unknown[] = [];
  let leiturasDasVersoes = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      const url = typeof entrada === "string" ? entrada : "";
      if (url === "/api/legal/versoes" && opcoes?.method === "GET") {
        // Uma promessa no lugar das versões segura a resposta até o teste resolvê-la.
        const corpo = versoes[Math.min(leiturasDasVersoes, versoes.length - 1)];
        leiturasDasVersoes += 1;
        return Promise.resolve(corpo).then((lido) =>
          lido === FALHA ? json({ status: 503 }) : json({ status: 200, corpo: lido }),
        );
      }
      if (url === "/api/conta/cadastro" && opcoes?.method === "POST") {
        corposDoCadastro.push(typeof opcoes.body === "string" ? JSON.parse(opcoes.body) : null);
        return Promise.resolve(json(cadastro));
      }
      return Promise.reject(new Error(`pedido inesperado: ${url}`));
    }),
  );
  return {
    corposDoCadastro,
    leiturasDasVersoes: () => leiturasDasVersoes,
  };
}

function renderizar() {
  const navegarParaPagina = vi.fn<(url: string) => void>();
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  cliente.setQueryData(["conta"], { estado: "PENDENTE", cadastroConcluido: false, verificada: false });
  const roteador = createMemoryRouter(
    [
      { path: "/cadastro", element: <Cadastro navegarParaPagina={navegarParaPagina} /> },
      { path: "/entrar", element: <h1>Tela de entrar</h1> },
    ],
    { initialEntries: ["/cadastro"] },
  );
  render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
  return { cliente, roteador, navegarParaPagina };
}

async function campoDaData(): Promise<HTMLInputElement> {
  return screen.findByLabelText<HTMLInputElement>("Data de nascimento");
}

function caixaDoAceite(): HTMLInputElement {
  return screen.getByRole("checkbox", { name: "Li e aceito os Termos de uso e a Política de privacidade" });
}

function botaoConcluir(): HTMLElement {
  return screen.getByRole("button", { name: "Concluir cadastro" });
}

async function preencherEConcluir(): Promise<void> {
  const usuario = userEvent.setup();
  fireEvent.change(await campoDaData(), { target: { value: NASCIMENTO } });
  await usuario.click(caixaDoAceite());
  await usuario.click(botaoConcluir());
}

afterEach(() => {
  focusManager.setFocused(undefined);
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("Cadastro", () => {
  it("mostraOsTextosDoContrato", async () => {
    prepararFetch();
    renderizar();

    expect(await screen.findByRole("heading", { level: 1, name: "Falta pouco" })).toBeInTheDocument();
    expect(await campoDaData()).toHaveAttribute("type", "date");
    expect(await campoDaData()).toHaveAccessibleDescription("Não dá para mudar depois.");
    expect(screen.getByText(/^Leia os/)).toHaveTextContent(/^Leia os Termos de uso.* e a Política de privacidade.*\.$/);
    expect(botaoConcluir()).not.toHaveAttribute("aria-disabled");
  });

  it("dataNaoPassaDeHojeEmSaoPaulo", async () => {
    // 23h30 de 27/11 em São Paulo; em UTC já é dia 28.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-28T02:30:00Z"));
    prepararFetch();
    renderizar();

    expect(await campoDaData()).toHaveAttribute("max", "2026-11-27");
  });

  it("enviaAsVersoesDoServidor", async () => {
    const { corposDoCadastro } = prepararFetch();
    renderizar();

    await preencherEConcluir();

    await waitFor(() => {
      expect(corposDoCadastro).toEqual([
        { dataDeNascimento: NASCIMENTO, versaoTermos: "2026-11-23", versaoPrivacidade: "2026-11-20", aceito: true },
      ]);
    });
  });

  it("semDataNaoEnvia", async () => {
    const usuario = userEvent.setup();
    const { corposDoCadastro } = prepararFetch();
    renderizar();
    await campoDaData();

    await usuario.click(caixaDoAceite());
    await usuario.click(botaoConcluir());

    expect(screen.getByRole("alert")).toHaveTextContent("Informe sua data de nascimento.");
    expect(await campoDaData()).toHaveFocus();
    expect(await campoDaData()).toHaveAttribute("aria-invalid", "true");
    expect(caixaDoAceite()).not.toHaveAttribute("aria-describedby");
    expect(corposDoCadastro).toEqual([]);
  });

  it("semAceiteNaoEnvia", async () => {
    const usuario = userEvent.setup();
    const { corposDoCadastro } = prepararFetch();
    renderizar();

    fireEvent.change(await campoDaData(), { target: { value: NASCIMENTO } });
    await usuario.click(botaoConcluir());

    expect(screen.getByRole("alert")).toHaveTextContent("Marque a caixa para aceitar os termos.");
    expect(caixaDoAceite()).toHaveFocus();
    expect(caixaDoAceite()).toHaveAccessibleDescription("Marque a caixa para aceitar os termos.");
    expect(await campoDaData()).toHaveAccessibleDescription("Não dá para mudar depois.");
    expect(corposDoCadastro).toEqual([]);
  });

  it("duploToqueEnviaUmaVez", async () => {
    const usuario = userEvent.setup();
    const { corposDoCadastro } = prepararFetch();
    const { navegarParaPagina } = renderizar();
    fireEvent.change(await campoDaData(), { target: { value: NASCIMENTO } });
    await usuario.click(caixaDoAceite());

    // Dois envios no mesmo tique, antes de o isPending chegar ao render.
    const formulario = botaoConcluir().closest("form");
    if (formulario === null) {
      throw new Error("sem formulário");
    }
    fireEvent.submit(formulario);
    fireEvent.submit(formulario);

    await vi.waitFor(() => {
      expect(navegarParaPagina).toHaveBeenCalledExactlyOnceWith("/verificacao");
    });
    expect(corposDoCadastro).toHaveLength(1);
  });

  it("cadastroConcluidoAbreAVerificacaoPorPaginaInteira", async () => {
    prepararFetch();
    const { roteador, navegarParaPagina } = renderizar();

    await preencherEConcluir();

    await vi.waitFor(() => {
      expect(navegarParaPagina).toHaveBeenCalledExactlyOnceWith("/verificacao");
    });
    expect(roteador.state.location.pathname).toBe("/cadastro");
  });

  it("menorMostraRecusaENaoFicaLogado", async () => {
    prepararFetch(erro(422, "MENOR_DE_IDADE", "O Duora é só para maiores de 18 anos."));
    const { cliente, roteador } = renderizar();

    await preencherEConcluir();

    const recusa = await screen.findByText(RECUSA_POR_IDADE);
    expect(screen.getByRole("heading", { level: 1, name: "Cadastro não concluído" })).toBeInTheDocument();
    expect(recusa).toHaveFocus();
    expect(screen.getByRole("link", { name: "Voltar ao terraço" })).toHaveAttribute("href", "/");
    expect(screen.queryByRole("button", { name: "Concluir cadastro" })).not.toBeInTheDocument();
    expect(cliente.getQueryData(["conta"])).toBeUndefined();
    expect(roteador.state.location.pathname).toBe("/cadastro");
  });

  it("naoAutenticadoLevaAEntrar", async () => {
    const { corposDoCadastro } = prepararFetch(erro(401, "NAO_AUTENTICADO", "Entre para continuar."));
    const { cliente, roteador } = renderizar();

    await preencherEConcluir();

    expect(await screen.findByRole("heading", { name: "Tela de entrar" })).toBeInTheDocument();
    expect(roteador.state.location.pathname).toBe("/entrar");
    expect(corposDoCadastro).toHaveLength(1);
    expect(cliente.getQueryData(["conta"])).toBeUndefined();
  });

  it("cadastroJaConcluidoAbreAVerificacaoPorPaginaInteira", async () => {
    prepararFetch(erro(409, "CADASTRO_JA_CONCLUIDO", "O cadastro já foi concluído."));
    const { navegarParaPagina } = renderizar();

    await preencherEConcluir();

    await vi.waitFor(() => {
      expect(navegarParaPagina).toHaveBeenCalledExactlyOnceWith("/verificacao");
    });
  });

  it("dataRecusadaVoltaAoCampo", async () => {
    prepararFetch(erro(422, "DATA_INVALIDA", "Confira a data de nascimento."));
    renderizar();

    await preencherEConcluir();

    expect(await screen.findByRole("alert")).toHaveTextContent("Confira a data de nascimento.");
    expect(await campoDaData()).toHaveFocus();
    expect(await campoDaData()).toHaveAccessibleDescription("Não dá para mudar depois. Confira a data de nascimento.");
  });

  it("termosDesatualizadosPedemNovoAceite", async () => {
    const mensagem = "Os termos mudaram. Recarregue a página e leia a versão nova.";
    const { leiturasDasVersoes } = prepararFetch(erro(422, "TERMOS_DESATUALIZADOS", mensagem), [
      VERSOES,
      NOVAS_VERSOES,
    ]);
    renderizar();

    await preencherEConcluir();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(mensagem);
    });
    await waitFor(() => {
      expect(leiturasDasVersoes()).toBe(2);
    });
    expect(caixaDoAceite()).not.toBeChecked();
    expect(caixaDoAceite()).toHaveFocus();
  });

  it("novoAceiteLevaAVersaoNovaSoDepoisDaLeitura", async () => {
    const usuario = userEvent.setup();
    let liberarVersaoNova = (): void => undefined;
    const versaoNova = new Promise<unknown>((resolver) => {
      liberarVersaoNova = () => {
        resolver(NOVAS_VERSOES);
      };
    });
    const { corposDoCadastro } = prepararFetch(erro(422, "TERMOS_DESATUALIZADOS", "Os termos mudaram."), [
      VERSOES,
      versaoNova,
    ]);
    renderizar();
    await preencherEConcluir();
    await screen.findByText("Os termos mudaram.");

    // Com a versão nova ainda a caminho, o envio espera.
    await usuario.click(caixaDoAceite());
    await usuario.click(botaoConcluir());
    expect(corposDoCadastro).toHaveLength(1);

    liberarVersaoNova();
    await waitFor(() => {
      expect(botaoConcluir()).not.toHaveAttribute("aria-disabled");
    });
    await usuario.click(botaoConcluir());

    await waitFor(() => {
      expect(corposDoCadastro).toHaveLength(2);
    });
    expect(corposDoCadastro[1]).toMatchObject({ versaoTermos: "2026-12-01", aceito: true });
  });

  it("focoDeVoltaNaoReleAsVersoes", async () => {
    // Quem abre os Termos em nova aba e volta não pode aceitar, sem ler, uma versão que chegou nesse meio-tempo.
    const { leiturasDasVersoes } = prepararFetch(CONCLUIDO, [VERSOES, NOVAS_VERSOES]);
    renderizar();
    await campoDaData();

    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });
    await act(() => new Promise((resolver) => setTimeout(resolver, 20)));

    expect(leiturasDasVersoes()).toBe(1);
  });

  it("releituraQueFalhaMantemOFormulario", async () => {
    prepararFetch(CONCLUIDO, [VERSOES, FALHA]);
    const { cliente } = renderizar();
    fireEvent.change(await campoDaData(), { target: { value: NASCIMENTO } });

    await act(() => cliente.refetchQueries({ queryKey: ["legal", "versoes"] }));

    expect(await campoDaData()).toHaveValue(NASCIMENTO);
    expect(botaoConcluir()).toBeInTheDocument();
  });

  it("contaIndisponivelMostraAMensagem", async () => {
    prepararFetch(erro(403, "CONTA_INDISPONIVEL", "Esta conta não está disponível."));
    renderizar();

    await preencherEConcluir();

    expect(await screen.findByText("Esta conta não está disponível.")).toHaveFocus();
    expect(screen.getByRole("heading", { level: 1, name: "Cadastro não concluído" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Concluir cadastro" })).not.toBeInTheDocument();
  });

  it("limiteDeTaxaMostraAEspera", async () => {
    prepararFetch(erro(429, "LIMITE_DE_TAXA", "Muitas tentativas.", { "Retry-After": "90" }));
    renderizar();

    await preencherEConcluir();

    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas. Tente de novo em 2 minutos.");
    expect(botaoConcluir()).not.toHaveAttribute("aria-disabled");
  });

  it("linksDosTermosForaDoRotulo", async () => {
    prepararFetch();
    renderizar();
    await campoDaData();

    for (const [nome, href] of [
      [/^Termos de uso/, "/termos"],
      [/^Política de privacidade/, "/privacidade"],
    ] as const) {
      const link = screen.getByRole("link", { name: nome });
      expect(link).toHaveAttribute("href", href);
      expect(link).toHaveAttribute("target", "_blank");
      expect(link).toHaveAttribute("rel", "noopener noreferrer");
      expect(link.closest("label")).toBeNull();
    }
    expect(caixaDoAceite()).toBeInTheDocument();
  });
});
