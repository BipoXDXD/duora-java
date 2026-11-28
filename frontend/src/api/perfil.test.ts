import { afterEach, describe, expect, it, vi } from "vitest";
import { ErroHttp } from "./http";
import {
  apagarNamoro,
  ativarLocalizacao,
  enviarFoto,
  obterMeuPerfil,
  obterNamoro,
  obterOrientacao,
  obterPerfilPublico,
  salvarOrientacao,
  salvarPerfil,
  versaoObsoleta,
} from "./perfil";

const FOTO_APROVADA = { nitida: "/api/fotos/a1/nitida", borrada: "/api/fotos/a1/borrada" };
const FOTO_PENDENTE = { nitida: "/api/fotos/p2/nitida", borrada: "/api/fotos/p2/borrada" };

const PERFIL = {
  versao: 3,
  apelido: "Ana",
  pronomes: "ELA_DELA",
  descricaoPronomes: null,
  intencoes: ["AMIZADE", "NAMORO"],
  bio: "Gosto de café.",
  pausada: false,
  prontoParaFila: true,
  pendencias: [],
  foto: { aprovada: FOTO_APROVADA, pendente: FOTO_PENDENTE, recusadaEm: null },
};

const ORIENTACAO = {
  orientacao: "BISSEXUAL",
  descricaoOrientacao: null,
  mostrarOrientacao: true,
  consentidaEm: "2026-11-28T12:00:00Z",
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
  return { url, init, cabecalhos: init?.headers as Record<string, string> };
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

describe("obterMeuPerfil", () => {
  it("obterMeuPerfilLeFotoComNitidaEBorrada", async () => {
    const fetchFalso = responder(200, PERFIL);

    const perfil = await obterMeuPerfil();

    expect(perfil).toEqual(PERFIL);
    expect(chamada(fetchFalso).url).toBe("/api/perfil");
    expect(chamada(fetchFalso).init?.method).toBe("GET");
  });

  it("perfilSemFotoTemAprovadaEPendenteNulas", async () => {
    const semPerfil = {
      versao: null,
      apelido: null,
      pronomes: null,
      descricaoPronomes: null,
      intencoes: [],
      bio: "",
      pausada: false,
      prontoParaFila: false,
      pendencias: ["PERFIL"],
      foto: { aprovada: null, pendente: null, recusadaEm: null },
    };
    responder(200, semPerfil);

    expect(await obterMeuPerfil()).toEqual(semPerfil);
  });

  it("pronomesForaDaListaViramRespostaInvalida", async () => {
    responder(200, { ...PERFIL, pronomes: "ELES_DELES" });

    const erro = await erroDe(obterMeuPerfil());

    expect(erro).toMatchObject({ status: 200, codigo: "RESPOSTA_INVALIDA" });
  });
});

describe("namoro", () => {
  it("generoGranularViraRespostaInvalida", async () => {
    responder(200, {
      genero: "MULHER_TRANS",
      descricaoGenero: null,
      interesses: ["HOMEM"],
      consentidoEm: "2026-11-28T12:00:00Z",
    });

    const erro = await erroDe(obterNamoro());

    expect(erro.codigo).toBe("RESPOSTA_INVALIDA");
  });

  it("obterNamoroSemDadosViraNull", async () => {
    responder(200, { genero: null, descricaoGenero: null, interesses: [], consentidoEm: null });

    expect(await obterNamoro()).toBeNull();
  });

  it("apagarNamoroUsaDelete", async () => {
    const fetchFalso = responder(204);

    await apagarNamoro();

    expect(chamada(fetchFalso).url).toBe("/api/perfil/namoro");
    expect(chamada(fetchFalso).init?.method).toBe("DELETE");
  });
});

describe("salvarPerfil", () => {
  it("salvarPerfilMandaVersaoNulaEPronomes", async () => {
    const fetchFalso = responder(200, { ...PERFIL, versao: 1 });
    const edicao = {
      versao: null,
      apelido: "Ana",
      pronomes: "OUTROS",
      descricaoPronomes: "ela/delu",
      intencoes: ["AMIZADE"],
      bio: "",
    } as const;

    const perfil = await salvarPerfil({ ...edicao, intencoes: [...edicao.intencoes] });

    const { url, init } = chamada(fetchFalso);
    expect(url).toBe("/api/perfil");
    expect(init?.method).toBe("PUT");
    expect(JSON.parse(init?.body as string)).toEqual(edicao);
    expect(perfil.versao).toBe(1);
  });

  it("versaoObsoletaReconhece409", () => {
    const obsoleta = new ErroHttp({ status: 409, codigo: "VERSAO_OBSOLETA" });
    const chaveReusada = new ErroHttp({ status: 409, codigo: "CHAVE_DE_IDEMPOTENCIA_REUSADA" });

    expect(versaoObsoleta(obsoleta)).toBe(true);
    expect(versaoObsoleta(chaveReusada)).toBe(false);
  });
});

describe("enviarFoto", () => {
  it("enviarFotoMandaMultipartComChave", async () => {
    const fetchFalso = responder(202, { estado: "PENDENTE", url: "/api/fotos/x/nitida" });
    const arquivo = new Blob(["bytes"], { type: "image/jpeg" });

    await enviarFoto(arquivo, "chave-12345678");

    const { url, init, cabecalhos } = chamada(fetchFalso);
    expect(url).toBe("/api/perfil/foto");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBeInstanceOf(FormData);
    expect((init?.body as FormData).get("arquivo")).toBeInstanceOf(Blob);
    expect(cabecalhos["Idempotency-Key"]).toBe("chave-12345678");
    expect(Object.keys(cabecalhos).map((nome) => nome.toLowerCase())).not.toContain("content-type");
  });
});

describe("orientacao", () => {
  it("obterOrientacaoSemDadosViraNull", async () => {
    responder(200, { orientacao: null, descricaoOrientacao: null, mostrarOrientacao: false, consentidaEm: null });

    expect(await obterOrientacao()).toBeNull();
  });

  it("orientacaoUsaRotaOrientacao", async () => {
    const fetchFalso = responder(200, ORIENTACAO);

    const dados = await salvarOrientacao({
      orientacao: "BISSEXUAL",
      descricaoOrientacao: null,
      mostrarOrientacao: true,
      consentimento: true,
    });

    expect(chamada(fetchFalso).url).toBe("/api/perfil/orientacao");
    expect(chamada(fetchFalso).init?.method).toBe("PUT");
    expect(dados).toEqual(ORIENTACAO);
  });
});

describe("obterPerfilPublico", () => {
  it("obterPerfilPublico404ViraNull", async () => {
    responder(404, { codigo: "PERFIL_INEXISTENTE", mensagem: "Não encontramos este perfil." });

    expect(await obterPerfilPublico("0193f1c2-7a4b-7c3d-8e5f-0a1b2c3d4e5f")).toBeNull();
  });

  it("obterPerfilPublicoIdMalformadoViraNull", async () => {
    responder(400, { codigo: "REQUISICAO_INVALIDA", mensagem: "Requisição inválida" });

    expect(await obterPerfilPublico("nao-e-uuid")).toBeNull();
  });

  it("obterPerfilPublicoOutro400Propaga", async () => {
    responder(400, { codigo: "CORPO_INVALIDO", mensagem: "Corpo inválido" });

    await expect(obterPerfilPublico("nao-e-uuid")).rejects.toMatchObject({ status: 400, codigo: "CORPO_INVALIDO" });
  });

  it("perfilPublicoComChaveExtraContinuaValido", async () => {
    const fetchFalso = responder(200, {
      apelido: "Bia",
      foto: "/api/fotos/b3/borrada",
      pronomes: "elu/delu",
      verificado: true,
      novidade: 1,
    });

    const perfil = await obterPerfilPublico("0193f1c2-7a4b-7c3d-8e5f-0a1b2c3d4e5f");

    expect(perfil).toEqual({ apelido: "Bia", foto: "/api/fotos/b3/borrada", pronomes: "elu/delu", verificado: true });
    expect(chamada(fetchFalso).url).toBe("/api/perfis/0193f1c2-7a4b-7c3d-8e5f-0a1b2c3d4e5f");
  });
});

describe("ativarLocalizacao", () => {
  it("ativarLocalizacaoMandaSoLatitudeELongitude", async () => {
    const fetchFalso = responder(204);
    const comExtra = { latitude: -23.55, longitude: -46.63, precisao: 12 };

    await ativarLocalizacao(comExtra);

    const { url, init } = chamada(fetchFalso);
    expect(url).toBe("/api/perfil/localizacao");
    expect(init?.method).toBe("PUT");
    expect(JSON.parse(init?.body as string)).toEqual({ latitude: -23.55, longitude: -46.63 });
  });
});
