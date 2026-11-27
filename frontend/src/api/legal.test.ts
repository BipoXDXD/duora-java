import { afterEach, describe, expect, it, vi } from "vitest";
import { obterVersoesLegais } from "./legal";

const VERSOES = {
  termos: { versao: "2026-11-23" },
  privacidade: { versao: "2026-11-23" },
  contato: "support@meetduora.com",
  encarregado: { nome: "o responsável pelo Duora", email: "encarregado@exemplo.invalid" },
};

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

describe("obterVersoesLegais", () => {
  it("versoesTrazemContatoEEncarregado", async () => {
    const fetchFalso = responder(VERSOES);

    await expect(obterVersoesLegais()).resolves.toEqual(VERSOES);
    expect(fetchFalso).toHaveBeenCalledWith("/api/legal/versoes", expect.objectContaining({ method: "GET" }));
  });

  it("semVersaoDosTermosViraRespostaInvalida", async () => {
    responder({ ...VERSOES, termos: {} });

    await expect(obterVersoesLegais()).rejects.toMatchObject({ codigo: "RESPOSTA_INVALIDA" });
  });
});
