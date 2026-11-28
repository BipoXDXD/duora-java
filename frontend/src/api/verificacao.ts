// Cliente da API da verificação de idade (2b) sobre o transporte de http.ts. Valida cada resposta à
// mão, como estadoDaConta.ts: fora do formato vira ErroHttp RESPOSTA_INVALIDA, sem levar o corpo
// junto (a resposta da sessão traz a credencial curta, que só pode viver no objeto devolvido).

import { ErroHttp, requisitarHttp } from "./http";

export type ModoDaVerificacao = "aws" | "falso";
export type Finalidade = "SELFIE" | "DOCUMENTO";

export interface SituacaoDaVerificacao {
  estado: "AGUARDANDO" | "DUVIDA" | "APROVADA";
  tentativasRestantesHoje: number;
  enviosDeDocumentoRestantesHoje: number;
  modo: ModoDaVerificacao;
}

export interface CredencialCurta {
  accessKeyId: string;
  secretAccessKey: string;
  sessionToken: string;
  expiration: Date;
}

export interface SessaoDeCaptura {
  sessaoId: string;
  regiao: "us-east-1";
  modo: ModoDaVerificacao;
  credencial: CredencialCurta;
  /** Relógio do servidor menos o daqui, só quando passa de 5 min; senão undefined. */
  desvioDoRelogioMs: number | undefined;
}

export interface Desfecho {
  resultado: "APROVADA" | "DUVIDA" | "REPETIR";
  pedeDocumento: boolean;
  tentativasRestantesHoje: number;
  enviosDeDocumentoRestantesHoje: number;
}

/** Códigos de erro que as rotas da verificação devolvem, mais o RESPOSTA_INVALIDA deste cliente. */
export type CodigoDaVerificacao =
  | "CONSENTIMENTO_BIOMETRICO_NECESSARIO"
  | "CONSENTIMENTO_DESATUALIZADO"
  | "VERIFICACAO_FORA_DE_ORDEM"
  | "SESSAO_DE_VERIFICACAO_INVALIDA"
  | "REFAZER_PROVA_DE_VIDA"
  | "SELFIE_NAO_DEU_CERTO"
  | "DOCUMENTO_INVALIDO"
  | "LIMITE_DE_VERIFICACAO"
  | "LIMITE_DE_TAXA"
  | "MENOR_DE_IDADE"
  | "REQUISICAO_EM_ANDAMENTO"
  | "PROVA_DE_VIDA_INDISPONIVEL"
  | "CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL"
  | "RESPOSTA_INVALIDA";

const DESVIO_MINIMO_MS = 300_000;

/** Exaustivas: um valor novo no tipo obriga a listá-lo aqui. */
const ESTADOS: readonly string[] = Object.keys({
  AGUARDANDO: true,
  DUVIDA: true,
  APROVADA: true,
} satisfies Record<SituacaoDaVerificacao["estado"], true>);
const MODOS: readonly string[] = Object.keys({ aws: true, falso: true } satisfies Record<ModoDaVerificacao, true>);
const RESULTADOS: readonly string[] = Object.keys({
  APROVADA: true,
  DUVIDA: true,
  REPETIR: true,
} satisfies Record<Desfecho["resultado"], true>);
const CODIGOS: readonly string[] = Object.keys({
  CONSENTIMENTO_BIOMETRICO_NECESSARIO: true,
  CONSENTIMENTO_DESATUALIZADO: true,
  VERIFICACAO_FORA_DE_ORDEM: true,
  SESSAO_DE_VERIFICACAO_INVALIDA: true,
  REFAZER_PROVA_DE_VIDA: true,
  SELFIE_NAO_DEU_CERTO: true,
  DOCUMENTO_INVALIDO: true,
  LIMITE_DE_VERIFICACAO: true,
  LIMITE_DE_TAXA: true,
  MENOR_DE_IDADE: true,
  REQUISICAO_EM_ANDAMENTO: true,
  PROVA_DE_VIDA_INDISPONIVEL: true,
  CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL: true,
  RESPOSTA_INVALIDA: true,
} satisfies Record<CodigoDaVerificacao, true>);

type Campos = Record<string, unknown>;

function objeto(dados: unknown): Campos | null {
  return typeof dados === "object" && dados !== null ? (dados as Campos) : null;
}

function contagem(valor: unknown): valor is number {
  return typeof valor === "number" && Number.isInteger(valor) && valor >= 0;
}

function textoPreenchido(valor: unknown): valor is string {
  return typeof valor === "string" && valor !== "";
}

function respostaInvalida(status: number): ErroHttp {
  return new ErroHttp({ status, codigo: "RESPOSTA_INVALIDA" });
}

function lerSituacao(dados: unknown): SituacaoDaVerificacao | null {
  const campos = objeto(dados);
  if (
    campos === null ||
    typeof campos.estado !== "string" ||
    !ESTADOS.includes(campos.estado) ||
    !contagem(campos.tentativasRestantesHoje) ||
    !contagem(campos.enviosDeDocumentoRestantesHoje) ||
    typeof campos.modo !== "string" ||
    !MODOS.includes(campos.modo)
  ) {
    return null;
  }
  return {
    estado: campos.estado as SituacaoDaVerificacao["estado"],
    tentativasRestantesHoje: campos.tentativasRestantesHoje,
    enviosDeDocumentoRestantesHoje: campos.enviosDeDocumentoRestantesHoje,
    modo: campos.modo as ModoDaVerificacao,
  };
}

