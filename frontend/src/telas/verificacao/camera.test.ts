import { describe, expect, it, vi } from "vitest";
import { checarCamera, navegadorTemSuporte } from "./camera";

/** Uma janela com tudo o que a selfie precisa; cada teste tira uma peça. */
function janelaCompleta() {
  return {
    isSecureContext: true,
    navigator: { mediaDevices: { getUserMedia: () => Promise.reject(new Error("não usado")) } },
    WebAssembly: {},
  };
}

function erroDeMidia(nome: string): DOMException {
  return new DOMException("recusado", nome);
}

describe("checarCamera", () => {
  it("checarCameraParaAsTrilhas", async () => {
    const parar = vi.fn();
    const getUserMedia = vi.fn(() => Promise.resolve({ getTracks: () => [{ stop: parar }, { stop: parar }] }));

    const resultado = await checarCamera({ getUserMedia } as unknown as Pick<MediaDevices, "getUserMedia">);

    expect(resultado).toBe("ok");
    expect(getUserMedia).toHaveBeenCalledExactlyOnceWith({ video: true, audio: false });
    expect(parar).toHaveBeenCalledTimes(2);
  });

  it.each([
    ["NotAllowedError", "negada"],
    ["SecurityError", "negada"],
    ["NotFoundError", "ausente"],
    ["OverconstrainedError", "ausente"],
    ["NotReadableError", "ocupada"],
  ] as const)("erroDaCameraViraMotivo: %s → %s", async (nome, motivo) => {
    const getUserMedia = vi.fn(() => Promise.reject(erroDeMidia(nome)));

    const resultado = await checarCamera({ getUserMedia });

    expect(resultado).toBe(motivo);
  });
});

describe("navegadorTemSuporte", () => {
  it("janelaCompletaTemSuporte", () => {
    expect(navegadorTemSuporte(janelaCompleta())).toBe(true);
  });

  it("semMediaDevicesNaoTemSuporte", () => {
    expect(navegadorTemSuporte({ ...janelaCompleta(), navigator: {} })).toBe(false);
  });

  it("semWebAssemblyNaoTemSuporte", () => {
    const { isSecureContext, navigator } = janelaCompleta();

    expect(navegadorTemSuporte({ isSecureContext, navigator })).toBe(false);
  });

  it("contextoInseguroNaoTemSuporte", () => {
    expect(navegadorTemSuporte({ ...janelaCompleta(), isSecureContext: false })).toBe(false);
  });
});
