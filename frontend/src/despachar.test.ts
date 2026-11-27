import { afterEach, beforeEach, describe, expect, it, type MockInstance, vi } from "vitest";
import { type DependenciasDoDespacho, despachar, ehCaminhoDoTerraco } from "./despachar";

let raiz: HTMLElement;
let replaceState: MockInstance<History["replaceState"]>;

beforeEach(() => {
  raiz = document.createElement("div");
  raiz.append(document.createElement("header"), document.createElement("section"));
  document.body.append(raiz);
  replaceState = vi.spyOn(history, "replaceState");
});

afterEach(() => {
  raiz.remove();
  vi.restoreAllMocks();
  history.pushState(null, "", "/");
});

/** Abre a URL no jsdom sem passar pelo spy do replaceState. */
function abrir(url: string): void {
  history.pushState(null, "", url);
}

function dependencias(carregarCasca: DependenciasDoDespacho["carregarCasca"] = () => new Promise(() => {})) {
  return {
    iniciarTerraco: vi.fn<DependenciasDoDespacho["iniciarTerraco"]>(),
    carregarCasca: vi.fn(carregarCasca),
  };
}

describe("ehCaminhoDoTerraco", () => {
  it.each(["/", "/index.html"])("%s é o terraço", (caminho) => {
    expect(ehCaminhoDoTerraco(caminho)).toBe(true);
  });

  it.each(["/entrar", "/entrar/confirmar", "/conta", "/qualquer", "/index.htm"])("%s não é o terraço", (caminho) => {
    expect(ehCaminhoDoTerraco(caminho)).toBe(false);
  });
});

describe("despachar", () => {
  it("raizAbreOTerracoComOConvite", () => {
    abrir("/#convite=abc");
    const deps = dependencias();

    despachar(location, history, raiz, deps);

    expect(deps.iniciarTerraco).toHaveBeenCalledWith(raiz, "abc");
    expect(deps.carregarCasca).not.toHaveBeenCalled();
  });

  it("indexHtmlEhTerraco", () => {
    abrir("/index.html");
    const deps = dependencias();

    despachar(location, history, raiz, deps);

    expect(deps.iniciarTerraco).toHaveBeenCalledWith(raiz, null);
    expect(deps.carregarCasca).not.toHaveBeenCalled();
  });

  it("outraRotaCarregaACascaSemTocarNoFragmento", () => {
    abrir("/entrar/confirmar#t=xyz");
    const deps = dependencias();

    despachar(location, history, raiz, deps);

    expect(deps.carregarCasca).toHaveBeenCalledOnce();
    expect(deps.iniciarTerraco).not.toHaveBeenCalled();
    expect(replaceState).not.toHaveBeenCalled();
    expect(location.hash).toBe("#t=xyz");
  });

  it("outraRotaLimpaOEsqueleto", () => {
    abrir("/conta");

    despachar(location, history, raiz, dependencias());

    expect(raiz.childElementCount).toBe(0);
  });

  it("raizFicaOcupadaEnquantoACascaBaixa", () => {
    abrir("/conta");

    despachar(location, history, raiz, dependencias());

    expect(raiz).toHaveAttribute("aria-busy", "true");
  });

  it("terracoNaoMarcaARaizComoOcupada", () => {
    abrir("/");

    despachar(location, history, raiz, dependencias());

    expect(raiz).not.toHaveAttribute("aria-busy");
  });

  it("cascaCarregadaMontaNaRaiz", async () => {
    abrir("/conta");
    const montarCasca = vi.fn();
    const deps = dependencias(() => Promise.resolve({ montarCasca }));

    despachar(location, history, raiz, deps);

    await vi.waitFor(() => {
      expect(montarCasca).toHaveBeenCalledWith(raiz, location, history);
    });
  });

  it("cascaMontadaLiberaARaiz", async () => {
    abrir("/conta");
    const montarCasca = vi.fn();

    despachar(
      location,
      history,
      raiz,
      dependencias(() => Promise.resolve({ montarCasca })),
    );

    await vi.waitFor(() => {
      expect(montarCasca).toHaveBeenCalled();
    });
    expect(raiz).not.toHaveAttribute("aria-busy");
  });

  it("falhaAoBaixarACascaAvisaNaTela", async () => {
    abrir("/conta");
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

    despachar(
      location,
      history,
      raiz,
      dependencias(() => Promise.reject(new TypeError("Failed to fetch"))),
    );

    await vi.waitFor(() => {
      expect(raiz.querySelector('[role="alert"]')).toHaveTextContent(
        "A página não carregou. Confira a conexão e recarregue.",
      );
    });
    expect(raiz).not.toHaveAttribute("aria-busy");
    expect(consoleError).toHaveBeenCalled();
  });
});
