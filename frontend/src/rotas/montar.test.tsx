import { onlineManager } from "@tanstack/react-query";
import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ErroHttp } from "../api/http";
import { stubarFetch } from "../teste/fetchFalso";
import { montarCasca, tentarDeNovo } from "./montar";

let raiz: HTMLElement;
let root: Root | null;
let tituloDoIndex: HTMLTitleElement;

beforeEach(() => {
  // O rodapé do layout lê as versões legais; os outros pedidos ficam sem resposta.
  stubarFetch();
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

/** Como o Voltar e o Avançar: muda a URL e avisa o roteador pelo popstate. */
function navegarNoCliente(caminho: string): void {
  act(() => {
    history.pushState(null, "", caminho);
    dispatchEvent(new PopStateEvent("popstate"));
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
    stubarFetch(() =>
      Promise.resolve(
        new Response(JSON.stringify({ chaveDoSiteTurnstile: "1x00000000000000000000AA" }), {
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    await montarEm("/entrar");

    expect(await screen.findByRole("heading", { level: 1, name: "Entrar no Duora" })).toBeInTheDocument();
    expect(document.title).toBe("Entrar · Duora");
  });

  it("tokenSobreviveAoStrictMode", async () => {
    const corpos: unknown[] = [];
    // As versões legais (rodapé do layout) respondem no helper; todo outro pedido entra na lista.
    stubarFetch((_entrada, opcoes) => {
      corpos.push(typeof opcoes?.body === "string" ? JSON.parse(opcoes.body) : null);
      return Promise.resolve(new Response(null, { status: 503 }));
    });

    await montarEm("/entrar/confirmar#t=abc");

    expect(location.hash).toBe("");
    await userEvent.click(await screen.findByRole("button", { name: "Entrar" }));
    await vi.waitFor(() => {
      expect(corpos).toEqual([{ token: "abc" }]);
    });
  });

  it("tokenGastoNaoVoltaAoRemontarATela", async () => {
    await montarEm("/entrar/confirmar#t=abc");
    await screen.findByRole("button", { name: "Entrar" });

    navegarNoCliente("/qualquer");
    await screen.findByRole("heading", { name: "Página não encontrada" });
    navegarNoCliente("/entrar/confirmar");

    expect(await screen.findByText(/Não encontramos o código do link/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Entrar" })).not.toBeInTheDocument();
  });

  describe("rotasQueExigemConta", () => {
    /** Responde o GET da conta com `conta` e o que as telas leem ao abrir. */
    function prepararConta(status: number, corpo?: unknown) {
      const respostas: Record<string, () => Response> = {
        "/api/conta": () =>
          corpo === undefined
            ? new Response(null, { status })
            : new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": "application/json" } }),
        "/api/conta/verificacao": () =>
          new Response(
            JSON.stringify({
              estado: "AGUARDANDO",
              tentativasRestantesHoje: 3,
              enviosDeDocumentoRestantesHoje: 2,
              modo: "falso",
            }),
            { headers: { "Content-Type": "application/json" } },
          ),
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

    it("cadastroLeAsVersoesLegaisUmaVez", async () => {
      prepararConta(200, contaNoEstado("PENDENTE"));

      await montarEm("/cadastro");

      // O rodapé do layout e o formulário do cadastro dividem a mesma consulta.
      expect(await screen.findByRole("link", { name: "Contato" })).toHaveAttribute(
        "href",
        "mailto:support@meetduora.com",
      );
      await screen.findByRole("heading", { level: 1, name: "Falta pouco" });
      const pedidosDasVersoes = vi.mocked(fetch).mock.calls.filter(([entrada]) => entrada === "/api/legal/versoes");
      expect(pedidosDasVersoes).toHaveLength(1);
    });

    it("passaOCaminhoDaPrimeiraCarga", async () => {
      // Aberta como documento próprio, a verificação roda o próprio loader; sem o caminho da carga,
      // o embrulho recarregaria o documento e a tela nunca chegaria.
      prepararConta(200, contaNoEstado("AGUARDANDO_VERIFICACAO"));

      await montarEm("/verificacao");

      expect(await screen.findByRole("heading", { level: 1, name: "Confirme sua idade" })).toBeInTheDocument();
      expect(location.pathname).toBe("/verificacao");
    });

    it.each(["AGUARDANDO_VERIFICACAO", "ATIVA"])("minhaContaAbreParaConta %s", async (estado) => {
      prepararConta(200, contaNoEstado(estado));

      await montarEm("/conta");

      expect(await screen.findByRole("heading", { level: 1, name: "Minha conta" })).toBeInTheDocument();
      expect(document.title).toBe("Minha conta · Duora");
    });

    it("minhaContaDaContaPendenteVaiParaCadastro", async () => {
      prepararConta(200, contaNoEstado("PENDENTE"));

      await montarEm("/conta");

      expect(await screen.findByRole("heading", { level: 1, name: "Falta pouco" })).toBeInTheDocument();
      expect(location.pathname).toBe("/cadastro");
    });

    it("guardaNaoPausaQuandoONavegadorSeDizOffline", async () => {
      prepararConta(200, contaNoEstado("PENDENTE"));
      onlineManager.setOnline(false);
      try {
        await montarEm("/cadastro");

        expect(await screen.findByRole("heading", { level: 1, name: "Falta pouco" })).toBeInTheDocument();
      } finally {
        onlineManager.setOnline(true);
      }
    });

    it("contaIndisponivelMostraAMensagemSemVirarAnonimo", async () => {
      prepararConta(403, { codigo: "CONTA_INDISPONIVEL", mensagem: "Esta conta não está disponível." });

      await montarEm("/cadastro");

      expect(await screen.findByRole("heading", { level: 1, name: "Conta indisponível" })).toBeInTheDocument();
      expect(screen.getByText("Esta conta não está disponível.")).toBeInTheDocument();
      expect(location.pathname).toBe("/cadastro");
    });
  });

  it("termosAbreODocumentoComORodape", async () => {
    stubarFetch((entrada) =>
      entrada === "/api/legal/termos"
        ? Promise.resolve(
            new Response(JSON.stringify({ versao: "2026-11-22", html: "<h1>Termos de uso</h1>\n<p>Texto.</p>\n" }), {
              headers: { "Content-Type": "application/json" },
            }),
          )
        : Promise.reject(new Error("pedido inesperado")),
    );

    await montarEm("/termos");

    expect(await screen.findByText("Versão de 22/11/2026")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Termos de uso" })).toBeInTheDocument();
    expect(screen.getByRole("contentinfo")).not.toBeEmptyDOMElement();
    expect(document.title).toBe("Termos de uso · Duora");
  });

  it.each([
    ["/denunciar", "Denunciar um problema"],
    ["/encarregado", "Encarregado de dados"],
  ])("%s abre a tela pública sem conta", async (caminho, titulo) => {
    await montarEm(caminho);

    expect(await screen.findByRole("heading", { level: 1, name: titulo })).toBeInTheDocument();
    expect(location.pathname).toBe(caminho);
  });

  it("rodapeDaCascaNavegaPeloRoteador", async () => {
    await montarEm("/qualquer");

    await userEvent.click(await screen.findByRole("link", { name: "Termos de uso" }));

    // O jsdom não navega por <a> comum: a troca de tela vem do Link do roteador.
    expect(await screen.findByRole("heading", { level: 1, name: "Termos de uso" })).toBeInTheDocument();
    expect(location.pathname).toBe("/termos");
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