function lerCredencial(dados: unknown): CredencialCurta | null {
  const campos = objeto(dados);
  if (
    campos === null ||
    !textoPreenchido(campos.accessKeyId) ||
    !textoPreenchido(campos.secretAccessKey) ||
    !textoPreenchido(campos.sessionToken) ||
    !textoPreenchido(campos.expiraEm)
  ) {
    return null;
  }
  const expiration = new Date(campos.expiraEm);
  if (Number.isNaN(expiration.getTime())) {
    return null;
  }
  return {
    accessKeyId: campos.accessKeyId,
    secretAccessKey: campos.secretAccessKey,
    sessionToken: campos.sessionToken,
    expiration,
  };
}

function lerDesfecho(dados: unknown): Desfecho | null {
  const campos = objeto(dados);
  if (
    campos === null ||
    typeof campos.resultado !== "string" ||
    !RESULTADOS.includes(campos.resultado) ||
    typeof campos.pedeDocumento !== "boolean" ||
    !contagem(campos.tentativasRestantesHoje) ||
    !contagem(campos.enviosDeDocumentoRestantesHoje)
  ) {
    return null;
  }
  return {
    resultado: campos.resultado as Desfecho["resultado"],
    pedeDocumento: campos.pedeDocumento,
    tentativasRestantesHoje: campos.tentativasRestantesHoje,
    enviosDeDocumentoRestantesHoje: campos.enviosDeDocumentoRestantesHoje,
  };
}

/**
 * Desvio entre o cabeçalho Date do servidor e `agoraMs`, em ms (servidor − aqui). Só conta a partir
 * de 5 min (contrato da 2b); abaixo disso, ou sem data legível, undefined.
 */
export function desvioDoRelogio(dataDoServidor: string | null, agoraMs: number): number | undefined {
  if (dataDoServidor === null) {
    return undefined;
  }
  const servidorMs = Date.parse(dataDoServidor);
  if (Number.isNaN(servidorMs)) {
    return undefined;
  }
  const delta = servidorMs - agoraMs;
  return Math.abs(delta) >= DESVIO_MINIMO_MS ? delta : undefined;
}

/** O código do erro, quando é um dos da verificação; senão undefined. */
export function codigoDaVerificacao(erro: unknown): CodigoDaVerificacao | undefined {
  return erro instanceof ErroHttp && CODIGOS.includes(erro.codigo) ? (erro.codigo as CodigoDaVerificacao) : undefined;
}

export async function obterVerificacao(): Promise<SituacaoDaVerificacao> {
  const resposta = await requisitarHttp("GET", "/conta/verificacao");
  const situacao = lerSituacao(resposta.dados);
  if (situacao === null) {
    throw respostaInvalida(resposta.status);
  }
  return situacao;
}

/**
 * Abre a sessão de captura; mandar a requisição já é o consentimento biométrico da pessoa, na versão
 * do texto que ela viu.
 */
export async function iniciarSessao(finalidade: Finalidade, versaoConsentimento: string): Promise<SessaoDeCaptura> {
  const resposta = await requisitarHttp("POST", "/conta/verificacao/sessao", {
    corpo: { consentimentoBiometrico: true, versaoConsentimento, finalidade },
  });
  const campos = objeto(resposta.dados);
  const credencial = lerCredencial(campos?.credencial);
  if (
    campos === null ||
    credencial === null ||
    !textoPreenchido(campos.sessaoId) ||
    campos.regiao !== "us-east-1" ||
    typeof campos.modo !== "string" ||
    !MODOS.includes(campos.modo)
  ) {
    throw respostaInvalida(resposta.status);
  }
  return {
    sessaoId: campos.sessaoId,
    regiao: "us-east-1",
    modo: campos.modo as ModoDaVerificacao,
    credencial,
    desvioDoRelogioMs: desvioDoRelogio(resposta.cabecalhos.get("Date"), Date.now()),
  };
}

export async function concluirSelfie(sessaoId: string): Promise<Desfecho> {
  const resposta = await requisitarHttp("POST", `/conta/verificacao/sessao/${encodeURIComponent(sessaoId)}/resultado`);
  const desfecho = lerDesfecho(resposta.dados);
  if (desfecho === null) {
    throw respostaInvalida(resposta.status);
  }
  return desfecho;
}

/** Os nomes de arquivo fixos evitam mandar ao servidor o nome da foto no aparelho. */
export async function enviarDocumento(envio: { sessaoId: string; frente: Blob; verso: Blob }): Promise<Desfecho> {
  const formulario = new FormData();
  formulario.append("sessaoId", envio.sessaoId);
  formulario.append("frente", envio.frente, "frente");
  formulario.append("verso", envio.verso, "verso");
  const resposta = await requisitarHttp("POST", "/conta/verificacao/documento", { formulario });
  const desfecho = lerDesfecho(resposta.dados);
  if (desfecho === null) {
    throw respostaInvalida(resposta.status);
  }
  return desfecho;
}
