import { describe, expect, it } from "vitest";
import { ErroHttp } from "../../api/http";
import type { Desfecho, SessaoDeCaptura, SituacaoDaVerificacao } from "../../api/verificacao";
import { etapaInicial, proximaEtapa, type Etapa, type FalhaDoDetector, type FotosDoDocumento } from "./etapas";

function situacao(campos: Partial<SituacaoDaVerificacao>): SituacaoDaVerificacao {
  return {
    estado: "AGUARDANDO",
    tentativasRestantesHoje: 3,
    enviosDeDocumentoRestantesHoje: 2,
    modo: "aws",
    ...campos,
  };
}

const SESSAO: SessaoDeCaptura = {
  sessaoId: "abc",
  regiao: "us-east-1",
  modo: "aws",
  credencial: { accessKeyId: "AKIA", secretAccessKey: "segredo", sessionToken: "token", expiration: new Date(0) },
  desvioDoRelogioMs: undefined,
};

const FRENTE = new Blob(["frente"], { type: "image/jpeg" });
const VERSO = new Blob(["verso"], { type: "image/jpeg" });
const FOTOS: FotosDoDocumento = { frente: FRENTE, verso: VERSO };
const SESSAO_DO_DOCUMENTO: SessaoDeCaptura = { ...SESSAO, sessaoId: "s2" };

describe("etapaInicial", () => {
  it.each<[string, Partial<SituacaoDaVerificacao>, Etapa | null]>([
    [
      "aguardandoComTentativasAbreNaExplicacao",
      { estado: "AGUARDANDO", tentativasRestantesHoje: 1 },
      { etapa: "explicacao" },
    ],
    [
      "aguardandoSemTentativasAbreNoLimite",
      { estado: "AGUARDANDO", tentativasRestantesHoje: 0 },
      { etapa: "limite", segundos: null },
    ],
    ["duvidaComEnviosAbreNoDocumento", { estado: "DUVIDA", enviosDeDocumentoRestantesHoje: 1 }, { etapa: "documento" }],
    [
      "duvidaSemEnviosAbreNaAnalise",
      { estado: "DUVIDA", enviosDeDocumentoRestantesHoje: 0 },
      { etapa: "desfecho", tipo: "analise" },
    ],
    [
      "analiseComEnviosAbreNaAnalise",
      { estado: "ANALISE", enviosDeDocumentoRestantesHoje: 1 },
      { etapa: "desfecho", tipo: "analise" },
    ],
    ["aprovadaNaoTemEtapaInicial", { estado: "APROVADA" }, null],
  ])("%s", (_nome, campos, esperada) => {
    expect(etapaInicial(situacao(campos))).toEqual(esperada);
  });
});

