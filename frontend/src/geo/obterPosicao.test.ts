import { describe, expect, it, vi } from "vitest";
import { obterPosicao, VIGIA_DA_PERMISSAO_MS } from "./obterPosicao";

type Sucesso = PositionCallback;
type Falha = PositionErrorCallback;

/** Um Geolocation que entrega à função de teste os callbacks recebidos. */
function geolocalizacao(responder: (sucesso: Sucesso, falha: Falha) => void) {
  const getCurrentPosition = vi.fn<Geolocation["getCurrentPosition"]>((sucesso, falha) => {
    responder(sucesso, falha ?? (() => undefined));
  });
  const geo = { getCurrentPosition, watchPosition: vi.fn(), clearWatch: vi.fn() } as unknown as Geolocation;
  return { geo, getCurrentPosition };
}

function erro(code: number): GeolocationPositionError {
  return { code, message: "", PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 };
}

function posicao(latitude: number, longitude: number): GeolocationPosition {
  return { coords: { latitude, longitude, accuracy: 30 }, timestamp: 0 } as GeolocationPosition;
}

describe("obterPosicao", () => {
  it("negadaViraNegada", async () => {
    const { geo } = geolocalizacao((_, falha) => {
      falha(erro(1));
    });

    await expect(obterPosicao(geo)).resolves.toEqual({ ok: false, falha: "negada" });
  });

  it("timeoutViraIndisponivel", async () => {
    const { geo } = geolocalizacao((_, falha) => {
      falha(erro(3));
    });

    await expect(obterPosicao(geo)).resolves.toEqual({ ok: false, falha: "indisponivel" });
  });

  it("semCallbackEm20sViraSemResposta", async () => {
    vi.useFakeTimers();
    try {
      const { geo } = geolocalizacao(() => undefined);

      const resultado = obterPosicao(geo, { setTimeout, clearTimeout });
      await vi.advanceTimersByTimeAsync(VIGIA_DA_PERMISSAO_MS);

      await expect(resultado).resolves.toEqual({ ok: false, falha: "semResposta" });
      expect(VIGIA_DA_PERMISSAO_MS).toBe(20_000);
    } finally {
      vi.useRealTimers();
    }
  });

  it("sucessoArredondaADuasCasas", async () => {
    const { geo } = geolocalizacao((sucesso) => {
      sucesso(posicao(-23.550512, -46.633308));
    });

    await expect(obterPosicao(geo)).resolves.toEqual({
      ok: true,
      coordenada: { latitude: -23.55, longitude: -46.63 },
    });
  });

  it("semGeolocationViraIndisponivel", async () => {
    await expect(obterPosicao(undefined)).resolves.toEqual({ ok: false, falha: "indisponivel" });
  });

  it("relogioPadraoChamaOsTimersSemTrocarOThis", async () => {
    // No navegador, setTimeout chamado como método de outro objeto lança "Illegal invocation".
    const exigirOThisGlobal = (original: typeof setTimeout | typeof clearTimeout) =>
      function (this: unknown, ...argumentos: unknown[]) {
        if (this !== undefined && this !== globalThis) {
          throw new TypeError("Illegal invocation");
        }
        return (original as (...a: unknown[]) => unknown)(...argumentos);
      };
    vi.stubGlobal("setTimeout", exigirOThisGlobal(globalThis.setTimeout));
    vi.stubGlobal("clearTimeout", exigirOThisGlobal(globalThis.clearTimeout));
    try {
      const { geo } = geolocalizacao((_, falha) => {
        falha(erro(1));
      });

      await expect(obterPosicao(geo)).resolves.toEqual({ ok: false, falha: "negada" });
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("pedeComAsOpcoesDoContrato", async () => {
    const { geo, getCurrentPosition } = geolocalizacao((sucesso) => {
      sucesso(posicao(0, 0));
    });

    await obterPosicao(geo);

    expect(getCurrentPosition).toHaveBeenCalledWith(expect.any(Function), expect.any(Function), {
      enableHighAccuracy: false,
      timeout: 10_000,
      maximumAge: 300_000,
    });
  });
});
