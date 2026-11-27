import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { turnstileFalso } from "../../teste/turnstileFalso";
import { DenunciarPublico } from "./DenunciarPublico";

const MENSAGEM = "Recebemos sua denúncia. Se você deixou um e-mail, respondemos por lá.";
const DESCRICAO = "Pediu dinheiro adiantado.";
const EMOJI = "😀";

type RespostaFalsa = { status: number; corpo?: unknown; cabecalhos?: Record<string, string> };

function json(status: number, corpo: unknown, cabecalhos: Record<string, string> = {}): Response {
  const tipo = status >= 400 ? "application/problem+json" : "application/json";
  return new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": tipo, ...cabecalhos } });
}

/** Responde a configuração e, a cada denúncia, a próxima resposta da fila (202 quando acaba). */
function prepararFetch(respostas: RespostaFalsa[]): unknown[] {
  const corpos: unknown[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      if (entrada === "/api/conta/configuracao") {
        return Promise.resolve(json(200, { chaveDoSiteTurnstile: "1x00000000000000000000AA" }));
      }
      if (entrada === "/api/denuncias-publicas") {
        corpos.push(typeof opcoes?.body === "string" ? JSON.parse(opcoes.body) : null);
        const proxima = respostas.shift() ?? { status: 202, corpo: { mensagem: MENSAGEM } };
        return Promise.resolve(json(proxima.status, proxima.corpo, proxima.cabecalhos));
      }
      return Promise.reject(new Error(`pedido inesperado: ${typeof entrada === "string" ? entrada : ""}`));
    }),
  );
  return corpos;
}

async function renderizar(respostas: RespostaFalsa[] = []) {
  const corpos = prepararFetch(respostas);
  const widget = turnstileFalso();
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={cliente}>
      <DenunciarPublico carregarTurnstile={widget.carregar} />
    </QueryClientProvider>,
  );
  await vi.waitFor(() => {
    expect(widget.desenhar).toHaveBeenCalled();
  });
  return { corpos, widget };
}

function botaoEnviar(): HTMLElement {
  return screen.getByRole("button", { name: "Enviar denúncia" });
}

function campoDaDescricao(): HTMLElement {
  return screen.getByRole("textbox", { name: "Conte o que aconteceu" });
}

/** Troca o texto de uma vez: o user-event digita caractere por caractere, lento para 2000. */
function escreverDescricao(texto: string) {
  fireEvent.change(campoDaDescricao(), { target: { value: texto } });
}

async function escolherMotivo(nome = "Golpe") {
  await userEvent.click(screen.getByRole("radio", { name: nome }));
}

function grupoDosMotivos(): HTMLElement {
  return screen.getByRole("group", { name: "O que aconteceu" });
}

function alertaGeral(): HTMLElement {
  return screen.getByRole("alert");
}

/**
 * O erro fica junto do campo: no mesmo bloco (o do campo, ou o fieldset dos motivos), na descrição
 * acessível dele, e fora do alerta geral.
 */
function esperarErroJuntoDo(campo: HTMLElement, texto: string) {
  const erro = screen.getByText(texto);
  expect(erro.id).not.toBe("");
  expect(campo.getAttribute("aria-describedby")?.split(" ")).toContain(erro.id);
  expect(erro.closest(".campo, fieldset")).toBe(campo.closest(".campo, fieldset"));
  expect(alertaGeral().textContent).toBe("");
}

