import { afterEach, describe, expect, it, vi } from "vitest";
import { entrarNaFila, obterFila, obterMovimento, sairDaFila } from "./fila";
import { ErroHttp } from "./http";

const AGUARDANDO_VAGA = {
  situacao: "AGUARDANDO_VAGA",
  desde: "2026-11-28T12:00:00Z",
  posicao: 2,
  estimativaMinutos: 4,
  encontroId: null,
  cenario: null,
};

const PAREADO = {
  situacao: "PAREADO",
  desde: "2026-11-28T12:00:00Z",
  posicao: null,
  estimativaMinutos: null,
  encontroId: "enc-1",
  cenario: "terraco",
};

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

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("entrarNaFila", () => {
  it("entrarSemPosicaoMandaCorpoVazio", async () => {
    const fetchFalso = responder(200, AGUARDANDO_VAGA);

    const estado = await entrarNaFila(null);

    expect(estado).toEqual(AGUARDANDO_VAGA);
    expect(chamada(fetchFalso)).toEqual({ url: "/api/fila", metodo: "POST", corpo: "{}" });
  });

  it("entrarComPosicaoMandaLatitudeELongitude", async () => {
    const fetchFalso = responder(200, AGUARDANDO_VAGA);

    await entrarNaFila({ latitude: -23.55, longitude: -46.63 });

    expect(JSON.parse(chamada(fetchFalso).corpo as string)).toEqual({ latitude: -23.55, longitude: -46.63 });
  });
});

describe("obterFila", () => {
  it("estadoComChaveAMaisViraRespostaInvalida", async () => {
    responder(200, { ...AGUARDANDO_VAGA, celula: "6gyf" });

    const erro = await erroDe(obterFila());

    expect(erro.codigo).toBe("RESPOSTA_INVALIDA");
  });

  it("situacaoForaDaListaViraRespostaInvalida", async () => {
    responder(200, { ...AGUARDANDO_VAGA, situacao: "ESPERANDO" });

    const erro = await erroDe(obterFila());

    expect(erro.codigo).toBe("RESPOSTA_INVALIDA");
  });

  it("pareadoSemEncontroIdViraRespostaInvalida", async () => {
    responder(200, { ...PAREADO, encontroId: null });

    const erro = await erroDe(obterFila());

    expect(erro.codigo).toBe("RESPOSTA_INVALIDA");
  });

  it("obterForaDaFilaViraNull", async () => {
    const fetchFalso = responder(404, { codigo: "FORA_DA_FILA", mensagem: "Você não está na fila." });

    const estado = await obterFila();

    expect(estado).toBeNull();
    expect(chamada(fetchFalso)).toMatchObject({ url: "/api/fila", metodo: "GET" });
  });

  it("obterComOutro404Rejeita", async () => {
    responder(404, { codigo: "DESCONHECIDO" });

    const erro = await erroDe(obterFila());

    expect(erro.status).toBe(404);
    expect(erro.codigo).toBe("DESCONHECIDO");
  });
});

describe("sairDaFila", () => {
  it("sairUsaDelete", async () => {
    const fetchFalso = responder(204);

    await sairDaFila();

    expect(chamada(fetchFalso)).toMatchObject({ url: "/api/fila", metodo: "DELETE" });
  });
});

describe("obterMovimento", () => {
  it("movimentoSemDadosTemNullEListaVazia", async () => {
    const fetchFalso = responder(200, { esperaTipicaMinutos: null, horasDePico: [] });

    const movimento = await obterMovimento();

    expect(movimento).toEqual({ esperaTipicaMinutos: null, horasDePico: [] });
    expect(chamada(fetchFalso)).toMatchObject({ url: "/api/fila/movimento", metodo: "GET" });
  });

  it("horaDePicoForaDe0a23ViraRespostaInvalida", async () => {
    responder(200, { esperaTipicaMinutos: 3, horasDePico: [19, 24] });

    const erro = await erroDe(obterMovimento());

    expect(erro.codigo).toBe("RESPOSTA_INVALIDA");
  });
});
