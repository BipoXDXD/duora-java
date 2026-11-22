import { act, screen, waitFor, within } from "@testing-library/react";
import type { Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, type MockInstance, vi } from "vitest";
import { type CenarioFront, ErroCenario } from "./cenario/tipos";
import { iniciarApp } from "./inicio";
import { cenarioDoTerraco } from "./teste/cenarioDoTerraco";

const TEXTO_DO_ERRO = "Não foi possível carregar o cenário. Recarregue a página.";

let raiz: HTMLElement;
/** Raízes montadas pelo iniciarApp no teste, desmontadas no afterEach. */
let roots: Root[];
let consoleError: MockInstance<typeof console.error>;

beforeEach(() => {
  // A sessão e o céu ficam pendentes para sempre: o teste olha só a montagem.
  vi.stubGlobal("fetch", () => new Promise(() => {}));
  // O jsdom não implementa matchMedia; a cena só lê prefers-reduced-motion.
  vi.stubGlobal("matchMedia", (consulta: string) => ({
    matches: false,
    media: consulta,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }));
  consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
  const esqueleto = document.createElement("p");
  esqueleto.className = "esqueleto";
  roots = [];
  raiz = document.createElement("div");
  raiz.append(esqueleto);
  document.body.append(raiz);
});

/** Inicia o app guardando a raiz para desmontar no fim do teste. */
async function iniciar(carregar: (id: string) => Promise<CenarioFront>): Promise<void> {
  roots.push(await iniciarApp(raiz, null, carregar));
}

afterEach(() => {
  act(() => {
    roots.forEach((root) => {
      root.unmount();
    });
  });
  raiz.remove();
  consoleError.mockRestore();
  vi.unstubAllGlobals();
});

describe("iniciarApp", () => {
  it("cenário que falha mostra o alerta", async () => {
    await iniciar(() => Promise.reject(new ErroCenario("cenário terraco indisponível (404)")));

    const alerta = await within(raiz).findByRole("alert");
    expect(alerta).toHaveTextContent(TEXTO_DO_ERRO);
    expect(screen.getByRole("heading", { name: "Antes da primeira estrela" })).toBeInTheDocument();
  });

  it("falha de rede também mostra o alerta", async () => {
    await iniciar(() => Promise.reject(new TypeError("Failed to fetch")));

    expect(await within(raiz).findByRole("alert")).toHaveTextContent(TEXTO_DO_ERRO);
  });

  it("o detalhe técnico do erro vai ao console, não à tela", async () => {
    const erro = new ErroCenario('cenario.json: Invalid key: Expected "nome"');

    await iniciar(() => Promise.reject(erro));

    await within(raiz).findByRole("alert");
    expect(consoleError).toHaveBeenCalledWith(expect.any(String), erro);
    expect(raiz).not.toHaveTextContent("Invalid key");
  });

  it("enquanto o cenário carrega, o esqueleto fica na tela", async () => {
    void iniciar(() => new Promise(() => {}));
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(raiz.querySelector(".esqueleto")).not.toBeNull();
    expect(raiz.querySelector("#palco")).toBeNull();
    expect(raiz.querySelector("[role=alert]")).toBeNull();
  });

  it("cenário carregado monta o app", async () => {
    const carregar = vi.fn<(id: string) => Promise<CenarioFront>>(() => Promise.resolve(cenarioDoTerraco()));

    await iniciar(carregar);

    expect(carregar).toHaveBeenCalledWith("terraco");
    await waitFor(() => {
      expect(raiz.querySelector("#palco")).not.toBeNull();
    });
    expect(raiz.querySelector("[role=alert]")).toBeNull();
  });
});