describe("DenunciarPublico", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("mostraOsTextosDoCanal", async () => {
    await renderizar();

    expect(screen.getByRole("heading", { level: 1, name: "Denunciar um problema" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Use este canal se você não tem conta ou não consegue denunciar pelo app. Em caso de risco imediato, ligue 190.",
      ),
    ).toBeInTheDocument();
    const motivos = screen.getByRole("group", { name: "O que aconteceu" });
    expect(Array.from(motivos.querySelectorAll("label"), (rotulo) => rotulo.textContent)).toEqual([
      "Parece menor de idade",
      "Abuso ou exploração de criança",
      "Imagem íntima sem consentimento",
      "Assédio ou ameaça",
      "Golpe",
      "Outro",
    ]);
    expect(screen.getByRole("textbox", { name: "Apelido ou link, se souber" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Seu e-mail para resposta (opcional)" })).toBeInTheDocument();
    expect(document.title).toBe("Denunciar · Duora");
  });

  it("semTokenOBotaoFicaIndisponivel", async () => {
    const { corpos } = await renderizar();
    await escolherMotivo();
    escreverDescricao(DESCRICAO);

    await userEvent.click(botaoEnviar());

    expect(botaoEnviar()).toHaveAttribute("aria-disabled", "true");
    expect(botaoEnviar()).toHaveAccessibleDescription("Aguarde a verificação de segurança acima.");
    expect(corpos).toHaveLength(0);
  });

  it("motivoObrigatorio", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    escreverDescricao(DESCRICAO);

    await userEvent.click(botaoEnviar());

    esperarErroJuntoDo(grupoDosMotivos(), "Escolha o que aconteceu.");
    expect(screen.getByRole("radio", { name: "Parece menor de idade" })).toHaveFocus();
    expect(grupoDosMotivos()).toHaveAccessibleDescription("Escolha o que aconteceu.");
    for (const motivo of screen.getAllByRole("radio")) {
      expect(motivo).toHaveAttribute("aria-invalid", "true");
    }
    expect(corpos).toHaveLength(0);
  });

  it("descricaoCurtaAvisa", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao("123456789");

    await userEvent.click(botaoEnviar());

    esperarErroJuntoDo(campoDaDescricao(), "Conte o que aconteceu em 10 a 2000 caracteres.");
    expect(campoDaDescricao()).toHaveFocus();
    expect(campoDaDescricao()).toHaveAttribute("aria-invalid", "true");
    expect(corpos).toHaveLength(0);
  });

  it("descricaoContaEspacosDasPontasFora", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao("   123456789   ");

    await userEvent.click(botaoEnviar());

    esperarErroJuntoDo(campoDaDescricao(), "Conte o que aconteceu em 10 a 2000 caracteres.");
    expect(corpos).toHaveLength(0);
  });

  it("separadorDeUnidadeNasPontasSaiComoNoServidor", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(`\u001F${DESCRICAO}\u001F`);
    fireEvent.change(screen.getByRole("textbox", { name: "Apelido ou link, se souber" }), {
      target: { value: "\u001F@golpista\u001F" },
    });

    expect(screen.getByText(`${String(DESCRICAO.length)} de 2000`)).toBeInTheDocument();
    await userEvent.click(botaoEnviar());

    await screen.findByText(MENSAGEM);
    expect(corpos).toEqual([
      { motivo: "GOLPE", descricao: DESCRICAO, referencia: "@golpista", tokenVerificacao: "token-1" },
    ]);
  });

  it("separadorDeUnidadeNaoContaParaOMinimo", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao("\u001F123456789\u001F");

    await userEvent.click(botaoEnviar());

    esperarErroJuntoDo(campoDaDescricao(), "Conte o que aconteceu em 10 a 2000 caracteres.");
    expect(corpos).toHaveLength(0);
  });

  it("contadorContaCaracteresENaoUnidadesDeUtf16", async () => {
    await renderizar();

    escreverDescricao(EMOJI.repeat(3));

    expect(screen.getByText("3 de 2000")).toBeInTheDocument();
  });

  it("descricaoDe2000EmojisVai", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(EMOJI.repeat(2000));

    await userEvent.click(botaoEnviar());

    await screen.findByText(MENSAGEM);
    expect(corpos).toEqual([{ motivo: "GOLPE", descricao: EMOJI.repeat(2000), tokenVerificacao: "token-1" }]);
  });

  it("descricaoDe2001EmojisAvisa", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(EMOJI.repeat(2001));

    await userEvent.click(botaoEnviar());

    expect(screen.getByText("2001 de 2000")).toBeInTheDocument();
    esperarErroJuntoDo(campoDaDescricao(), "Conte o que aconteceu em 10 a 2000 caracteres.");
    expect(corpos).toHaveLength(0);
  });

  it("referenciaAcimaDe200Avisa", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(DESCRICAO);
    const referencia = screen.getByRole("textbox", { name: "Apelido ou link, se souber" });
    fireEvent.change(referencia, { target: { value: EMOJI.repeat(201) } });

    await userEvent.click(botaoEnviar());

    esperarErroJuntoDo(referencia, "Use até 200 caracteres no apelido ou link.");
    expect(referencia).toHaveFocus();
    expect(referencia).toHaveAttribute("aria-invalid", "true");
    expect(corpos).toHaveLength(0);
  });

  it("enviaCamposOpcionaisSoQuandoPreenchidos", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo("Assédio ou ameaça");
    escreverDescricao(`  ${DESCRICAO}  `);
    fireEvent.change(screen.getByRole("textbox", { name: "Apelido ou link, se souber" }), {
      target: { value: "   " },
    });
    fireEvent.change(screen.getByRole("textbox", { name: "Seu e-mail para resposta (opcional)" }), {
      target: { value: " ana@exemplo.com " },
    });

    await userEvent.click(botaoEnviar());

    await screen.findByText(MENSAGEM);
    expect(corpos).toEqual([
      { motivo: "ASSEDIO", descricao: DESCRICAO, emailContato: "ana@exemplo.com", tokenVerificacao: "token-1" },
    ]);
  });

  it("sucessoMostraAMensagemDoServidor", async () => {
    const { widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(DESCRICAO);

    await userEvent.click(botaoEnviar());

    const mensagem = await screen.findByText(MENSAGEM);
    expect(mensagem).toHaveAttribute("role", "status");
    expect(mensagem).toHaveFocus();
    expect(campoDaDescricao()).toHaveValue("");
    expect(screen.getByRole("radio", { name: "Golpe" })).not.toBeChecked();
  });

  it("respostaReiniciaOWidget", async () => {
    const { corpos, widget } = await renderizar([
      { status: 422, corpo: { codigo: "VERIFICACAO_HUMANA_FALHOU", mensagem: "Tente de novo." } },
    ]);
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(DESCRICAO);
    await userEvent.click(botaoEnviar());
    await vi.waitFor(() => {
      expect(widget.reiniciar).toHaveBeenCalledExactlyOnceWith("widget-1");
    });
    expect(alertaGeral()).toHaveTextContent("Tente de novo.");
    expect(botaoEnviar()).toHaveAttribute("aria-disabled", "true");

    widget.resolverDesafio("token-2");
    await userEvent.click(botaoEnviar());

    await screen.findByText(MENSAGEM);
    expect(corpos).toHaveLength(2);
    expect(corpos[1]).toMatchObject({ tokenVerificacao: "token-2" });
    expect(widget.reiniciar).toHaveBeenCalledTimes(2);
  });

  it("recusaDoServidorLevaOFocoAoCampo", async () => {
    const { widget } = await renderizar([
      { status: 422, corpo: { codigo: "DENUNCIA_PUBLICA_INVALIDA", mensagem: "Confira o e-mail para resposta." } },
    ]);
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(DESCRICAO);

    await userEvent.click(botaoEnviar());

    await screen.findByText("Confira o e-mail para resposta.");
    const email = screen.getByRole("textbox", { name: "Seu e-mail para resposta (opcional)" });
    expect(email).toHaveFocus();
    expect(email).toHaveAttribute("aria-invalid", "true");
    expect(email).toHaveAccessibleDescription("Confira o e-mail para resposta.");
    esperarErroJuntoDo(email, "Confira o e-mail para resposta.");
  });

  it("limiteMostraAEspera", async () => {
    const { widget } = await renderizar([
      { status: 429, corpo: { codigo: "LIMITE_DE_TAXA" }, cabecalhos: { "Retry-After": "120" } },
    ]);
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(DESCRICAO);

    await userEvent.click(botaoEnviar());

    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas. Tente de novo em 2 minutos.");
  });

  it("falhaSemRespostaMostraOAviso", async () => {
    const { widget } = await renderizar([{ status: 503, corpo: { codigo: "SERVIDOR" } }]);
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(DESCRICAO);

    await userEvent.click(botaoEnviar());

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Não conseguimos confirmar o envio. Tente de novo em instantes.",
    );
  });

  it("duploToqueEnviaUmaDenuncia", async () => {
    const { corpos, widget } = await renderizar();
    widget.resolverDesafio("token-1");
    await escolherMotivo();
    escreverDescricao(DESCRICAO);

    const botao = botaoEnviar();
    fireEvent.click(botao);
    fireEvent.click(botao);

    await screen.findByText(MENSAGEM);
    expect(corpos).toHaveLength(1);
  });
});
