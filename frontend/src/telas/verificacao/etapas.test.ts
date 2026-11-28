import { describe, expect, it } from "vitest";
import { ErroHttp } from "../../api/http";
import type { SessaoDeCaptura, SituacaoDaVerificacao } from "../../api/verificacao";
import { etapaInicial, proximaEtapa, type Etapa } from "./etapas";

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

  // A selfie do documento segue para o envio das fotos (F7), não para a conferência da selfie.
  it("concluirCapturaDoDocumentoNaoConfereASelfie", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO, finalidade: "DOCUMENTO" };

    expect(proximaEtapa(captura, { tipo: "concluiu" })).toBe(captura);
  });

  it("falhaDoDetectorVaiParaAFalha", () => {
    const captura: Etapa = { etapa: "captura", sessao: SESSAO, finalidade: "SELFIE" };

    expect(proximaEtapa(captura, { tipo: "falhou", falha: "outraPessoa" })).toEqual({
      etapa: "falhaDoDetector",
      falha: "outraPessoa",
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
