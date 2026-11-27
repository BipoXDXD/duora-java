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
