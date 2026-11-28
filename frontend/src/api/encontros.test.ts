import { afterEach, describe, expect, it, vi } from "vitest";
import { criarConvite, entrarPorConvite, obterCenariosDoConvite, pedirAssento } from "./encontros";

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
  return { url, metodo: init?.method, corpo: init?.body, cabecalhos: new Headers(init?.headers) };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("criarConvite", () => {
  it("criarSemCenarioMandaNull", async () => {
    const fetchFalso = responder(201, { encontroId: "enc-1", convite: "tok-1", cenario: "terraco" });

    await criarConvite(null, "chave-0001");

    const { url, metodo, corpo, cabecalhos } = chamada(fetchFalso);
    expect(url).toBe("/api/encontros/privados");
    expect(metodo).toBe("POST");
    expect(JSON.parse(corpo as string)).toEqual({ cenario: null });
    expect(cabecalhos.get("Idempotency-Key")).toBe("chave-0001");
  });

  it("criarDevolveEncontroConviteECenario", async () => {
    responder(201, { encontroId: "enc-1", convite: "tok-1", cenario: "terraco" });

    const convite = await criarConvite("terraco", "chave-0001");

    expect(convite).toEqual({ encontroId: "enc-1", convite: "tok-1", cenario: "terraco" });
  });
});

describe("entrarPorConvite", () => {
  it("entrarMandaIdempotencyKey", async () => {
    const fetchFalso = responder(200, { encontroId: "enc-1", cenario: "terraco" });

    await entrarPorConvite("tok-1", "chave-0002");

    const { url, metodo, corpo, cabecalhos } = chamada(fetchFalso);
    expect(url).toBe("/api/encontros/privados/entrar");
    expect(metodo).toBe("POST");
    expect(JSON.parse(corpo as string)).toEqual({ convite: "tok-1" });
    expect(cabecalhos.get("Idempotency-Key")).toBe("chave-0002");
  });

  it("entrarDevolveEncontroECenario", async () => {
    responder(200, { encontroId: "enc-1", cenario: "terraco" });

    const encontro = await entrarPorConvite("tok-1", "chave-0002");

    expect(encontro).toEqual({ encontroId: "enc-1", cenario: "terraco" });
  });
});

describe("pedirAssento", () => {
  it("pedirAssentoUsaOId", async () => {
    const fetchFalso = responder(200, { qualquer: "visao" });

    await pedirAssento("abc");

    expect(chamada(fetchFalso)).toMatchObject({ url: "/api/encontros/abc/assento", metodo: "POST" });
  });
});

describe("obterCenariosDoConvite", () => {
  it("cenariosLeAListaComIdENome", async () => {
    const fetchFalso = responder(200, { cenarios: [{ id: "terraco", nome: "Terraço" }] });

    const cenarios = await obterCenariosDoConvite();

    expect(cenarios).toEqual([{ id: "terraco", nome: "Terraço" }]);
    expect(chamada(fetchFalso)).toMatchObject({ url: "/api/encontros/privados/cenarios", metodo: "GET" });
  });
});
