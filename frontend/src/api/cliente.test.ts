import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ErroApi } from "./cliente";

const VISAO = { fase: "MONTANDO", versao: 4 };

function responder(status: number, corpo?: unknown, cabecalhos: Record<string, string> = {}) {
  const fetchFalso = vi.fn<typeof fetch>(() => {
    const json = corpo === undefined ? null : JSON.stringify(corpo);
    const tipo: Record<string, string> = json === null ? {} : { "Content-Type": "application/json" };
    return Promise.resolve(new Response(json, { status, headers: { ...tipo, ...cabecalhos } }));
  });
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

async function erroDe(promessa: Promise<unknown>): Promise<ErroApi> {
  try {
    await promessa;
  } catch (erro) {
    if (erro instanceof ErroApi) {
      return erro;
    }
    throw erro;
  }
  throw new Error("a promessa deveria ter sido rejeitada");
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("api", () => {
  it("304 diz que nada mudou e mantém o ETag enviado", async () => {
    responder(304);

    const resposta = await api.obterSala('"v4"');

    expect(resposta).toEqual({ naoMudou: true, visao: null, etag: '"v4"' });
  });

  it("200 devolve a visão e o ETag da resposta", async () => {
    responder(200, VISAO, { ETag: '"v5"' });

    const resposta = await api.obterSala(null);

    expect(resposta).toEqual({ naoMudou: false, visao: VISAO, etag: '"v5"' });
  });

  it("obterSalaSemConteudoViraSemAssento", async () => {
    responder(204);

    const erro = await erroDe(api.obterSala(null));

    expect(erro).toMatchObject({ status: 401, codigo: "SEM_ASSENTO" });
  });

  it("409 com código e visão vira ErroApi com a visão", async () => {
    responder(409, { codigo: "LUGAR_OCUPADO", visao: VISAO, detail: "ocupado" });

    const erro = await erroDe(api.moverPeca("telescopio", "C1", 4));

    expect(erro).toMatchObject({ status: 409, codigo: "LUGAR_OCUPADO", visao: VISAO, motivo: null });
    expect(erro.transitorio).toBe(false);
  });

  it("falha de rede vira o código REDE, transitório", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(() => Promise.reject(new TypeError("Failed to fetch"))),
    );

    const erro = await erroDe(api.obterSala(null));

    expect(erro).toMatchObject({ status: 0, codigo: "REDE", message: "Failed to fetch" });
    expect(erro.transitorio).toBe(true);
  });

  it("503 LIMITE_SALAS não é transitório", async () => {
    responder(503, { codigo: "LIMITE_SALAS" });

    const erro = await erroDe(api.criarSala("Ana"));

    expect(erro.transitorio).toBe(false);
  });

  it("500 sem corpo vira SERVIDOR, transitório", async () => {
    responder(500);

    const erro = await erroDe(api.pedirAjuste());

    expect(erro).toMatchObject({ status: 500, codigo: "SERVIDOR" });
    expect(erro.transitorio).toBe(true);
  });

  it("toda requisição leva o cabeçalho X-Duora: 1", async () => {
    const fetchFalso = responder(204);

    await api.sair();
    await api.sinalizar("manta", "L2");

    for (const [, init] of fetchFalso.mock.calls) {
      expect(new Headers(init?.headers).get("X-Duora")).toBe("1");
    }
    expect(fetchFalso).toHaveBeenCalledTimes(2);
  });

  it("sinalizarEnviaPecaELugar", async () => {
    const fetchFalso = responder(200, VISAO);

    await api.sinalizar("manta", "L2");

    const [url, init] = fetchFalso.mock.calls[0] ?? [];
    expect(url).toBe("/api/sala/sinal");
    expect(init?.body).toBe('{"peca":"manta","lugar":"L2"}');
  });

  it("pedirERecusarAjusteUsamAsRotasNovas", async () => {
    const fetchFalso = responder(200, VISAO);

    await api.pedirAjuste();
    await api.recusarAjuste();

    const [urlPedido, initPedido] = fetchFalso.mock.calls[0] ?? [];
    const [urlRecusa, initRecusa] = fetchFalso.mock.calls[1] ?? [];
    expect(urlPedido).toBe("/api/sala/ajustar");
    expect(initPedido?.method).toBe("POST");
    expect(urlRecusa).toBe("/api/sala/ajustar/recusar");
    expect(initRecusa?.method).toBe("POST");
  });

  it("tocarAberturaChamaARota", async () => {
    const fetchFalso = responder(200, VISAO);

    const resposta = await api.tocarAbertura();

    const [url, init] = fetchFalso.mock.calls[0] ?? [];
    expect(url).toBe("/api/sala/abertura");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBeUndefined();
    expect(resposta.visao).toEqual(VISAO);
  });

  it("envia o corpo em JSON no caminho do contrato", async () => {
    const fetchFalso = responder(200, VISAO);

    await api.marcarPronto(4, true);

    const [url, init] = fetchFalso.mock.calls[0] ?? [];
    expect(url).toBe("/api/sala/pronto");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify({ versao: 4, pronto: true }));
  });
});