describe("proximaEtapa", () => {
  it("consentimentoComCameraOkIniciaSessao", () => {
    const checando = proximaEtapa({ etapa: "explicacao" }, { tipo: "comecar", modo: "aws" });

    expect(checando).toEqual({ etapa: "checandoCamera", finalidade: "SELFIE" });
    expect(proximaEtapa(checando, { tipo: "cameraChecada", resultado: "ok" })).toEqual({
      etapa: "iniciando",
      finalidade: "SELFIE",
    });
  });

  it("modoFalsoIniciaSemChecarACamera", () => {
    expect(proximaEtapa({ etapa: "explicacao" }, { tipo: "comecar", modo: "falso" })).toEqual({
      etapa: "iniciando",
      finalidade: "SELFIE",
    });
  });

  it("cameraNegadaNaoGastaTentativa", () => {
    const semCamera = proximaEtapa(
      { etapa: "checandoCamera", finalidade: "SELFIE" },
      { tipo: "cameraChecada", resultado: "negada" },
    );

    expect(semCamera).toEqual({ etapa: "semCamera", motivo: "negada", finalidade: "SELFIE" });
    expect(proximaEtapa(semCamera, { tipo: "tentarDeNovo" })).toEqual({
      etapa: "checandoCamera",
      finalidade: "SELFIE",
    });
  });

  it("sessaoCriadaAbreACaptura", () => {
    expect(
      proximaEtapa({ etapa: "iniciando", finalidade: "SELFIE" }, { tipo: "sessaoCriada", sessao: SESSAO }),
    ).toEqual({
      etapa: "captura",
      sessao: SESSAO,
      finalidade: "SELFIE",
    });
  });

  it("servico503VaiParaIndisponivelComRepeticao", () => {
    const erro = new ErroHttp({ status: 503, codigo: "PROVA_DE_VIDA_INDISPONIVEL" });

    const indisponivel = proximaEtapa({ etapa: "iniciando", finalidade: "SELFIE" }, { tipo: "sessaoRecusada", erro });

    expect(indisponivel).toEqual({ etapa: "indisponivel", repetir: { tipo: "iniciar", finalidade: "SELFIE" } });
    expect(proximaEtapa(indisponivel, { tipo: "tentarDeNovo" })).toEqual({ etapa: "iniciando", finalidade: "SELFIE" });
  });

  it("falhaDeRedeVaiParaIndisponivel", () => {
    const erro = new ErroHttp({ status: 0, codigo: "SEM_CONEXAO" });

    expect(proximaEtapa({ etapa: "iniciando", finalidade: "SELFIE" }, { tipo: "sessaoRecusada", erro })).toEqual({
      etapa: "indisponivel",
      repetir: { tipo: "iniciar", finalidade: "SELFIE" },
    });
  });

  it("limite429GuardaOsSegundos", () => {
    const erro = new ErroHttp({ status: 429, codigo: "LIMITE_DE_TAXA", tentarDeNovoEmSegundos: 120 });

    expect(proximaEtapa({ etapa: "iniciando", finalidade: "SELFIE" }, { tipo: "sessaoRecusada", erro })).toEqual({
      etapa: "limite",
      segundos: 120,
    });
  });

  it("limiteDeVerificacaoEOLimiteDoDia", () => {
    const erro = new ErroHttp({ status: 429, codigo: "LIMITE_DE_VERIFICACAO", tentarDeNovoEmSegundos: 120 });

    expect(proximaEtapa({ etapa: "iniciando", finalidade: "SELFIE" }, { tipo: "sessaoRecusada", erro })).toEqual({
      etapa: "limite",
      segundos: null,
    });
  });

  it("limiteDeTaxaSemRetryAfterEsperaUmMinuto", () => {
    const erro = new ErroHttp({ status: 429, codigo: "LIMITE_DE_TAXA" });

    expect(proximaEtapa({ etapa: "iniciando", finalidade: "SELFIE" }, { tipo: "sessaoRecusada", erro })).toEqual({
      etapa: "limite",
      segundos: 60,
    });
  });

  it("concluirSelfieVaiParaConferindo", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO, finalidade: "SELFIE" };

    expect(proximaEtapa(captura, { tipo: "concluiu" })).toEqual({ etapa: "conferindo", sessaoId: SESSAO.sessaoId });
  });

  // A selfie do documento segue para o envio das fotos, não para a conferência da selfie.
  it("concluirCapturaDoDocumentoEnviaAsFotosComASessaoNova", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO_DO_DOCUMENTO, finalidade: "DOCUMENTO", fotos: FOTOS };

    expect(proximaEtapa(captura, { tipo: "concluiu" })).toEqual({
      etapa: "enviandoDocumento",
      sessaoId: "s2",
      fotos: FOTOS,
    });
  });

  it("falhaDoDetectorVaiParaAFalha", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO, finalidade: "SELFIE" };

    expect(proximaEtapa(captura, { tipo: "falhou", falha: "outraPessoa" })).toEqual({
      etapa: "falhaDoDetector",
      falha: "outraPessoa",
      finalidade: "SELFIE",
    });
  });

  // Falha de infraestrutura não custa tentativa: a sessão da AWS segue valendo e volta inteira.
  it("carregamentoRepeteComAMesmaSessao", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO, finalidade: "SELFIE" };

    const falha = proximaEtapa(captura, { tipo: "falhou", falha: "carregamento" });

    expect(falha).toEqual({ etapa: "falhaDoDetector", falha: "carregamento", finalidade: "SELFIE", sessao: SESSAO });
    expect(proximaEtapa(falha, { tipo: "comecar", modo: "aws" })).toEqual(captura);
  });

  it.each<FalhaDoDetector>(["camera", "tempo", "servico"])("falha %s guarda a finalidade sem a sessão", (falha) => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO, finalidade: "DOCUMENTO", fotos: FOTOS };

    expect(proximaEtapa(captura, { tipo: "falhou", falha })).toEqual({
      etapa: "falhaDoDetector",
      falha,
      finalidade: "DOCUMENTO",
      fotos: FOTOS,
    });
  });

  it("falhaNaSelfieDoDocumentoRecomecaComoDocumento", () => {
    const falha: Etapa = { etapa: "falhaDoDetector", falha: "tempo", finalidade: "DOCUMENTO", fotos: FOTOS };

    expect(proximaEtapa(falha, { tipo: "comecar", modo: "aws" })).toEqual({
      etapa: "checandoCamera",
      finalidade: "DOCUMENTO",
      fotos: FOTOS,
    });
  });

  it("cancelarVoltaParaExplicacao", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO, finalidade: "SELFIE" };

    expect(proximaEtapa(captura, { tipo: "cancelou" })).toEqual({ etapa: "explicacao" });
  });

  it("eventoForaDaEtapaNaoMudaNada", () => {
    const iniciando: Etapa = { etapa: "iniciando", finalidade: "SELFIE" };

    expect(proximaEtapa(iniciando, { tipo: "comecar", modo: "falso" })).toBe(iniciando);
  });
});

