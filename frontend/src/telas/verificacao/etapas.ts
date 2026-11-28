// A máquina de etapas da verificação de idade, pura: a página guarda a etapa num useReducer e reage a
// cada uma (checa a câmera, abre a sessão, envia o documento). A sessão de captura, com a credencial
// curta, e as fotos do documento vivem só aqui dentro.

import { ErroHttp } from "../../api/http";
import type { Desfecho, ModoDaVerificacao, SessaoDeCaptura, SituacaoDaVerificacao } from "../../api/verificacao";
import type { MotivoSemCamera, ResultadoDaCamera } from "./camera";

/** Por que o detector de prova de vida desistiu da selfie. */
export type FalhaDoDetector =
  "camera" | "tempo" | "rosto" | "outraPessoa" | "conexao" | "servico" | "aparelho" | "orientacao" | "carregamento";

export type TipoDeDesfecho = "aprovada" | "repetir" | "analise" | "menor" | "sessaoInvalida";

export type AvisoDoDocumento = "naoLeu" | "selfieVenceu" | "fotosIlegiveis" | "fotosGrandes";

export type LadoDoDocumento = "frente" | "verso";

/** As duas fotos do documento, já reduzidas; só vão ao servidor junto com a selfie nova. */
export interface FotosDoDocumento {
  frente: Blob;
  verso: Blob;
}

/** Para que serve a selfie: a do documento leva as fotos consigo até o envio. */
export type Alvo = { finalidade: "SELFIE" } | { finalidade: "DOCUMENTO"; fotos: FotosDoDocumento };

export type Evento =
  /** Consentimento dado (ou as fotos escolhidas): com a AWS, a câmera é checada antes de abrir a sessão. */
  | { tipo: "comecar"; modo: ModoDaVerificacao }
  | { tipo: "cameraChecada"; resultado: ResultadoDaCamera }
  | ({ tipo: "iniciar" } & Alvo)
  | { tipo: "sessaoCriada"; sessao: SessaoDeCaptura }
  | { tipo: "sessaoRecusada"; erro: unknown }
  /** O detector terminou a selfie: a da selfie vai para a conferência; a do documento, para o envio. */
  | { tipo: "concluiu" }
  | { tipo: "falhou"; falha: FalhaDoDetector }
  /** A pessoa desistiu da selfie: volta para onde estava (a tentativa já foi gasta no servidor). */
  | { tipo: "cancelou" }
  /** Pede (de novo) o resultado da selfie da sessão: a repetição depois de um 503 ou de um 409 em andamento. */
  | { tipo: "conferir"; sessaoId: string }
  | { tipo: "conferido"; desfecho: Desfecho }
  | { tipo: "conferenciaRecusada"; erro: unknown }
  /** Uma foto do documento escolhida; null quando a foto não abriu e o lado fica vazio. */
  | { tipo: "foto"; lado: LadoDoDocumento; foto: Blob | null }
  /** Envia (de novo) as fotos com a sessão da selfie nova: a repetição depois de um 503 ou de um 409 em andamento. */
  | { tipo: "enviar"; sessaoId: string; fotos: FotosDoDocumento }
  | { tipo: "enviado"; desfecho: Desfecho }
  | { tipo: "envioRecusado"; erro: unknown }
  | { tipo: "tentarDeNovo" };

export type Etapa =
  | { etapa: "explicacao" }
  | ({ etapa: "checandoCamera" } & Alvo)
  | ({ etapa: "semCamera"; motivo: MotivoSemCamera } & Alvo)
  | ({ etapa: "iniciando" } & Alvo)
  | ({ etapa: "captura"; sessao: SessaoDeCaptura } & Alvo)
  | { etapa: "conferindo"; sessaoId: string }
  /**
   * O alvo da captura que falhou, para a nova selfie ser do mesmo tipo. sessao: só na falha de
   * carregamento, em que a sessão da AWS segue valendo e a repetição não gasta outra tentativa.
   */
  | ({ etapa: "falhaDoDetector"; falha: FalhaDoDetector; sessao?: SessaoDeCaptura } & Alvo)
  | { etapa: "desfecho"; tipo: TipoDeDesfecho }
  | { etapa: "documento"; frente?: Blob; verso?: Blob; aviso?: AvisoDoDocumento }
  | { etapa: "enviandoDocumento"; sessaoId: string; fotos: FotosDoDocumento }
  /** segundos: o Retry-After do 429; null quando as tentativas do dia acabaram. */
  | { etapa: "limite"; segundos: number | null }
  /** repetir: o evento que o "Tentar de novo" despacha, para refazer o pedido que o serviço não atendeu. */
  | { etapa: "indisponivel"; repetir: Exclude<Evento, { tipo: "tentarDeNovo" }> };

