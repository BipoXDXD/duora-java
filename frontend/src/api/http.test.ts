import { afterEach, describe, expect, it, vi } from "vitest";
import { ErroHttp, novaChaveDeIdempotencia, requisitarHttp } from "./http";

const CHAVE = /^[A-Za-z0-9-]{8,64}$/;

function responder(status: number, corpo?: unknown, cabecalhos: Record<string, string> = {}) {
  const fetchFalso = vi.fn<typeof fetch>(() => {
    const json = corpo === undefined ? null : JSON.stringify(corpo);
    const tipo: Record<string, string> = json === null ? {} : { "Content-Type": "application/problem+json" };
    return Promise.resolve(new Response(json, { status, headers: { ...tipo, ...cabecalhos } }));
  });
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

async function erroDe(promessa: Promise<unknown>): Promise<ErroHttp> {
  try {
    await promessa;
  } catch (erro) {
    if (erro instanceof ErroHttp) {
      return erro;
    }
    throw erro;
  }
  throw new Error("a promessa deveria ter sido rejeitada");
}

function cabecalhosEnviados(fetchFalso: ReturnType<typeof responder>): Record<string, string> {
  const [, init] = fetchFalso.mock.calls[0] ?? [];
  return init?.headers as Record<string, string>;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("requisitarHttp", () => {
  it("enviaXDuoraAcceptECredenciais", async () => {
    const fetchFalso = responder(204);

    await requisitarHttp("GET", "/sala");

    const [url, init] = fetchFalso.mock.calls[0] ?? [];
    expect(url).toBe("/api/sala");
    expect(cabecalhosEnviados(fetchFalso)["X-Duora"]).toBe("1");
    expect(cabecalhosEnviados(fetchFalso)["Accept"]).toContain("application/problem+json");
    expect(init?.credentials).toBe("same-origin");
    expect(init?.cache).toBe("no-store");
  });

  it("leCodigoEMensagemDoProblemJson", async () => {
    responder(422, { codigo: "EMAIL_INVALIDO", mensagem: "Use um e-mail válido.", detail: "x" });

    const erro = await erroDe(requisitarHttp("POST", "/conta/entrar", { corpo: { email: "a" } }));

    expect(erro).toMatchObject({ status: 422, codigo: "EMAIL_INVALIDO", mensagem: "Use um e-mail válido." });
    expect(erro.transitorio).toBe(false);
  });

  it("semMensagemUsaDetail", async () => {
    responder(400, { detail: "d" });

    const erro = await erroDe(requisitarHttp("POST", "/conta/entrar"));

    expect(erro.mensagem).toBe("d");
    expect(erro.codigo).toBe("DESCONHECIDO");
  });

  it("leRetryAfterEmSegundos", async () => {
    responder(429, { codigo: "LIMITE_DE_TAXA" }, { "Retry-After": "120" });
    const comCabecalho = await erroDe(requisitarHttp("POST", "/conta/entrar"));

    responder(429, { codigo: "LIMITE_DE_TAXA" });
    const semCabecalho = await erroDe(requisitarHttp("POST", "/conta/entrar"));

    expect(comCabecalho.tentarDeNovoEmSegundos).toBe(120);
    expect(semCabecalho.tentarDeNovoEmSegundos).toBeNull();
  });

  it("falhaDeRedeViraStatusZero", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(() => Promise.reject(new TypeError("Failed to fetch"))),
    );

    const erro = await erroDe(requisitarHttp("GET", "/sala"));

    expect(erro).toMatchObject({ status: 0, codigo: "REDE", message: "Failed to fetch" });
    expect(erro.transitorio).toBe(true);
  });

  it("cabecalhosExtrasSaem", async () => {
    const fetchFalso = responder(202);

    await requisitarHttp("POST", "/conta/entrar", { cabecalhos: { "Idempotency-Key": "k-12345678" } });

    expect(cabecalhosEnviados(fetchFalso)["Idempotency-Key"]).toBe("k-12345678");
  });

  it("respostaSemJsonDevolveDadosNulos", async () => {
    responder(202, undefined, { "Content-Type": "text/plain" });

    const resposta = await requisitarHttp("POST", "/conta/entrar");

    expect(resposta).toMatchObject({ status: 202, dados: null });
  });

  it("304NaoLancaEEnviaOEtag", async () => {
    const fetchFalso = responder(304);

    const resposta = await requisitarHttp("GET", "/sala", { etag: '"v4"' });

    expect(resposta.status).toBe(304);
    expect(cabecalhosEnviados(fetchFalso)["If-None-Match"]).toBe('"v4"');
  });

  it("erro5xxSemCorpoViraServidorTransitorio", async () => {
    responder(503);

    const erro = await erroDe(requisitarHttp("GET", "/sala"));

    expect(erro).toMatchObject({ status: 503, codigo: "SERVIDOR", mensagem: null, corpo: null });
    expect(erro.transitorio).toBe(true);
  });
});

describe("novaChaveDeIdempotencia", () => {
  it("novaChaveUsaRandomUUID", () => {
    const uuid = "0193f1c2-7a4b-7c3d-8e5f-0a1b2c3d4e5f";
    const cripto = {
      randomUUID: () => uuid as `${string}-${string}-${string}-${string}-${string}`,
      getRandomValues: vi.fn(),
    };

    const chave = novaChaveDeIdempotencia(cripto);

    expect(chave).toBe(uuid);
    expect(chave).toMatch(CHAVE);
    expect(cripto.getRandomValues).not.toHaveBeenCalled();
  });

  it("novaChaveSemRandomUUIDUsaGetRandomValues", () => {
    const cripto = {
      getRandomValues: <T extends ArrayBufferView | null>(destino: T): T => {
        if (destino instanceof Uint8Array) {
          destino.forEach((_, i) => (destino[i] = i * 17));
        }
        return destino;
      },
    };

    const chave = novaChaveDeIdempotencia(cripto);

    expect(chave).toMatch(/^[0-9a-f]{32}$/);
    expect(chave).toMatch(CHAVE);
  });
});
