import { afterEach, describe, expect, it, vi } from "vitest";
import { inscrever, obterChavePublica, removerInscricao } from "./push";

function responder(status: number, corpo?: unknown) {
  const fetchFalso = vi.fn<typeof fetch>(() => {
    const json = corpo === undefined ? null : JSON.stringify(corpo);
    const tipo: Record<string, string> = json === null ? {} : { "Content-Type": "application/json" };
    return Promise.resolve(new Response(json, { status, headers: tipo }));
  });
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

function chamada(fetchFalso: ReturnType<typeof responder>) {
  const [url, init] = fetchFalso.mock.calls[0] ?? [];
  return { url, metodo: init?.method, corpo: init?.body };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("obterChavePublica", () => {
  it("chavePublicaLeOCampoChave", async () => {
    const fetchFalso = responder(200, { chave: "BEl62iUYgUivxIkv69yViEuiBIa" });

    const chave = await obterChavePublica();

    expect(chave).toBe("BEl62iUYgUivxIkv69yViEuiBIa");
    expect(chamada(fetchFalso)).toMatchObject({ url: "/api/push/chave-publica", metodo: "GET" });
  });

  it("chavePublica404ViraNull", async () => {
    responder(404, { codigo: "PUSH_INDISPONIVEL", mensagem: "Os avisos não estão disponíveis agora." });

    const chave = await obterChavePublica();

    expect(chave).toBeNull();
  });
});

describe("inscrever", () => {
  it("inscreverMandaEndpointEChaves", async () => {
    const fetchFalso = responder(204);
    const inscricao = { endpoint: "https://fcm.googleapis.com/fcm/send/x", chaves: { p256dh: "p", auth: "a" } };

    await inscrever(inscricao);

    const { url, metodo, corpo } = chamada(fetchFalso);
    expect(url).toBe("/api/push/inscricao");
    expect(metodo).toBe("PUT");
    expect(JSON.parse(corpo as string)).toEqual(inscricao);
  });
});

describe("removerInscricao", () => {
  it("removerMandaDeleteComEndpointNoCorpo", async () => {
    const fetchFalso = responder(204);

    await removerInscricao("https://fcm.googleapis.com/fcm/send/x");

    const { url, metodo, corpo } = chamada(fetchFalso);
    expect(url).toBe("/api/push/inscricao");
    expect(metodo).toBe("DELETE");
    expect(JSON.parse(corpo as string)).toEqual({ endpoint: "https://fcm.googleapis.com/fcm/send/x" });
  });
});