function desfecho(campos: Partial<Desfecho>): Desfecho {
  return {
    resultado: "APROVADA",
    pedeDocumento: false,
    tentativasRestantesHoje: 2,
    enviosDeDocumentoRestantesHoje: 2,
    ...campos,
  };
}

describe("conferência da selfie", () => {
  const conferindo: Etapa = { etapa: "conferindo", sessaoId: "abc" };

  function recusa(status: number, codigo: string, tentarDeNovoEmSegundos: number | null = null): Etapa {
    const erro = new ErroHttp({ status, codigo, tentarDeNovoEmSegundos });
    return proximaEtapa(conferindo, { tipo: "conferenciaRecusada", erro });
  }

  it("aprovadaVaiParaODesfechoAprovada", () => {
    expect(proximaEtapa(conferindo, { tipo: "conferido", desfecho: desfecho({}) })).toEqual({
      etapa: "desfecho",
      tipo: "aprovada",
    });
  });

  it("repetirVaiParaODesfechoRepetir", () => {
    expect(proximaEtapa(conferindo, { tipo: "conferido", desfecho: desfecho({ resultado: "REPETIR" }) })).toEqual({
      etapa: "desfecho",
      tipo: "repetir",
    });
  });

  it("duvidaComDocumentoVaiParaODocumento", () => {
    const duvida = desfecho({ resultado: "DUVIDA", pedeDocumento: true });

    expect(proximaEtapa(conferindo, { tipo: "conferido", desfecho: duvida })).toEqual({ etapa: "documento" });
  });

  it("duvidaSemDocumentoVaiParaAnalise", () => {
    const duvida = desfecho({ resultado: "DUVIDA", pedeDocumento: false, enviosDeDocumentoRestantesHoje: 0 });

    expect(proximaEtapa(conferindo, { tipo: "conferido", desfecho: duvida })).toEqual({
      etapa: "desfecho",
      tipo: "analise",
    });
  });

  it("menorDeIdadeVaiParaODesfechoMenor", () => {
    expect(recusa(422, "MENOR_DE_IDADE")).toEqual({ etapa: "desfecho", tipo: "menor" });
  });

  it("sessaoInvalidaVaiParaODesfechoSessaoInvalida", () => {
    expect(recusa(404, "SESSAO_DE_VERIFICACAO_INVALIDA")).toEqual({ etapa: "desfecho", tipo: "sessaoInvalida" });
  });

  it("servico503RepeteComOMesmoSessaoId", () => {
    const indisponivel = recusa(503, "PROVA_DE_VIDA_INDISPONIVEL");

    expect(indisponivel).toEqual({ etapa: "indisponivel", repetir: { tipo: "conferir", sessaoId: "abc" } });
    expect(proximaEtapa(indisponivel, { tipo: "tentarDeNovo" })).toEqual(conferindo);
  });

  it("emAndamentoRepeteComOMesmoSessaoId", () => {
    const indisponivel = recusa(409, "REQUISICAO_EM_ANDAMENTO", 1);

    expect(indisponivel).toEqual({ etapa: "indisponivel", repetir: { tipo: "conferir", sessaoId: "abc" } });
    expect(proximaEtapa(indisponivel, { tipo: "tentarDeNovo" })).toEqual(conferindo);
  });

  it("falhaDeRedeRepeteComOMesmoSessaoId", () => {
    expect(recusa(0, "SEM_CONEXAO")).toEqual({ etapa: "indisponivel", repetir: { tipo: "conferir", sessaoId: "abc" } });
  });

  // D13-bis: só o 429 de taxa usa o Retry-After; o do dia mostra o texto do limite do dia.
  it("limiteDeTaxaGuardaOsSegundos", () => {
    expect(recusa(429, "LIMITE_DE_TAXA", 90)).toEqual({ etapa: "limite", segundos: 90 });
  });

  it("limiteDeVerificacaoEOLimiteDoDia", () => {
    expect(recusa(429, "LIMITE_DE_VERIFICACAO", 3600)).toEqual({ etapa: "limite", segundos: null });
  });

  it("eventoForaDaConferenciaNaoMudaNada", () => {
    expect(proximaEtapa(conferindo, { tipo: "tentarDeNovo" })).toBe(conferindo);
  });
});