/** Sem Retry-After, o 429 de taxa espera um minuto: nunca vira o texto do limite do dia. */
const ESPERA_SEM_RETRY_AFTER = 60;

const SELFIE: Alvo = { finalidade: "SELFIE" };

/**
 * A etapa em que a página abre, pela situação lida do servidor. A situação APROVADA não tem etapa
 * (null): a conta já foi verificada, e a página volta para Minha conta.
 */
export function etapaInicial(situacao: SituacaoDaVerificacao): Etapa | null {
  switch (situacao.estado) {
    case "AGUARDANDO":
      return situacao.tentativasRestantesHoje > 0 ? { etapa: "explicacao" } : { etapa: "limite", segundos: null };
    case "DUVIDA":
      return situacao.enviosDeDocumentoRestantesHoje > 0
        ? { etapa: "documento" }
        : { etapa: "desfecho", tipo: "analise" };
    case "APROVADA":
      return null;
  }
}

/** Só o alvo, sem os outros campos da etapa ou do evento de onde veio. */
function alvoDe(origem: Alvo): Alvo {
  return origem.finalidade === "DOCUMENTO" ? { finalidade: "DOCUMENTO", fotos: origem.fotos } : SELFIE;
}

/** O código da recusa; vazio quando o erro não veio do servidor (rede, resposta estranha). */
function codigo(erro: unknown): string {
  return erro instanceof ErroHttp ? erro.codigo : "";
}

/** Os 429: o limite do dia ignora o Retry-After (que conta horas); o de taxa guarda a espera. */
function limiteDaRecusa(erro: unknown): Etapa | null {
  if (erro instanceof ErroHttp && erro.codigo === "LIMITE_DE_VERIFICACAO") {
    return { etapa: "limite", segundos: null };
  }
  if (erro instanceof ErroHttp && erro.codigo === "LIMITE_DE_TAXA") {
    return { etapa: "limite", segundos: erro.tentarDeNovoEmSegundos ?? ESPERA_SEM_RETRY_AFTER };
  }
  return null;
}

/** A recusa do POST da sessão: os 429 viram limite; o resto (503, rede, resposta estranha) pode repetir. */
function recusaDaSessao(alvo: Alvo, erro: unknown): Etapa {
  return limiteDaRecusa(erro) ?? { etapa: "indisponivel", repetir: { tipo: "iniciar", ...alvoDe(alvo) } };
}

/** O resultado da selfie: a DUVIDA só vai para o envio do documento quando o servidor ainda o aceita hoje. */
function etapaDoDesfecho(desfecho: Desfecho): Etapa {
  switch (desfecho.resultado) {
    case "APROVADA":
      return { etapa: "desfecho", tipo: "aprovada" };
    case "REPETIR":
      return { etapa: "desfecho", tipo: "repetir" };
    case "DUVIDA":
      return desfecho.pedeDocumento ? { etapa: "documento" } : { etapa: "desfecho", tipo: "analise" };
  }
}

/**
 * A recusa da conferência: o 422 de menor e o 404 de sessão vencida são desfechos; os 429 seguem a regra
 * da sessão; o resto (503, 409 em andamento, rede) repete o pedido com a mesma sessão, sem gastar outra
 * tentativa.
 */
export function recusaDaConferencia(sessaoId: string, erro: unknown): Etapa {
  switch (codigo(erro)) {
    case "MENOR_DE_IDADE":
      return { etapa: "desfecho", tipo: "menor" };
    case "SESSAO_DE_VERIFICACAO_INVALIDA":
      return { etapa: "desfecho", tipo: "sessaoInvalida" };
    default:
      return limiteDaRecusa(erro) ?? { etapa: "indisponivel", repetir: { tipo: "conferir", sessaoId } };
  }
}

