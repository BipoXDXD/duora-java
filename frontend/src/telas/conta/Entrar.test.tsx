import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiTurnstile, OpcoesDoWidget } from "../../componentes/Turnstile";
import { Entrar } from "./Entrar";

const MENSAGEM = "Se o e-mail puder entrar, o link chega em instantes.";
const EMAIL = "ana@exemplo.com";

type RespostaFalsa = { status: number; corpo?: unknown; cabecalhos?: Record<string, string> } | "rede";

interface PedidoDeEntrada {
  corpo: unknown;
  chave: string | null;
}

function json(status: number, corpo: unknown, cabecalhos: Record<string, string> = {}): Response {
  const tipo = status >= 400 ? "application/problem+json" : "application/json";
  return new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": tipo, ...cabecalhos } });
}

/**
 * Responde a configuração (503 nas primeiras `falhasDaConfiguracao` vezes) e, a cada POST de entrada,
 * a próxima resposta da fila (202 quando acaba).
 */
function prepararFetch(respostas: RespostaFalsa[], falhasDaConfiguracao = 0): PedidoDeEntrada[] {
  const pedidos: PedidoDeEntrada[] = [];
  let configuracoesPedidas = 0;
  const fetchFalso = vi.fn<typeof fetch>((entrada, opcoes) => {
    const url = typeof entrada === "string" ? entrada : "";
    if (url === "/api/conta/configuracao") {
      configuracoesPedidas += 1;
      return Promise.resolve(
        configuracoesPedidas <= falhasDaConfiguracao
          ? json(503, { codigo: "SERVIDOR" })
          : json(200, { chaveDoSiteTurnstile: "1x00000000000000000000AA" }),
      );
    }
    if (url === "/api/conta/entrar") {
      pedidos.push({
        corpo: typeof opcoes?.body === "string" ? JSON.parse(opcoes.body) : null,
        chave: new Headers(opcoes?.headers).get("Idempotency-Key"),
      });
      const proxima = respostas.shift() ?? { status: 202, corpo: { mensagem: MENSAGEM } };
      if (proxima === "rede") {
        return Promise.reject(new TypeError("Failed to fetch"));
      }
      return Promise.resolve(json(proxima.status, proxima.corpo, proxima.cabecalhos));
    }
    return Promise.reject(new Error(`pedido inesperado: ${url}`));
  });
  vi.stubGlobal("fetch", fetchFalso);
  return pedidos;
}

function turnstileFalso(carregou = true) {
  let opcoes: OpcoesDoWidget | null = null;
  const desenhar = vi.fn((_elemento: HTMLElement, recebidas: OpcoesDoWidget) => {
    opcoes = recebidas;
    return "widget-1";
  });
  const reiniciar = vi.fn<(id: string) => void>();
  const api: ApiTurnstile = { render: desenhar, reset: reiniciar, remove: vi.fn() };
  const carregar = vi.fn(() => (carregou ? Promise.resolve(api) : Promise.reject(new Error("bloqueado"))));
  function atuais(): OpcoesDoWidget {
    if (!opcoes) {
      throw new Error("o widget não foi desenhado");
    }
    return opcoes;
  }
  return {
    carregar,
    desenhar,
    reiniciar,
    resolverDesafio(token: string) {
      act(() => {
        atuais().callback(token);
      });
    },
    expirar() {
      act(() => {
        atuais()["expired-callback"]();
      });
    },
  };
}

function montar(widget: ReturnType<typeof turnstileFalso>) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={cliente}>
      <Entrar carregarTurnstile={widget.carregar} />
    </QueryClientProvider>,
  );
}

async function renderizar(respostas: RespostaFalsa[] = [], widget = turnstileFalso()) {
  const pedidos = prepararFetch(respostas);
  montar(widget);
  await vi.waitFor(() => {
    expect(widget.carregar).toHaveBeenCalled();
  });
  return { pedidos, widget };
}

function botaoEnviar(): HTMLElement {
  return screen.getByRole("button", { name: "Enviar link" });
}

