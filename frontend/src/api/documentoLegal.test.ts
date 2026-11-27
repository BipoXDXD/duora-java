import { afterEach, describe, expect, it, vi } from "vitest";
import { obterDocumentoLegal } from "./documentoLegal";

function responder(corpo: unknown) {
  const fetchFalso = vi.fn<typeof fetch>(() =>
    Promise.resolve(new Response(JSON.stringify(corpo), { headers: { "Content-Type": "application/json" } })),
  );
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("obterDocumentoLegal", () => {
  it("documentoTrazVersaoEHtml", async () => {
    const fetchFalso = responder({ versao: "2026-11-22", html: "<h1>Termos de uso</h1>" });

    await expect(obterDocumentoLegal("termos")).resolves.toEqual({
      versao: "2026-11-22",
      html: "<h1>Termos de uso</h1>",
    });
    expect(fetchFalso).toHaveBeenCalledWith("/api/legal/termos", expect.objectContaining({ method: "GET" }));
  });

  it.each([
    ["sem html", { versao: "2026-11-22" }],
    ["versão fora do formato", { versao: "22/11/2026", html: "<p>Texto.</p>" }],
  ])("respostaIncompletaViraRespostaInvalida (%s)", async (_caso, corpo) => {
    responder(corpo);

    await expect(obterDocumentoLegal("privacidade")).rejects.toMatchObject({ codigo: "RESPOSTA_INVALIDA" });
  });
});