/**
 * O resultado do envio do documento. O REPETIR (a Didit não leu) apaga as fotos e pede outras, enquanto
 * houver envio hoje; sem envio, a conta fica em análise, como na volta no mesmo dia.
 */
function etapaDoEnvio(desfecho: Desfecho): Etapa {
  switch (desfecho.resultado) {
    case "APROVADA":
      return { etapa: "desfecho", tipo: "aprovada" };
    case "DUVIDA":
      return { etapa: "desfecho", tipo: "analise" };
    case "REPETIR":
      return desfecho.enviosDeDocumentoRestantesHoje > 0
        ? { etapa: "documento", aviso: "naoLeu" }
        : { etapa: "desfecho", tipo: "analise" };
  }
}

/**
 * A recusa do envio do documento (D14). A selfie vencida (409 REFAZER_PROVA_DE_VIDA, ou o 404 do mesmo
 * envio repetido) guarda as fotos e pede só uma selfie nova; as fotos que a Didit não abre (422) ou
 * grandes demais (413) são apagadas; o menor encerra; os 429 viram limite; o resto (503, 409 em
 * andamento, rede) reenvia o mesmo multipart com a mesma sessão.
 */
export function recusaDoEnvio(envio: { sessaoId: string; fotos: FotosDoDocumento }, erro: unknown): Etapa {
  switch (codigo(erro)) {
    case "REFAZER_PROVA_DE_VIDA":
    case "SESSAO_DE_VERIFICACAO_INVALIDA":
      return { etapa: "documento", frente: envio.fotos.frente, verso: envio.fotos.verso, aviso: "selfieVenceu" };
    case "DOCUMENTO_INVALIDO":
      return { etapa: "documento", aviso: "fotosIlegiveis" };
    case "MENOR_DE_IDADE":
      return { etapa: "desfecho", tipo: "menor" };
    default:
      if (erro instanceof ErroHttp && erro.status === 413) {
        return { etapa: "documento", aviso: "fotosGrandes" };
      }
      return (
        limiteDaRecusa(erro) ?? {
          etapa: "indisponivel",
          repetir: { tipo: "enviar", sessaoId: envio.sessaoId, fotos: envio.fotos },
        }
      );
  }
}

/** Uma nova selfie, depois de uma falha, de um REPETIR ou com as fotos: com a AWS, a câmera é checada de novo. */
function novaSelfie(modo: ModoDaVerificacao, alvo: Alvo = SELFIE): Etapa {
  return modo === "aws" ? { etapa: "checandoCamera", ...alvoDe(alvo) } : { etapa: "iniciando", ...alvoDe(alvo) };
}

type Captura = Extract<Etapa, { etapa: "captura" }>;

/**
 * A falha do detector. A de carregamento (o chunk não veio) não chegou a usar a sessão: ela fica guardada
 * para a repetição. Se a sessão tiver vencido, o detector falha de novo por tempo ou serviço, sem ela.
 */
function falhaNaCaptura(captura: Captura, falha: FalhaDoDetector): Etapa {
  return falha === "carregamento"
    ? { etapa: "falhaDoDetector", falha, sessao: captura.sessao, ...alvoDe(captura) }
    : { etapa: "falhaDoDetector", falha, ...alvoDe(captura) };
}

function selfieConcluida(captura: Captura): Etapa {
  return captura.finalidade === "DOCUMENTO"
    ? { etapa: "enviandoDocumento", sessaoId: captura.sessao.sessaoId, fotos: captura.fotos }
    : { etapa: "conferindo", sessaoId: captura.sessao.sessaoId };
}

/** Desistir da selfie do documento volta para as fotos, que continuam escolhidas. */
function selfieCancelada(captura: Captura): Etapa {
  return captura.finalidade === "DOCUMENTO"
    ? { etapa: "documento", frente: captura.fotos.frente, verso: captura.fotos.verso }
    : { etapa: "explicacao" };
}

/** O pedido que o serviço não atendeu, de novo: a sessão, a conferência ou o envio. */
function repeticao(etapa: Etapa, evento: Evento): Etapa {
  if (evento.tipo === "iniciar") {
    return { etapa: "iniciando", ...alvoDe(evento) };
  }
  if (evento.tipo === "conferir") {
    return { etapa: "conferindo", sessaoId: evento.sessaoId };
  }
  if (evento.tipo === "enviar") {
    return { etapa: "enviandoDocumento", sessaoId: evento.sessaoId, fotos: evento.fotos };
  }
  return etapa;
}

