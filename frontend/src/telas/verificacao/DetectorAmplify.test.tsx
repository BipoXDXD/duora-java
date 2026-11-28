import { render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { SessaoDeCaptura } from "../../api/verificacao";
import DetectorAmplify, { falhaDoEstado } from "./DetectorAmplify";
import type { FalhaDoDetector } from "./etapas";
import { TEXTOS_DO_DETECTOR } from "./textosDoDetector";

/** As props que o Core recebeu na última renderização; o componente de verdade abriria a câmera. */
const recebidas = vi.hoisted(() => ({ props: undefined as Record<string, unknown> | undefined }));

vi.mock("@aws-amplify/ui-react-liveness", () => ({
  FaceLivenessDetectorCore: (props: Record<string, unknown>) => {
    recebidas.props = props;
    return null;
  },
}));

interface ConfigRecebida {
  credentialProvider: () => Promise<unknown>;
  systemClockOffset: number | undefined;
}

interface PropsRecebidas {
  sessionId: string;
  region: string;
  disableStartScreen?: boolean;
  displayText: unknown;
  config: ConfigRecebida;
  onAnalysisComplete: () => Promise<void>;
  onUserCancel: () => void;
  onError: (erro: { state: string; error: Error }) => void;
}

const CREDENCIAL = {
  accessKeyId: "ASIAEXEMPLO",
  secretAccessKey: "segredo-exemplo",
  sessionToken: "token-exemplo",
  expiration: new Date("2026-11-27T12:05:00Z"),
};

const SESSAO: SessaoDeCaptura = {
  sessaoId: "sessao-123",
  regiao: "us-east-1",
  modo: "aws",
  credencial: CREDENCIAL,
  desvioDoRelogioMs: 420_000,
};

function renderizar(sessao: SessaoDeCaptura = SESSAO) {
  const props = { sessao, aoConcluir: vi.fn(), aoFalhar: vi.fn(), aoCancelar: vi.fn() };
  render(<DetectorAmplify {...props} />);
  if (recebidas.props === undefined) {
    throw new Error("O FaceLivenessDetectorCore não foi renderizado.");
  }
  return { ...props, core: recebidas.props as unknown as PropsRecebidas };
}

beforeEach(() => {
  recebidas.props = undefined;
});

describe("DetectorAmplify", () => {
  it("credentialProviderDevolveACredencialDaSessao", async () => {
    const { core } = renderizar();

    await expect(core.config.credentialProvider()).resolves.toBe(CREDENCIAL);
    expect(core.sessionId).toBe("sessao-123");
  });

  // Sem binaryPath nem faceModelUrl: o wasm e o modelo vêm das URLs que a CSP da F1 libera.
  it("configSoTemCredencialEDesvio", () => {
    const { core } = renderizar();

    expect(Object.keys(core.config)).toEqual(["credentialProvider", "systemClockOffset"]);
    expect(core.config.systemClockOffset).toBe(420_000);
  });

  it("regiaoFixa", () => {
    const { core } = renderizar();

    expect(core.region).toBe("us-east-1");
  });

  it("semTelaInicialDoAmplify", () => {
    const { core } = renderizar();

    expect(core.disableStartScreen).toBe(true);
    expect(core.displayText).toBe(TEXTOS_DO_DETECTOR);
  });

  it("analiseConcluidaChamaAoConcluir", async () => {
    const { core, aoConcluir } = renderizar();

    await core.onAnalysisComplete();

    expect(aoConcluir).toHaveBeenCalledOnce();
  });

  it("cancelarChamaAoCancelar", () => {
    const { core, aoCancelar, aoFalhar } = renderizar();

    core.onUserCancel();

    expect(aoCancelar).toHaveBeenCalledOnce();
    expect(aoFalhar).not.toHaveBeenCalled();
  });

  it("erroDoCoreChamaAoFalharComAFalha", () => {
    const { core, aoFalhar } = renderizar();

    core.onError({ state: "MULTIPLE_FACES_ERROR", error: new Error("dois rostos") });

    expect(aoFalhar).toHaveBeenCalledExactlyOnceWith("outraPessoa");
  });
});

describe("falhaDoEstado", () => {
  it.each<[Parameters<typeof falhaDoEstado>[0], FalhaDoDetector]>([
    ["CAMERA_ACCESS_ERROR", "camera"],
    ["DEFAULT_CAMERA_NOT_FOUND_ERROR", "camera"],
    ["TIMEOUT", "tempo"],
    ["FRESHNESS_TIMEOUT", "tempo"],
    ["FACE_DISTANCE_ERROR", "rosto"],
    ["MULTIPLE_FACES_ERROR", "outraPessoa"],
    ["CONNECTION_TIMEOUT", "conexao"],
    ["SERVER_ERROR", "servico"],
    ["CAMERA_FRAMERATE_ERROR", "aparelho"],
    ["RUNTIME_ERROR", "aparelho"],
    ["MOBILE_LANDSCAPE_ERROR", "orientacao"],
  ])("estadoDeErroViraFalha: %s → %s", (estado, falha) => {
    expect(falhaDoEstado(estado)).toBe(falha);
  });
});
