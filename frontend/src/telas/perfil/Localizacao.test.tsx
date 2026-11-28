import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PreferenciasDeEncontro, SituacaoDaLocalizacao } from "../../api/perfil";
import { VIGIA_DA_PERMISSAO_MS } from "../../geo/obterPosicao";
import { Preferencias } from "./Preferencias";

const PREFERENCIAS: PreferenciasDeEncontro = {
  versao: 2,
  idadeMinima: 25,
  idadeMaxima: 40,
  raio: "SEM_LIMITE",
  raioObrigatorio: false,
};

const INATIVA: SituacaoDaLocalizacao = { consentida: false, atualizadaEm: null };
const ATIVA: SituacaoDaLocalizacao = { consentida: true, atualizadaEm: "2026-11-28T12:00:00Z" };

/** O ponto exato do aparelho: nenhum destes dígitos pode sair da função que o recebe. */
const LATITUDE = -23.550512;
const LONGITUDE = -46.633308;

type Responder = (sucesso: PositionCallback, falha: PositionErrorCallback) => void;

/** Instala um navigator.geolocation falso; o afterEach o tira. */
function geolocalizacao(responder: Responder) {
  Object.defineProperty(window.navigator, "geolocation", {
    configurable: true,
    value: {
      getCurrentPosition: (sucesso: PositionCallback, falha?: PositionErrorCallback | null) => {
        responder(sucesso, falha ?? (() => undefined));
      },
    },
  });
}

const negar: Responder = (_, falha) => {
  falha({ code: 1, message: "", PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 });
};

const permitir: Responder = (sucesso) => {
  sucesso({ coords: { latitude: LATITUDE, longitude: LONGITUDE, accuracy: 12 }, timestamp: 0 } as GeolocationPosition);
};

function json(status: number, corpo?: unknown): Response {
  if (status === 204) {
    return new Response(null, { status });
  }
  return new Response(JSON.stringify(corpo ?? null), { status, headers: { "Content-Type": "application/json" } });
}

/** Grava os pedidos ("MÉTODO /caminho") e os corpos; o PUT e o DELETE da localização dão 204. */
function prepararFetch(preferencias: PreferenciasDeEncontro = PREFERENCIAS) {
  const pedidos: string[] = [];
  const corpos: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      const chave = `${opcoes?.method ?? "GET"} ${typeof entrada === "string" ? entrada : "?"}`;
      pedidos.push(chave);
      corpos.push(typeof opcoes?.body === "string" ? opcoes.body : "");
      switch (chave) {
        case "GET /api/perfil/preferencias":
          return Promise.resolve(json(200, preferencias));
        case "GET /api/perfil/localizacao":
          return Promise.resolve(json(200, ATIVA));
        case "PUT /api/perfil/localizacao":
        case "DELETE /api/perfil/localizacao":
          return Promise.resolve(json(204));
        default:
          return Promise.reject(new Error(`pedido inesperado: ${chave}`));
      }
    }),
  );
  return { pedidos, corpos };
}

function renderizar(localizacao: SituacaoDaLocalizacao) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  cliente.setQueryData(["perfil", "preferencias"], PREFERENCIAS);
  cliente.setQueryData(["perfil", "localizacao"], localizacao);
  const { container } = render(
    <QueryClientProvider client={cliente}>
      <Preferencias />
    </QueryClientProvider>,
  );
  return { container };
}

function usarLocalizacao(): HTMLElement {
  return screen.getByRole("button", { name: "Usar minha localização" });
}

const FALHA = 'Não conseguimos sua localização. A distância fica em "Sem limite".';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
  vi.restoreAllMocks();
  Reflect.deleteProperty(window.navigator, "geolocation");
});

describe("Localizacao", () => {
  it("localizacaoNegadaVoltaParaSemLimite", async () => {
    geolocalizacao(negar);
    const { pedidos } = prepararFetch();
    renderizar(INATIVA);
    const usuario = userEvent.setup();
    expect(screen.getByText("Guardamos só uma área de cerca de 5 km, nunca o ponto exato.")).toBeInTheDocument();

    await usuario.click(usarLocalizacao());

    expect(await screen.findByText(FALHA)).toBeInTheDocument();
    expect(
      screen.getByText("Para tentar de novo, libere a localização nas configurações do navegador."),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Sem limite" })).toBeChecked();
    await waitFor(() => {
      expect(pedidos).toContain("GET /api/perfil/preferencias");
    });
    expect(pedidos).not.toContain("PUT /api/perfil/localizacao");
  });

  it("semRespostaVoltaParaSemLimite", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    geolocalizacao(() => undefined);
    const { pedidos } = prepararFetch();
    renderizar(INATIVA);
    const usuario = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) });

    await usuario.click(usarLocalizacao());
    expect(screen.getByText("Esperando a permissão do navegador…")).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(VIGIA_DA_PERMISSAO_MS));

    expect(await screen.findByText(FALHA)).toBeInTheDocument();
    expect(screen.queryByText(/libere a localização/)).not.toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Sem limite" })).toBeChecked();
    await waitFor(() => {
      expect(pedidos).toContain("GET /api/perfil/preferencias");
    });
  });

  it("ativarMandaPutComCoordenadaArredondada", async () => {
    geolocalizacao(permitir);
    const { pedidos, corpos } = prepararFetch();
    renderizar(INATIVA);
    const usuario = userEvent.setup();

    await usuario.click(usarLocalizacao());

    expect(await screen.findByText("Localização ativa (área aproximada)")).toBeInTheDocument();
    const indice = pedidos.indexOf("PUT /api/perfil/localizacao");
    expect(JSON.parse(corpos[indice] ?? "null")).toEqual({ latitude: -23.55, longitude: -46.63 });
    expect(screen.getByRole("radio", { name: "Até 5 km" })).not.toHaveAttribute("aria-disabled");
  });

  it("coordenadaNuncaVaiAoDomNemAoConsole", async () => {
    const espioes = (["log", "info", "warn", "error", "debug"] as const).map((metodo) =>
      vi.spyOn(console, metodo).mockImplementation(() => undefined),
    );
    geolocalizacao(permitir);
    prepararFetch();
    const { container } = renderizar(INATIVA);
    const usuario = userEvent.setup();

    await usuario.click(usarLocalizacao());
    await screen.findByText("Localização ativa (área aproximada)");

    const html = document.documentElement.outerHTML + container.outerHTML;
    for (const digitos of ["23.55", "46.63", "550512", "633308"]) {
      expect(html).not.toContain(digitos);
    }
    const escrito = espioes.flatMap((espiao) => espiao.mock.calls.flat().map(String)).join("\n");
    expect(escrito).not.toMatch(/23[.,]55|46[.,]63/);
  });

  it("desativarVoltaParaSemLimite", async () => {
    const { pedidos } = prepararFetch();
    renderizar(ATIVA);
    const usuario = userEvent.setup();

    await usuario.click(screen.getByRole("button", { name: "Desativar localização" }));

    expect(
      await screen.findByText('Localização desativada. A distância voltou para "Sem limite".'),
    ).toBeInTheDocument();
    expect(pedidos).toContain("DELETE /api/perfil/localizacao");
    expect(screen.getByRole("radio", { name: "Sem limite" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Até 5 km" })).toHaveAttribute("aria-disabled", "true");
  });
});