describe("depois da conferência", () => {
  it.each<FalhaDoDetector>(["camera", "tempo", "conexao"])("falha %s com a AWS checa a câmera de novo", (falha) => {
    const etapa: Etapa = { etapa: "falhaDoDetector", falha, finalidade: "SELFIE" };

    expect(proximaEtapa(etapa, { tipo: "comecar", modo: "aws" })).toEqual({
      etapa: "checandoCamera",
      finalidade: "SELFIE",
    });
  });

  it("falhaNoModoFalsoIniciaSemChecarACamera", () => {
    const etapa: Etapa = { etapa: "falhaDoDetector", falha: "rosto", finalidade: "SELFIE" };

    expect(proximaEtapa(etapa, { tipo: "comecar", modo: "falso" })).toEqual({
      etapa: "iniciando",
      finalidade: "SELFIE",
    });
  });

  it("repetirComecaUmaNovaSelfie", () => {
    expect(proximaEtapa({ etapa: "desfecho", tipo: "repetir" }, { tipo: "comecar", modo: "aws" })).toEqual({
      etapa: "checandoCamera",
      finalidade: "SELFIE",
    });
  });

  it("sessaoInvalidaVoltaParaExplicacao", () => {
    expect(proximaEtapa({ etapa: "desfecho", tipo: "sessaoInvalida" }, { tipo: "tentarDeNovo" })).toEqual({
      etapa: "explicacao",
    });
  });

  it.each(["aprovada", "analise", "menor"] as const)("desfecho %s é final", (tipo) => {
    const final: Etapa = { etapa: "desfecho", tipo };

    expect(proximaEtapa(final, { tipo: "comecar", modo: "aws" })).toBe(final);
    expect(proximaEtapa(final, { tipo: "tentarDeNovo" })).toBe(final);
  });
});

