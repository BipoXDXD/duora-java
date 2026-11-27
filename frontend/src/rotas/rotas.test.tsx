import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import {
  createMemoryRouter,
  type LoaderFunctionArgs,
  type RouteObject,
  RouterProvider,
  UNSAFE_createMemoryHistory as createMemoryHistory,
  UNSAFE_createRouter as createRouter,
} from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { stubarFetch } from "../teste/fetchFalso";
import { CAMINHOS_DA_CASCA } from "./caminhosDaCasca";
import { CAMINHO_DA_VERIFICACAO, criarRotas, isolarDocumentoDaVerificacao } from "./rotas";

/** O cabeçalho que o redirectDocument (react-router 7.18.6) põe para o roteador recarregar o documento. */
const RECARGA_DO_DOCUMENTO = "X-Remix-Reload-Document";

// O chunk da confirmação não baixa: o import() da rota rejeita, como num deploy novo com a aba velha.
vi.mock("../telas/conta/ConfirmarEntrada", () => {
  throw new TypeError("Failed to fetch dynamically imported module");
});

beforeEach(() => {
  // O rodapé lê as versões legais; os outros pedidos (a conta, nas rotas com guarda) ficam sem resposta.
  stubarFetch();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function rotasDaCasca(caminhoDaCarga: string): RouteObject[] {
  return criarRotas({ clienteDeConsulta: new QueryClient(), tomarTokenDoLink: () => null, caminhoDaCarga });
}

/** A filha da raiz com este path ("*" para a não encontrada). */
function filha(rotas: RouteObject[], path: string): RouteObject {
  const rota = rotas[0]?.children?.find((candidata) => candidata.path === path);
  if (rota === undefined) {
    throw new Error(`sem a rota ${path}`);
  }
  return rota;
}

/** Roda o loader da rota como o roteador faria ao navegar para `caminho`. */
async function carregar(rota: RouteObject, caminho: string): Promise<unknown> {
  if (typeof rota.loader !== "function") {
    throw new Error(`a rota ${rota.path ?? ""} não tem loader`);
  }
  const url = new URL(caminho, "http://localhost");
  const argumentos = { request: new Request(url), url, pattern: rota.path ?? "", params: {}, context: undefined };
  // Num async, o throw síncrono do loader vira rejeição, como no roteador.
  const resultado: unknown = await rota.loader(argumentos as LoaderFunctionArgs);
  return resultado;
}

/** O redirect lançado pelo loader (uma Response); falha se o loader não redireciona. */
async function redirectDe(promessa: Promise<unknown>): Promise<Response> {
  try {
    await promessa;
  } catch (lancado) {
    if (lancado instanceof Response) {
      return lancado;
    }
    throw lancado;
  }
  throw new Error("esperava um redirect");
}

/** O chunk de uma tela que não baixa (deploy novo com a aba velha, rede caída). */
function chunkQueFalha(): Promise<never> {
  return Promise.reject(new TypeError("Failed to fetch dynamically imported module"));
}

/**
 * Um roteador de dados em `inicial`, com uma janela falsa: o jsdom não deixa espionar
 * location.assign, e é por ela que o roteador recarrega o documento num redirectDocument.
 */
function roteadorComJanela(filhas: RouteObject[], inicial: string) {
  const recarregar = vi.fn<(destino: string) => void>();
  const janela = {
    document: { createElement: () => ({}) },
    location: { origin: "http://localhost", assign: recarregar, replace: recarregar },
    sessionStorage: { getItem: () => null, setItem: () => undefined },
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  };
  const roteador = createRouter({
    routes: [{ path: "/", children: filhas }],
    history: createMemoryHistory({ initialEntries: [inicial] }),
    window: janela as unknown as Window,
  }).initialize();
  return { roteador, recarregar };
}

function abrirEm(caminho: string): void {
  const clienteDeConsulta = new QueryClient();
  const roteador = createMemoryRouter(
    criarRotas({ clienteDeConsulta, tomarTokenDoLink: () => null, caminhoDaCarga: caminho }),
    {
      initialEntries: [caminho],
    },
  );
  render(
    <QueryClientProvider client={clienteDeConsulta}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
}

describe("criarRotas", () => {
  it("caminhosDaCascaSaoOsDasRotas", () => {
    const [raiz] = rotasDaCasca("/entrar");
    const caminhos = (raiz?.children ?? []).map((rota) => rota.path).filter((caminho) => caminho !== "*");

    expect(caminhos.map((caminho) => `/${caminho ?? ""}`)).toEqual([...CAMINHOS_DA_CASCA]);
  });

  it("primeiraCargaMostraCarregandoNaMolduraAteATelaChegar", async () => {
    // A leitura da conta, no guarda do /cadastro, nunca responde: a casca fica na primeira carga.
    abrirEm("/cadastro");

    const principal = await screen.findByRole("main");
    expect(within(principal).getByRole("status")).toHaveTextContent("Carregando…");
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
  });

  it("falhaDoChunkDaConfirmacaoMostraOErroDaCascaNaMoldura", async () => {
    abrirEm("/entrar/confirmar");

    const titulo = await screen.findByRole("heading", { level: 1, name: "Não conseguimos abrir esta página" });
    expect(screen.getByRole("main")).toContainElement(titulo);
    expect(screen.getByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
  });
});

describe("isolarDocumentoDaVerificacao", () => {
  it("verificacaoAbertaPeloRoteadorRecarregaODocumento", async () => {
    const resposta = await redirectDe(carregar(filha(rotasDaCasca("/conta"), "verificacao"), "/verificacao"));

    expect(resposta.status).toBe(302);
    expect(resposta.headers.get("Location")).toBe("/verificacao");
    expect(resposta.headers.get(RECARGA_DO_DOCUMENTO)).toBe("true");
  });

  it("saidaDaVerificacaoPeloRoteadorRecarregaODocumento", async () => {
    const resposta = await redirectDe(carregar(filha(rotasDaCasca(CAMINHO_DA_VERIFICACAO), "termos"), "/termos?x=1"));

    expect(resposta.status).toBe(302);
    expect(resposta.headers.get("Location")).toBe("/termos?x=1");
    expect(resposta.headers.get(RECARGA_DO_DOCUMENTO)).toBe("true");
  });

  it("rotaSemLoaderTambemSaiPorDocumento", async () => {
    const resposta = await redirectDe(carregar(filha(rotasDaCasca(CAMINHO_DA_VERIFICACAO), "*"), "/qualquer/coisa"));

    expect(resposta.headers.get("Location")).toBe("/qualquer/coisa");
    expect(resposta.headers.get(RECARGA_DO_DOCUMENTO)).toBe("true");
  });

  it("recargaSaiMesmoComOChunkDoDestinoFalhando", async () => {
    const filhas = isolarDocumentoDaVerificacao(
      [{ path: "conta" }, { path: "verificacao", lazy: chunkQueFalha }],
      "/conta",
    );
    const { roteador, recarregar } = roteadorComJanela(filhas, "/conta");

    await roteador.navigate("/verificacao");

    expect(recarregar).toHaveBeenCalledExactlyOnceWith("/verificacao");
    expect(roteador.state.errors).toBeNull();
    roteador.dispose();
  });

  it("rotaTrocadaSoTemPathELoader", () => {
    const [verificacao] = isolarDocumentoDaVerificacao(
      [{ path: "verificacao", id: "v", loader: () => null, lazy: chunkQueFalha, shouldRevalidate: () => true }],
      "/conta",
    );

    expect(Object.keys(verificacao ?? {}).sort()).toEqual(["loader", "path"]);
  });

  it.each(["conta", "cadastro"])("%sNoDocumentoDaVerificacaoNaoLeAConta", async (path) => {
    const fetchFalso = stubarFetch();

    const resposta = await redirectDe(carregar(filha(rotasDaCasca(CAMINHO_DA_VERIFICACAO), path), `/${path}`));

    expect(resposta.headers.get(RECARGA_DO_DOCUMENTO)).toBe("true");
    expect(fetchFalso.mock.calls.map(([entrada]) => entrada)).not.toContain("/api/conta");
  });

  it("verificacaoNoDocumentoProprioChamaOLoaderOriginal", async () => {
    const original = vi.fn(() => "dados da verificação");
    const [verificacao] = isolarDocumentoDaVerificacao([{ path: "verificacao", loader: original }], "/verificacao");

    expect(await carregar(verificacao ?? {}, "/verificacao")).toBe("dados da verificação");
    expect(original).toHaveBeenCalledOnce();
  });

  it("foraDaVerificacaoNadaMuda", () => {
    const filhas: RouteObject[] = [{ path: "conta", loader: () => null }, { path: "termos" }, { path: "*" }];

    const isoladas = isolarDocumentoDaVerificacao(filhas, "/conta");

    expect(isoladas).toHaveLength(filhas.length);
    isoladas.forEach((rota, indice) => {
      expect(rota).toBe(filhas[indice]);
    });
  });
});
