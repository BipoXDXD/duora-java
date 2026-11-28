// A máquina de etapas da verificação de idade, pura: a página guarda a etapa num useReducer e reage a
// cada uma (checa a câmera, abre a sessão). A sessão de captura, com a credencial curta, vive só aqui
// dentro, na etapa da captura.

import { ErroHttp } from "../../api/http";
import type { Finalidade, ModoDaVerificacao, SessaoDeCaptura, SituacaoDaVerificacao } from "../../api/verificacao";
import type { MotivoSemCamera, ResultadoDaCamera } from "./camera";

/** Por que o detector de prova de vida desistiu da selfie. */
export type FalhaDoDetector =
  "camera" | "tempo" | "rosto" | "outraPessoa" | "conexao" | "servico" | "aparelho" | "orientacao" | "carregamento";

export type TipoDeDesfecho = "aprovada" | "repetir" | "analise" | "menor" | "sessaoInvalida";

export type AvisoDoDocumento = "naoLeu" | "selfieVenceu" | "fotosIlegiveis";

export type Evento =
  /** Consentimento dado: com a AWS, a câmera é checada antes de gastar uma tentativa. */
  | { tipo: "comecar"; modo: ModoDaVerificacao }
  | { tipo: "cameraChecada"; resultado: ResultadoDaCamera }
  | { tipo: "iniciar"; finalidade: Finalidade }
  | { tipo: "sessaoCriada"; sessao: SessaoDeCaptura }
  | { tipo: "sessaoRecusada"; erro: unknown }
  /** O detector terminou a selfie: a conferência pede o resultado ao servidor. */
  | { tipo: "concluiu" }
  | { tipo: "falhou"; falha: FalhaDoDetector }
  /** A pessoa desistiu da selfie: volta para a explicação (a tentativa já foi gasta no servidor). */
  | { tipo: "cancelou" }
  | { tipo: "tentarDeNovo" };

export type Etapa =
  | { etapa: "explicacao" }
  | { etapa: "checandoCamera"; finalidade: Finalidade }
  | { etapa: "semCamera"; motivo: MotivoSemCamera; finalidade: Finalidade }
  | { etapa: "iniciando"; finalidade: Finalidade }
  | { etapa: "captura"; sessao: SessaoDeCaptura; finalidade: Finalidade }
  | { etapa: "conferindo"; sessaoId: string }
  | { etapa: "falhaDoDetector"; falha: FalhaDoDetector }
  | { etapa: "desfecho"; tipo: TipoDeDesfecho }
  | { etapa: "documento"; frente?: Blob; verso?: Blob; aviso?: AvisoDoDocumento }
  | { etapa: "enviandoDocumento" }
  /** segundos: o Retry-After do 429; null quando as tentativas do dia acabaram. */
  | { etapa: "limite"; segundos: number | null }
  /** repetir: o evento que o "Tentar de novo" despacha, para refazer o pedido que o serviço não atendeu. */
  | { etapa: "indisponivel"; repetir: Exclude<Evento, { tipo: "tentarDeNovo" }> };

/** Sem Retry-After, o 429 de taxa espera um minuto: nunca vira o texto do limite do dia. */
const ESPERA_SEM_RETRY_AFTER = 60;

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

/**
 * A recusa do POST da sessão: o limite do dia ignora o Retry-After (que conta horas) e mostra o texto do
 * dia; o 429 de taxa guarda a espera; o resto (503, rede, resposta estranha) pode repetir.
 */
function recusaDaSessao(finalidade: Finalidade, erro: unknown): Etapa {
  if (erro instanceof ErroHttp && erro.codigo === "LIMITE_DE_VERIFICACAO") {
    return { etapa: "limite", segundos: null };
  }
  if (erro instanceof ErroHttp && erro.codigo === "LIMITE_DE_TAXA") {
    return { etapa: "limite", segundos: erro.tentarDeNovoEmSegundos ?? ESPERA_SEM_RETRY_AFTER };
  }
  return { etapa: "indisponivel", repetir: { tipo: "iniciar", finalidade } };
}

/** A transição; um evento que não vale para a etapa atual (o segundo toque de um clique duplo) não muda nada. */
export function proximaEtapa(etapa: Etapa, evento: Evento): Etapa {
  switch (etapa.etapa) {
    case "explicacao":
      if (evento.tipo === "comecar") {
        return evento.modo === "aws"
          ? { etapa: "checandoCamera", finalidade: "SELFIE" }
          : { etapa: "iniciando", finalidade: "SELFIE" };
      }
      return etapa;
    case "checandoCamera":
      if (evento.tipo === "cameraChecada") {
        return evento.resultado === "ok"
          ? { etapa: "iniciando", finalidade: etapa.finalidade }
          : { etapa: "semCamera", motivo: evento.resultado, finalidade: etapa.finalidade };
      }
      return etapa;
    case "semCamera":
      return evento.tipo === "tentarDeNovo" ? { etapa: "checandoCamera", finalidade: etapa.finalidade } : etapa;
    case "iniciando":
      if (evento.tipo === "sessaoCriada") {
        return { etapa: "captura", sessao: evento.sessao, finalidade: etapa.finalidade };
      }
      if (evento.tipo === "sessaoRecusada") {
        return recusaDaSessao(etapa.finalidade, evento.erro);
      }
      return etapa;
    case "indisponivel":
      if (evento.tipo === "tentarDeNovo") {
        return proximaEtapa(etapa, etapa.repetir);
      }
      return evento.tipo === "iniciar" ? { etapa: "iniciando", finalidade: evento.finalidade } : etapa;
    case "captura":
      // A selfie do documento segue para o envio das fotos, não para a conferência da selfie.
      if (evento.tipo === "concluiu" && etapa.finalidade === "SELFIE") {
        return { etapa: "conferindo", sessaoId: etapa.sessao.sessaoId };
      }
      if (evento.tipo === "falhou") {
        return { etapa: "falhaDoDetector", falha: evento.falha };
      }
      return evento.tipo === "cancelou" ? { etapa: "explicacao" } : etapa;
    case "conferindo":
    case "falhaDoDetector":
    case "desfecho":
    case "documento":
    case "enviandoDocumento":
    case "limite":
      return etapa;
  }
}
