import { afterEach, describe, expect, it, vi } from "vitest";
import { ErroHttp } from "./http";
import {
  codigoDaVerificacao,
  concluirSelfie,
  desvioDoRelogio,
  enviarDocumento,
  iniciarSessao,
  obterVerificacao,
} from "./verificacao";

const AGORA = Date.parse("2026-11-27T12:00:00.000Z");

const SESSAO = {
  sessaoId: "abc",
  regiao: "us-east-1",
  modo: "aws",
  credencial: {
    accessKeyId: "ASIAFALSA",
    secretAccessKey: "segredo-falso",
    sessionToken: "token-falso",
    expiraEm: "2026-11-27T12:15:00Z",
  },
};

const DESFECHO = {
  resultado: "DUVIDA",
  pedeDocumento: true,
  tentativasRestantesHoje: 2,
  enviosDeDocumentoRestantesHoje: 1,
};

function responder(status: number, corpo?: unknown, cabecalhos: Record<string, string> = {}) {
  const fetchFalso = vi.fn<typeof fetch>(() => {
    const json = corpo === undefined ? null : JSON.stringify(corpo);
    const tipo: Record<string, string> = json === null ? {} : { "Content-Type": "application/json" };
    return Promise.resolve(new Response(json, { status, headers: { ...tipo, ...cabecalhos } }));
  });
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

function chamada(fetchFalso: ReturnType<typeof responder>) {
  const [url, init] = fetchFalso.mock.calls[0] ?? [];
  return { url, init };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("obterVerificacao", () => {
  it("obterVerificacaoLeOModo", async () => {
    const fetchFalso = responder(200, {
      estado: "AGUARDANDO",
      tentativasRestantesHoje: 3,
      enviosDeDocumentoRestantesHoje: 2,
      modo: "falso",
    });

    const situacao = await obterVerificacao();

    expect(situacao).toEqual({
      estado: "AGUARDANDO",
      tentativasRestantesHoje: 3,
      enviosDeDocumentoRestantesHoje: 2,
      modo: "falso",
    });
    expect(chamada(fetchFalso).url).toBe("/api/conta/verificacao");
    expect(chamada(fetchFalso).init?.method).toBe("GET");
  });

  it("obterVerificacaoLeAAnalise", async () => {
    responder(200, { estado: "ANALISE", tentativasRestantesHoje: 2, enviosDeDocumentoRestantesHoje: 1, modo: "falso" });

    expect(await obterVerificacao()).toMatchObject({ estado: "ANALISE" });
  });

  it("obterVerificacaoRecusaModoDesconhecido", async () => {
    responder(200, { estado: "AGUARDANDO", tentativasRestantesHoje: 3, enviosDeDocumentoRestantesHoje: 2, modo: "x" });

    await expect(obterVerificacao()).rejects.toMatchObject({ name: "ErroHttp", codigo: "RESPOSTA_INVALIDA" });
  });
});

describe("iniciarSessao", () => {
  it("iniciarSessaoMandaConsentimentoVersaoEFinalidade", async () => {
    const fetchFalso = responder(201, SESSAO);

    await iniciarSessao("DOCUMENTO", "2026-11-28");

    const { url, init } = chamada(fetchFalso);
    expect(url).toBe("/api/conta/verificacao/sessao");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(
      '{"consentimentoBiometrico":true,"versaoConsentimento":"2026-11-28","finalidade":"DOCUMENTO"}',
    );
  });

  it("iniciarSessaoConverteExpiraEmEmDate", async () => {
    responder(201, SESSAO);

    const sessao = await iniciarSessao("SELFIE", "2026-11-28");

    expect(sessao.credencial.expiration).toBeInstanceOf(Date);
    expect(sessao.credencial.expiration.toISOString()).toBe("2026-11-27T12:15:00.000Z");
    expect(sessao).toMatchObject({ sessaoId: "abc", regiao: "us-east-1", modo: "aws" });
    expect(sessao.credencial).toMatchObject({
      accessKeyId: "ASIAFALSA",
      secretAccessKey: "segredo-falso",
      sessionToken: "token-falso",
    });
  });

  it("iniciarSessaoRecusaOutraRegiao", async () => {
    responder(201, { ...SESSAO, regiao: "sa-east-1" });

    await expect(iniciarSessao("SELFIE", "2026-11-28")).rejects.toMatchObject({
      name: "ErroHttp",
      codigo: "RESPOSTA_INVALIDA",
    });
  });

  it("iniciarSessaoCalculaODesvioPeloCabecalhoDate", async () => {
    vi.setSystemTime(AGORA);
    responder(201, SESSAO, { Date: new Date(AGORA + 600_000).toUTCString() });

    const sessao = await iniciarSessao("SELFIE", "2026-11-28");

    expect(sessao.desvioDoRelogioMs).toBe(600_000);
  });

  it("erroDoServidorViraErroHttpComCodigo", async () => {
    responder(503, { codigo: "PROVA_DE_VIDA_INDISPONIVEL", mensagem: "A verificação está fora do ar." });

    const erro: unknown = await iniciarSessao("SELFIE", "2026-11-28").catch((causa: unknown) => causa);

    expect(erro).toBeInstanceOf(ErroHttp);
    expect(erro).toMatchObject({ status: 503, codigo: "PROVA_DE_VIDA_INDISPONIVEL", transitorio: true });
  });
});

describe("desvioDoRelogio", () => {
  it("desvioDoRelogioSoAcimaDeCincoMinutos", () => {
    const servidor = (segundos: number) => new Date(AGORA + segundos * 1000).toUTCString();

    expect(desvioDoRelogio(servidor(-299), AGORA)).toBeUndefined();
    expect(desvioDoRelogio(servidor(-300), AGORA)).toBe(-300_000);
    expect(desvioDoRelogio(servidor(600), AGORA)).toBe(600_000);
    expect(desvioDoRelogio(null, AGORA)).toBeUndefined();
  });

  it("dataIlegivelNaoGeraDesvio", () => {
    expect(desvioDoRelogio("ontem", AGORA)).toBeUndefined();
  });
});

describe("concluirSelfie", () => {
  it("concluirSelfiePostaNoCaminhoDaSessao", async () => {
    const fetchFalso = responder(200, DESFECHO);

    await concluirSelfie("abc");

    const { url, init } = chamada(fetchFalso);
    expect(url).toBe("/api/conta/verificacao/sessao/abc/resultado");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBeUndefined();
  });

  it("desfechoTrazAsContagens", async () => {
    responder(200, DESFECHO);

    const desfecho = await concluirSelfie("abc");

    expect(desfecho).toEqual(DESFECHO);
  });

  it("desfechoForaDoFormatoViraRespostaInvalida", async () => {
    responder(200, { ...DESFECHO, resultado: "RECUSADA" });

    await expect(concluirSelfie("abc")).rejects.toMatchObject({ name: "ErroHttp", codigo: "RESPOSTA_INVALIDA" });
  });
});

describe("enviarDocumento", () => {
  it("enviarDocumentoMandaSessaoFrenteEVerso", async () => {
    const fetchFalso = responder(200, { ...DESFECHO, resultado: "APROVADA", pedeDocumento: false });
    const frente = new Blob([new Uint8Array([0xff, 0xd8, 0xff])], { type: "image/jpeg" });
    const verso = new Blob([new Uint8Array([0xff, 0xd8, 0xff])], { type: "image/jpeg" });

    await enviarDocumento({ sessaoId: "abc", frente, verso });

    const { url, init } = chamada(fetchFalso);
    expect(url).toBe("/api/conta/verificacao/documento");
    expect(init?.method).toBe("POST");
    const formulario = init?.body as FormData;
    expect([...formulario.keys()]).toEqual(["sessaoId", "frente", "verso"]);
    expect(formulario.get("sessaoId")).toBe("abc");
    expect((formulario.get("frente") as File).type).toBe("image/jpeg");
    expect((formulario.get("verso") as File).type).toBe("image/jpeg");
  });
});

describe("codigoDaVerificacao", () => {
  it("reconheceForaDeOrdemELimiteDeTaxa", () => {
    const foraDeOrdem = new ErroHttp({ status: 409, codigo: "VERIFICACAO_FORA_DE_ORDEM" });
    const limiteDeTaxa = new ErroHttp({ status: 429, codigo: "LIMITE_DE_TAXA" });

    expect(codigoDaVerificacao(foraDeOrdem)).toBe("VERIFICACAO_FORA_DE_ORDEM");
    expect(codigoDaVerificacao(limiteDeTaxa)).toBe("LIMITE_DE_TAXA");
  });

  it("codigoAlheioOuErroQualquerDaUndefined", () => {
    expect(codigoDaVerificacao(new ErroHttp({ status: 500, codigo: "SERVIDOR" }))).toBeUndefined();
    expect(codigoDaVerificacao(new Error("x"))).toBeUndefined();
  });
});
