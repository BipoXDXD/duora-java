import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ModoDaVerificacao, SessaoDeCaptura } from "../../api/verificacao";

// Cada teste importa o CapturaDeVida de novo (resetModules): o React.lazy guarda o módulo da primeira
// carga, e cada teste precisa ver o seu próprio import() acontecer (ou falhar).
const carregados = vi.hoisted(() => ({ amplify: 0, falso: 0 }));

function sessao(modo: ModoDaVerificacao): SessaoDeCaptura {
  return {
    sessaoId: "sessao-123",
    regiao: "us-east-1",
    modo,
    credencial: {
      accessKeyId: "ASIAEXEMPLO",
      secretAccessKey: "segredo-exemplo",
      sessionToken: "token-exemplo",
      expiration: new Date("2026-11-27T12:05:00Z"),
    },
    desvioDoRelogioMs: undefined,
  };
}

function detectoresQueCarregam(): void {
  vi.doMock("./DetectorAmplify", () => {
    carregados.amplify += 1;
    return { default: () => <p>detector amplify</p> };
  });
  vi.doMock("./DetectorFalso", () => {
    carregados.falso += 1;
    return { default: () => <p>detector falso</p> };
  });
}

async function renderizar(modo: ModoDaVerificacao) {
  const { CapturaDeVida } = await import("./CapturaDeVida");
  const props = { sessao: sessao(modo), aoConcluir: vi.fn(), aoFalhar: vi.fn(), aoCancelar: vi.fn() };
  render(<CapturaDeVida {...props} />);
  return props;
}

beforeEach(() => {
  vi.resetModules();
  carregados.amplify = 0;
  carregados.falso = 0;
});

afterEach(() => {
  vi.doUnmock("./DetectorAmplify");
  vi.doUnmock("./DetectorFalso");
  vi.restoreAllMocks();
});

describe("CapturaDeVida", () => {
  it("amplifySoCarregaComModoAws", async () => {
    detectoresQueCarregam();

    await renderizar("aws");

    expect(await screen.findByText("detector amplify")).toBeInTheDocument();
    expect(carregados).toEqual({ amplify: 1, falso: 0 });
  });

  it("falsoSoCarregaComModoFalso", async () => {
    detectoresQueCarregam();

    await renderizar("falso");

    expect(await screen.findByText("detector falso")).toBeInTheDocument();
    expect(carregados).toEqual({ amplify: 0, falso: 1 });
  });

  it("esperaAbrindoACameraEnquantoCarrega", async () => {
    vi.doMock("./DetectorAmplify", () => new Promise<never>(() => undefined));

    await renderizar("aws");

    expect(screen.getByRole("status")).toHaveTextContent("Abrindo a câmera…");
  });

  it("falhaNoDownloadViraCarregamento", async () => {
    // O React registra no console o erro que o limite pegou; aqui ele é o esperado.
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    vi.doMock("./DetectorAmplify", () => Promise.reject(new Error("Failed to fetch dynamically imported module")));

    const { aoFalhar } = await renderizar("aws");

    await vi.waitFor(() => {
      expect(aoFalhar).toHaveBeenCalledExactlyOnceWith("carregamento");
    });
  });

  it("regiaoComTituloSelfieEmVideo", async () => {
    detectoresQueCarregam();

    await renderizar("falso");

    expect(screen.getByRole("region", { name: "Selfie em vídeo" })).toBeInTheDocument();
    expect(await screen.findByText("detector falso")).toBeInTheDocument();
  });
});