function fimDaCaptura(captura: Captura, evento: Evento): Etapa {
  if (evento.tipo === "concluiu") {
    return selfieConcluida(captura);
  }
  if (evento.tipo === "falhou") {
    return falhaNaCaptura(captura, evento.falha);
  }
  return evento.tipo === "cancelou" ? selfieCancelada(captura) : captura;
}

type Documento = Extract<Etapa, { etapa: "documento" }>;

function trocarFoto(documento: Documento, lado: LadoDoDocumento, foto: Blob | null): Documento {
  const fotos = { frente: documento.frente, verso: documento.verso };
  fotos[lado] = foto ?? undefined;
  const { frente, verso } = fotos;
  const { aviso } = documento;
  return {
    etapa: "documento",
    ...(frente === undefined ? {} : { frente }),
    ...(verso === undefined ? {} : { verso }),
    ...(aviso === undefined ? {} : { aviso }),
  };
}

function selfieDoDocumento(documento: Documento, modo: ModoDaVerificacao): Etapa {
  const { frente, verso } = documento;
  return frente === undefined || verso === undefined
    ? documento
    : novaSelfie(modo, { finalidade: "DOCUMENTO", fotos: { frente, verso } });
}

/** A transição; um evento que não vale para a etapa atual (o segundo toque de um clique duplo) não muda nada. */
export function proximaEtapa(etapa: Etapa, evento: Evento): Etapa {
  switch (etapa.etapa) {
    case "explicacao":
      return evento.tipo === "comecar" ? novaSelfie(evento.modo) : etapa;
    case "checandoCamera":
      if (evento.tipo === "cameraChecada") {
        return evento.resultado === "ok"
          ? { etapa: "iniciando", ...alvoDe(etapa) }
          : { etapa: "semCamera", motivo: evento.resultado, ...alvoDe(etapa) };
      }
      return etapa;
    case "semCamera":
      return evento.tipo === "tentarDeNovo" ? { etapa: "checandoCamera", ...alvoDe(etapa) } : etapa;
    case "iniciando":
      if (evento.tipo === "sessaoCriada") {
        return { etapa: "captura", sessao: evento.sessao, ...alvoDe(etapa) };
      }
      if (evento.tipo === "sessaoRecusada") {
        return recusaDaSessao(etapa, evento.erro);
      }
      return etapa;
    case "indisponivel":
      return evento.tipo === "tentarDeNovo" ? proximaEtapa(etapa, etapa.repetir) : repeticao(etapa, evento);
    case "captura":
      return fimDaCaptura(etapa, evento);
    case "conferindo":
      if (evento.tipo === "conferido") {
        return etapaDoDesfecho(evento.desfecho);
      }
      return evento.tipo === "conferenciaRecusada" ? recusaDaConferencia(etapa.sessaoId, evento.erro) : etapa;
    case "falhaDoDetector":
      if (evento.tipo !== "comecar") {
        return etapa;
      }
      return etapa.sessao === undefined
        ? novaSelfie(evento.modo, etapa)
        : { etapa: "captura", sessao: etapa.sessao, ...alvoDe(etapa) };
    case "desfecho":
      if (etapa.tipo === "repetir" && evento.tipo === "comecar") {
        return novaSelfie(evento.modo);
      }
      if (etapa.tipo === "sessaoInvalida" && evento.tipo === "tentarDeNovo") {
        return { etapa: "explicacao" };
      }
      return etapa;
    case "documento":
      if (evento.tipo === "foto") {
        return trocarFoto(etapa, evento.lado, evento.foto);
      }
      return evento.tipo === "comecar" ? selfieDoDocumento(etapa, evento.modo) : etapa;
    case "enviandoDocumento":
      if (evento.tipo === "enviado") {
        return etapaDoEnvio(evento.desfecho);
      }
      return evento.tipo === "envioRecusado" ? recusaDoEnvio(etapa, evento.erro) : etapa;
    case "limite":
      return etapa;
  }
}
