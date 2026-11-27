import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ErroHttp } from "../api/http";
import { montarCasca, tentarDeNovo } from "./montar";

let raiz: HTMLElement;
let root: Root | null;
let tituloDoIndex: HTMLTitleElement;

beforeEach(() => {
  // O index.html chega com o título do terraço; a casca precisa trocá-lo.
  tituloDoIndex = document.createElement("title");
  tituloDoIndex.textContent = "Antes da primeira estrela";
  document.head.append(tituloDoIndex);
  raiz = document.createElement("div");
  document.body.append(raiz);
  root = null;
});

afterEach(() => {
  act(() => {
    root?.unmount();
  });
  raiz.remove();
  tituloDoIndex.remove();
  history.pushState(null, "", "/");
  vi.unstubAllGlobals();
});

async function montarEm(url: string): Promise<void> {
  history.pushState(null, "", url);
  await act(async () => {
    root = montarCasca(raiz, location, history);
    await Promise.resolve();
  });
}

describe("montarCasca", () => {
  it("rotaDesconhecidaMostraNaoEncontrada", async () => {
    await montarEm("/qualquer/coisa");

    expect(await screen.findByRole("heading", { level: 1, name: "Página não encontrada" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar ao terraço" })).toHaveAttribute("href", "/");
  });

  it("tituloDaAbaSegueATela", async () => {
    await montarEm("/qualquer");

    await screen.findByRole("heading", { name: "Página não encontrada" });
    expect(document.title).toBe("Página não encontrada · Duora");
  });

  it("entrarCarregaATelaSobDemanda", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(() =>
        Promise.resolve(
          new Response(JSON.stringify({ chaveDoSiteTurnstile: "1x00000000000000000000AA" }), {
            headers: { "Content-Type": "application/json" },
          }),
        ),
      ),
    );

    await montarEm("/entrar");

    expect(await screen.findByRole("heading", { level: 1, name: "Entrar no Duora" })).toBeInTheDocument();
    expect(document.title).toBe("Entrar · Duora");
  });

  it("tokenSobreviveAoStrictMode", async () => {
    const corpos: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>((_entrada, opcoes) => {
        corpos.push(typeof opcoes?.body === "string" ? JSON.parse(opcoes.body) : null);
        return Promise.resolve(new Response(null, { status: 503 }));
      }),
    );

    await montarEm("/entrar/confirmar#t=abc");

    expect(location.hash).toBe("");
    await userEvent.click(await screen.findByRole("button", { name: "Entrar" }));
    expect(corpos).toEqual([{ token: "abc" }]);
  });

  describe("rotasQueExigemConta", () => {
    /** Responde o GET da conta com `conta` e o que as telas leem ao abrir. */
    function prepararConta(status: number, corpo?: unknown) {
      const respostas: Record<string, () => Response> = {
        "/api/conta": () =>
          corpo === undefined
            ? new Response(null, { status })
            : new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": "application/json" } }),
        "/api/conta/configuracao": () =>
          new Response(JSON.stringify({ chaveDoSiteTurnstile: "1x00000000000000000000AA" }), {
            headers: { "Content-Type": "application/json" },
          }),
        "/api/legal/versoes": () =>
          new Response(
            JSON.stringify({
              termos: { versao: "2026-11-23" },
              privacidade: { versao: "2026-11-23" },
              contato: "support@meetduora.com",
              encarregado: { nome: "o responsável pelo Duora", email: "encarregado@exemplo.invalid" },
            }),
            { headers: { "Content-Type": "application/json" } },
          ),
      };
      vi.stubGlobal(
        "fetch",
        vi.fn<typeof fetch>((entrada) => {
          const resposta = typeof entrada === "string" ? respostas[entrada] : undefined;
          return resposta ? Promise.resolve(resposta()) : Promise.reject(new Error("pedido inesperado"));
        }),
      );
    }

    function contaNoEstado(estado: string) {
      return { estado, cadastroConcluido: false, verificada: false };
    }

    it("cadastroSemSessaoVaiParaEntrar", async () => {
      prepararConta(204);

      await montarEm("/cadastro");

      expect(await screen.findByRole("heading", { level: 1, name: "Entrar no Duora" })).toBeInTheDocument();
      expect(location.pathname).toBe("/entrar");
    });

    it("cadastroDaContaPendenteAbreATela", async () => {
      prepararConta(200, contaNoEstado("PENDENTE"));

      await montarEm("/cadastro");

      expect(await screen.findByRole("heading", { level: 1, name: "Falta pouco" })).toBeInTheDocument();
      expect(document.title).toBe("Cadastro · Duora");
    });

    it("aguardandoDaContaPendenteVaiParaCadastro", async () => {
      prepararConta(200, contaNoEstado("PENDENTE"));

      await montarEm("/aguardando");

      expect(await screen.findByRole("heading", { level: 1, name: "Falta pouco" })).toBeInTheDocument();
      expect(location.pathname).toBe("/cadastro");
    });

    it("aguardandoAbreParaQuemAguardaVerificacao", async () => {
      prepararConta(200, contaNoEstado("AGUARDANDO_VERIFICACAO"));

      await montarEm("/aguardando");

      expect(await screen.findByRole("heading", { level: 1, name: "Cadastro recebido" })).toBeInTheDocument();
    });

    it("contaIndisponivelMostraAMensagemSemVirarAnonimo", async () => {
      prepararConta(403, { codigo: "CONTA_INDISPONIVEL", mensagem: "Esta conta não está disponível." });

      await montarEm("/cadastro");

      expect(await screen.findByRole("heading", { level: 1, name: "Conta indisponível" })).toBeInTheDocument();
      expect(screen.getByText("Esta conta não está disponível.")).toBeInTheDocument();
      expect(location.pathname).toBe("/cadastro");
    });
  });

  it("telaFicaNoMainDoLayout", async () => {
    await montarEm("/qualquer");

    const principal = await screen.findByRole("main");
    expect(principal).toContainElement(screen.getByRole("heading", { name: "Página não encontrada" }));
  });
});

describe("tentarDeNovo", () => {
  const transitorio = new ErroHttp({ status: 503, codigo: "SERVIDOR" });

  it.each([0, 1])("erro transitório tenta de novo depois de %i falha(s)", (falhas) => {
    expect(tentarDeNovo(falhas, transitorio)).toBe(true);
  });

  it("erro transitório para na terceira falha", () => {
    expect(tentarDeNovo(2, transitorio)).toBe(false);
  });

  it("erro de negócio não tenta de novo", () => {
    expect(tentarDeNovo(0, new ErroHttp({ status: 422, codigo: "EMAIL_INVALIDO" }))).toBe(false);
  });

  it("erro que não é ErroHttp não tenta de novo", () => {
    expect(tentarDeNovo(0, new TypeError("bug"))).toBe(false);
  });
});