/** Desafio resolvido, e-mail digitado e clique em Enviar link. */
async function enviar(widget: ReturnType<typeof turnstileFalso>, token: string, usuario = userEvent.setup()) {
  await vi.waitFor(() => {
    expect(widget.desenhar).toHaveBeenCalled();
  });
  widget.resolverDesafio(token);
  const campo = screen.getByLabelText("Seu e-mail");
  await usuario.clear(campo);
  await usuario.type(campo, EMAIL);
  await usuario.click(botaoEnviar());
}

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("Entrar", () => {
  it("mostraTituloECampoDeEmail", async () => {
    await renderizar();

    expect(screen.getByRole("heading", { level: 1, name: "Entrar no Duora" })).toBeInTheDocument();
    const campo = screen.getByLabelText("Seu e-mail");
    expect(campo).toHaveAttribute("type", "email");
    expect(campo).toHaveAttribute("autocomplete", "email");
  });

  it("enviaEmailTokenEChave", async () => {
    const { pedidos, widget } = await renderizar();

    await enviar(widget, "token-1");

    await vi.waitFor(() => {
      expect(pedidos).toHaveLength(1);
    });
    expect(pedidos[0]?.corpo).toEqual({ email: EMAIL, tokenVerificacao: "token-1" });
    expect(pedidos[0]?.chave).toMatch(/^[A-Za-z0-9-]{8,64}$/);
  });

  it("semTokenBotaoIndisponivelComMotivo", async () => {
    const { pedidos } = await renderizar();
    const usuario = userEvent.setup();
    await usuario.type(screen.getByLabelText("Seu e-mail"), EMAIL);

    const botao = botaoEnviar();
    await usuario.click(botao);

    expect(botao).toHaveAttribute("aria-disabled", "true");
    expect(botao).toHaveAccessibleDescription("Aguarde a verificação de segurança acima.");
    expect(pedidos).toHaveLength(0);
  });

  it("tokenExpiradoVoltaAIndisponivel", async () => {
    const { widget } = await renderizar();
    await vi.waitFor(() => {
      expect(widget.desenhar).toHaveBeenCalled();
    });
    widget.resolverDesafio("token-1");
    expect(botaoEnviar()).not.toHaveAttribute("aria-disabled");

    widget.expirar();

    expect(botaoEnviar()).toHaveAttribute("aria-disabled", "true");
  });

  it.each<[string, RespostaFalsa]>([
    [
      "409 em andamento",
      { status: 409, corpo: { codigo: "REQUISICAO_EM_ANDAMENTO" }, cabecalhos: { "Retry-After": "1" } },
    ],
    ["falha de rede", "rede"],
  ])("repeticaoAutomaticaReusaChaveEToken (%s)", async (_caso, falha) => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"], shouldAdvanceTime: true });
    const { pedidos, widget } = await renderizar([falha, falha]);

    await enviar(widget, "token-1", userEvent.setup({ advanceTimers: vi.advanceTimersByTime }));
    await vi.waitFor(() => {
      expect(pedidos).toHaveLength(1);
    });
    await act(() => vi.advanceTimersByTimeAsync(1000));
    await vi.waitFor(() => {
      expect(pedidos).toHaveLength(2);
    });
    await act(() => vi.advanceTimersByTimeAsync(10_000));

    expect(pedidos).toHaveLength(2);
    expect(pedidos[1]).toEqual(pedidos[0]);
    expect(widget.reiniciar).toHaveBeenCalledOnce();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Não conseguimos confirmar o envio do link. Tente de novo em instantes.",
    );
  });

  it.each<[string, RespostaFalsa]>([
    ["202", { status: 202, corpo: { mensagem: MENSAGEM } }],
    ["422", { status: 422, corpo: { codigo: "EMAIL_INVALIDO", mensagem: "Confira o e-mail." } }],
    ["429", { status: 429, corpo: { codigo: "LIMITE_DE_TAXA" }, cabecalhos: { "Retry-After": "60" } }],
  ])("respostaFinalReiniciaOWidget (%s)", async (_caso, resposta) => {
    const { pedidos, widget } = await renderizar([resposta]);
    await enviar(widget, "token-1");
    await vi.waitFor(() => {
      expect(widget.reiniciar).toHaveBeenCalledExactlyOnceWith("widget-1");
    });
    expect(botaoEnviar()).toHaveAttribute("aria-disabled", "true");

    await enviar(widget, "token-2");

    await vi.waitFor(() => {
      expect(pedidos).toHaveLength(2);
    });
    expect(pedidos[1]?.corpo).toEqual({ email: EMAIL, tokenVerificacao: "token-2" });
    expect(pedidos[1]?.chave).not.toBe(pedidos[0]?.chave);
  });

  it("duploToqueEnviaUmPedido", async () => {
    const { pedidos, widget } = await renderizar();
    await vi.waitFor(() => {
      expect(widget.desenhar).toHaveBeenCalled();
    });
    widget.resolverDesafio("token-1");
    fireEvent.change(screen.getByLabelText("Seu e-mail"), { target: { value: EMAIL } });

    const botao = botaoEnviar();
    fireEvent.click(botao);
    fireEvent.click(botao);

    await screen.findByText(MENSAGEM);
    expect(pedidos).toHaveLength(1);
  });

  it("erroDeEmailLevaOFocoAoCampo", async () => {
    const { widget } = await renderizar([
      { status: 422, corpo: { codigo: "EMAIL_INVALIDO", mensagem: "Confira o e-mail." } },
    ]);

    await enviar(widget, "token-1");

    await screen.findByText("Confira o e-mail.");
    const campo = screen.getByLabelText("Seu e-mail");
    expect(campo).toHaveFocus();
    expect(campo).toHaveAttribute("aria-invalid", "true");
    expect(campo).toHaveAccessibleDescription("Confira o e-mail.");
  });

  it("erroQueNaoEDeEmailNaoDescreveOCampo", async () => {
    const { widget } = await renderizar([
      { status: 429, corpo: { codigo: "LIMITE_DE_TAXA" }, cabecalhos: { "Retry-After": "60" } },
    ]);

    await enviar(widget, "token-1");

    await screen.findByText("Muitas tentativas. Tente de novo em 1 minuto.");
    expect(screen.getByLabelText("Seu e-mail")).not.toHaveAttribute("aria-describedby");
  });

  it("sucessoTiraADicaDaVistaEMantemOMotivo", async () => {
    const { widget } = await renderizar();

    await enviar(widget, "token-1");

    await screen.findByText(MENSAGEM);
    expect(screen.getByText("Aguarde a verificação de segurança acima.")).toHaveClass("sr-only");
    expect(botaoEnviar()).toHaveAccessibleDescription("Aguarde a verificação de segurança acima.");
  });

  it("sucessoMostraAMensagemDoServidor", async () => {
    const { widget } = await renderizar([{ status: 202, corpo: { mensagem: MENSAGEM } }]);

    await enviar(widget, "token-1");

    expect(await screen.findByRole("status")).toHaveTextContent(MENSAGEM);
  });

  it("erro422MostraAMensagemDoServidor", async () => {
    const { widget } = await renderizar([
      { status: 422, corpo: { codigo: "EMAIL_DESCARTAVEL", mensagem: "Use um e-mail permanente." } },
    ]);

    await enviar(widget, "token-1");

    expect(await screen.findByRole("alert")).toHaveTextContent("Use um e-mail permanente.");
  });

  it("limiteMostraOsMinutos", async () => {
    const { widget } = await renderizar([
      { status: 429, corpo: { codigo: "LIMITE_DE_TAXA" }, cabecalhos: { "Retry-After": "61" } },
    ]);

    await enviar(widget, "token-1");

    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas. Tente de novo em 2 minutos.");
  });

  it("falhaDoWidgetOfereceTentarDeNovo", async () => {
    const widget = turnstileFalso(false);
    await renderizar([], widget);

    expect(await screen.findByText("Não conseguimos carregar a verificação de segurança.")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Tentar de novo" }));

    await vi.waitFor(() => {
      expect(widget.carregar).toHaveBeenCalledTimes(2);
    });
  });

  it("falhaDaConfiguracaoOfereceTentarDeNovo", async () => {
    prepararFetch([], 1);
    const widget = turnstileFalso();
    montar(widget);

    expect(await screen.findByText("Não conseguimos carregar a verificação de segurança.")).toBeInTheDocument();
    expect(botaoEnviar()).toHaveAccessibleDescription("Não conseguimos carregar a verificação de segurança.");
    await userEvent.setup().click(screen.getByRole("button", { name: "Tentar de novo" }));

    await vi.waitFor(() => {
      expect(widget.desenhar).toHaveBeenCalledOnce();
    });
  });
});
