import { afterEach, describe, expect, it, vi } from "vitest";
import { obterConfiguracao } from "./conta";
import { ErroHttp } from "./http";

function responder(corpo: unknown) {
  const fetchFalso = vi.fn<typeof fetch>(() =>
    Promise.resolve(
      new Response(JSON.stringify(corpo), { status: 200, headers: { "Content-Type": "application/json" } }),
    ),
  );
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("obterConfiguracao", () => {
  it("configuracaoDevolveAChave", async () => {
    const fetchFalso = responder({ chaveDoSiteTurnstile: "1x00000000000000000000AA" });

    const configuracao = await obterConfiguracao();

    expect(configuracao).toEqual({ chaveDoSiteTurnstile: "1x00000000000000000000AA" });
    expect(fetchFalso).toHaveBeenCalledWith("/api/conta/configuracao", expect.objectContaining({ method: "GET" }));
  });

  it("configuracaoSemChaveViraRespostaInvalida", async () => {
    responder({});

    await expect(obterConfiguracao()).rejects.toThrow(ErroHttp);
    await expect(obterConfiguracao()).rejects.toMatchObject({ codigo: "RESPOSTA_INVALIDA" });
  });
});