describe("documento", () => {
  const enviando: Etapa = { etapa: "enviandoDocumento", sessaoId: "s2", fotos: FOTOS };

  function envio(campos: Partial<Desfecho>): Desfecho {
    return {
      resultado: "APROVADA",
      pedeDocumento: false,
      tentativasRestantesHoje: 0,
      enviosDeDocumentoRestantesHoje: 1,
      ...campos,
    };
  }

  function recusa(status: number, codigo: string, tentarDeNovoEmSegundos?: number): ErroHttp {
    return new ErroHttp({ status, codigo, tentarDeNovoEmSegundos });
  }

  it("fotoDaFrenteFicaNaEtapa", () => {
    expect(proximaEtapa({ etapa: "documento" }, { tipo: "foto", lado: "frente", foto: FRENTE })).toEqual({
      etapa: "documento",
      frente: FRENTE,
    });
  });

  it("fotoIlegivelTiraAFotoDoLado", () => {
    const documento: Etapa = { etapa: "documento", frente: FRENTE, verso: VERSO };

    expect(proximaEtapa(documento, { tipo: "foto", lado: "verso", foto: null })).toEqual({
      etapa: "documento",
      frente: FRENTE,
    });
  });

  it("fotoNovaLimpaOAviso", () => {
    const documento: Etapa = { etapa: "documento", frente: FRENTE, verso: VERSO, aviso: "selfieVenceu" };

    expect(proximaEtapa(documento, { tipo: "foto", lado: "verso", foto: VERSO })).toEqual({
      etapa: "documento",
      frente: FRENTE,
      verso: VERSO,
    });
  });

  it("falhaNaSelfieDoDocumentoSemEnvioVaiParaAAnalise", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO_DO_DOCUMENTO, finalidade: "DOCUMENTO", fotos: FOTOS };

    expect(proximaEtapa(captura, { tipo: "falhou", falha: "tempo", semEnvio: true })).toEqual({
      etapa: "desfecho",
      tipo: "analise",
    });
  });

  it("falhaDeCarregamentoSemEnvioGuardaASessao", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO_DO_DOCUMENTO, finalidade: "DOCUMENTO", fotos: FOTOS };

    expect(proximaEtapa(captura, { tipo: "falhou", falha: "carregamento", semEnvio: true })).toEqual({
      etapa: "falhaDoDetector",
      falha: "carregamento",
      sessao: SESSAO_DO_DOCUMENTO,
      finalidade: "DOCUMENTO",
      fotos: FOTOS,
    });
  });

  it("selfieSemAsDuasFotosNaoComeca", () => {
    const documento: Etapa = { etapa: "documento", frente: FRENTE };

    expect(proximaEtapa(documento, { tipo: "comecar", modo: "aws" })).toBe(documento);
  });

  it("selfieDoDocumentoUsaFinalidadeDocumento", () => {
    const documento: Etapa = { etapa: "documento", frente: FRENTE, verso: VERSO, aviso: "selfieVenceu" };

    expect(proximaEtapa(documento, { tipo: "comecar", modo: "aws" })).toEqual({
      etapa: "checandoCamera",
      finalidade: "DOCUMENTO",
      fotos: FOTOS,
    });
  });

  it("selfieDoDocumentoNoModoFalsoIniciaDireto", () => {
    const documento: Etapa = { etapa: "documento", frente: FRENTE, verso: VERSO };

    expect(proximaEtapa(documento, { tipo: "comecar", modo: "falso" })).toEqual({
      etapa: "iniciando",
      finalidade: "DOCUMENTO",
      fotos: FOTOS,
    });
  });

  it("cameraChecadaLevaAsFotos", () => {
    const checando: Etapa = { etapa: "checandoCamera", finalidade: "DOCUMENTO", fotos: FOTOS };

    expect(proximaEtapa(checando, { tipo: "cameraChecada", resultado: "ok" })).toEqual({
      etapa: "iniciando",
      finalidade: "DOCUMENTO",
      fotos: FOTOS,
    });
  });

  it("semCameraRepeteComAsFotos", () => {
    const semCamera: Etapa = { etapa: "semCamera", motivo: "negada", finalidade: "DOCUMENTO", fotos: FOTOS };

    expect(proximaEtapa(semCamera, { tipo: "tentarDeNovo" })).toEqual({
      etapa: "checandoCamera",
      finalidade: "DOCUMENTO",
      fotos: FOTOS,
    });
  });

  it("sessaoDoDocumentoVaiParaACaptura", () => {
    const iniciando: Etapa = { etapa: "iniciando", finalidade: "DOCUMENTO", fotos: FOTOS };

    expect(proximaEtapa(iniciando, { tipo: "sessaoCriada", sessao: SESSAO_DO_DOCUMENTO })).toEqual({
      etapa: "captura",
      sessao: SESSAO_DO_DOCUMENTO,
      finalidade: "DOCUMENTO",
      fotos: FOTOS,
    });
  });

  it("sessaoDoDocumentoForaDoArRepeteComAsFotos", () => {
    const iniciando: Etapa = { etapa: "iniciando", finalidade: "DOCUMENTO", fotos: FOTOS };

    const fora = proximaEtapa(iniciando, { tipo: "sessaoRecusada", erro: recusa(503, "PROVA_DE_VIDA_INDISPONIVEL") });

    expect(proximaEtapa(fora, { tipo: "tentarDeNovo" })).toEqual(iniciando);
  });

  it("cancelarASelfieDoDocumentoVoltaParaAsFotos", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO_DO_DOCUMENTO, finalidade: "DOCUMENTO", fotos: FOTOS };

    expect(proximaEtapa(captura, { tipo: "cancelou" })).toEqual({ etapa: "documento", frente: FRENTE, verso: VERSO });
  });

  it("aprovadaNoEnvioVaiParaOAprovado", () => {
    expect(proximaEtapa(enviando, { tipo: "enviado", desfecho: envio({ resultado: "APROVADA" }) })).toEqual({
      etapa: "desfecho",
      tipo: "aprovada",
    });
  });

  it("duvidaNoEnvioVaiParaAAnalise", () => {
    expect(proximaEtapa(enviando, { tipo: "enviado", desfecho: envio({ resultado: "DUVIDA" }) })).toEqual({
      etapa: "desfecho",
      tipo: "analise",
    });
  });

  it("repetirNoEnvioApagaAsFotosEAvisaQueNaoLeu", () => {
    expect(proximaEtapa(enviando, { tipo: "enviado", desfecho: envio({ resultado: "REPETIR" }) })).toEqual({
      etapa: "documento",
      aviso: "naoLeu",
    });
  });

  it("repetirSemEnviosVaiParaAAnalise", () => {
    const desfecho = envio({ resultado: "REPETIR", enviosDeDocumentoRestantesHoje: 0 });

    expect(proximaEtapa(enviando, { tipo: "enviado", desfecho })).toEqual({ etapa: "desfecho", tipo: "analise" });
  });

  it("refazerProvaDeVidaMantemAsFotos", () => {
    expect(proximaEtapa(enviando, { tipo: "envioRecusado", erro: recusa(409, "REFAZER_PROVA_DE_VIDA") })).toEqual({
      etapa: "documento",
      frente: FRENTE,
      verso: VERSO,
      aviso: "selfieVenceu",
    });
  });

  it("selfieQueNaoDeuCertoMantemAsFotosComOutroAviso", () => {
    expect(proximaEtapa(enviando, { tipo: "envioRecusado", erro: recusa(409, "SELFIE_NAO_DEU_CERTO") })).toEqual({
      etapa: "documento",
      frente: FRENTE,
      verso: VERSO,
      aviso: "selfieNaoDeuCerto",
    });
  });

  it("sessaoDoEnvioVencidaMantemAsFotos", () => {
    const erro = recusa(404, "SESSAO_DE_VERIFICACAO_INVALIDA");

    expect(proximaEtapa(enviando, { tipo: "envioRecusado", erro })).toEqual({
      etapa: "documento",
      frente: FRENTE,
      verso: VERSO,
      aviso: "selfieVenceu",
    });
  });

  it("documentoInvalidoApagaAsFotos", () => {
    expect(proximaEtapa(enviando, { tipo: "envioRecusado", erro: recusa(422, "DOCUMENTO_INVALIDO") })).toEqual({
      etapa: "documento",
      aviso: "fotosIlegiveis",
    });
  });

  it("fotosGrandesDemaisApagaAsFotos", () => {
    expect(proximaEtapa(enviando, { tipo: "envioRecusado", erro: recusa(413, "REQUISICAO_INVALIDA") })).toEqual({
      etapa: "documento",
      aviso: "fotosGrandes",
    });
  });

  it("menorNoEnvioEncerra", () => {
    expect(proximaEtapa(enviando, { tipo: "envioRecusado", erro: recusa(422, "MENOR_DE_IDADE") })).toEqual({
      etapa: "desfecho",
      tipo: "menor",
    });
  });

  it("limiteDeTaxaNoEnvioGuardaOsSegundos", () => {
    expect(proximaEtapa(enviando, { tipo: "envioRecusado", erro: recusa(429, "LIMITE_DE_TAXA", 40) })).toEqual({
      etapa: "limite",
      segundos: 40,
    });
  });

  it("limiteDeVerificacaoNoEnvioEOLimiteDoDia", () => {
    expect(proximaEtapa(enviando, { tipo: "envioRecusado", erro: recusa(429, "LIMITE_DE_VERIFICACAO") })).toEqual({
      etapa: "limite",
      segundos: null,
    });
  });

  it.each([
    ["servico503", recusa(503, "CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL")],
    ["emAndamento409", recusa(409, "REQUISICAO_EM_ANDAMENTO")],
    ["rede", new ErroHttp({ status: 0, codigo: "REDE" })],
  ])("%s reenvia as mesmas fotos com a mesma sessão", (_nome, erro) => {
    const fora = proximaEtapa(enviando, { tipo: "envioRecusado", erro });

    expect(fora).toEqual({ etapa: "indisponivel", repetir: { tipo: "enviar", sessaoId: "s2", fotos: FOTOS } });
    expect(proximaEtapa(fora, { tipo: "tentarDeNovo" })).toEqual(enviando);
  });

  it("eventoForaDoEnvioNaoMudaNada", () => {
    expect(proximaEtapa(enviando, { tipo: "comecar", modo: "aws" })).toBe(enviando);
  });
});
