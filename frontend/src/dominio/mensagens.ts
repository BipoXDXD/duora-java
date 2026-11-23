// Textos montados no cliente a partir dos códigos e dados estruturados da API.

import type { CenarioFront } from "../cenario/tipos";
import type { Assento, Visao } from "./visao";

/** O que as mensagens leem de um erro da API (o ErroApi do cliente satisfaz este tipo). */
export interface ErroComCodigo {
  codigo: string;
  status: number;
  motivo: string | null;
}

const nomeDoParceiro = (visao: Visao | null | undefined): string => visao?.parceiro?.nome ?? "A outra pessoa";

/** Mensagem curta para um ErroApi: diz o que aconteceu e o que fazer. */
export function mensagemDeErro(erro: ErroComCodigo, visao: Visao | null | undefined): string {
  const parceiro = nomeDoParceiro(visao);
  switch (erro.codigo) {
    case "REDE":
      return "Sem conexão com o servidor. Confira se ele está rodando e tente de novo.";
    case "SERVIDOR":
    case "RESPOSTA_INVALIDA":
      return "O servidor falhou ao responder. Tente de novo em instantes.";
    case "SEM_ASSENTO":
      return "Este navegador não está em nenhum terraço. Crie um ou abra um convite.";
    case "CONVITE_INVALIDO":
      return "Este convite não vale mais. Peça um link novo ou crie seu próprio terraço.";
    case "PECA_ALHEIA":
      return `Essa peça é de ${parceiro}. Toque nela no terraço para sugerir que mova.`;
    case "ORIGEM_RECUSADA":
      return "O servidor recusou o pedido. Recarregue a página e tente de novo.";
    case "LUGAR_OCUPADO":
      return `${parceiro} acabou de usar este lugar. Escolha outro.`;
    case "VERSAO_OBSOLETA":
      return "A composição mudou; veja a versão atual.";
    case "ESPERE_PRIMEIRA_PECA":
      return textoBloqueio("ESPERE_PRIMEIRA_PECA", visao);
    case "FASE_INVALIDA":
      return "Essa ação não vale nesta etapa. O terraço foi atualizado.";
    case "ABERTURA_EM_ANDAMENTO":
      // O mesmo texto do detalhe do servidor: as peças esperam a abertura acabar.
      return "Espere as lanternas acenderem.";
    case "INCOMPLETA":
      return "Ainda faltam peças no terraço para confirmar.";
    case "LUGAR_INVALIDO":
      return "Esse lugar não serve para essa peça. Escolha um dos lugares marcados.";
    case "PECA_INVALIDA":
      return "Peça desconhecida. Recarregue a página.";
    case "APELIDO_INVALIDO":
      return "Use de 1 a 20 caracteres no apelido.";
    case "LIMITE_SALAS":
      return "O servidor já tem 20 terraços abertos. Tente de novo em alguns minutos.";
    case "SALA_ENCERRADA":
      return "Este terraço terminou. Crie outro ou abra um convite.";
    case "SALA_INEXISTENTE":
      return "Este terraço não existe mais. Crie outro ou abra um convite.";
    default:
      return `O servidor não aceitou o pedido (${String(erro.status)}). Tente de novo.`;
  }
}

/** Texto de estado para uma peça própria que não pode ser colocada agora. */
export function textoBloqueio(codigo: string, visao: Visao | null | undefined): string {
  if (codigo === "ESPERE_PRIMEIRA_PECA") {
    return `Espere ${visao?.parceiro?.nome ?? "a outra pessoa"} colocar a primeira peça.`;
  }
  return "Indisponível agora.";
}

/** Aviso e ação do bloco que confirma a saída; sem parceiro, só o próprio terraço acaba. */
export function textoConfirmarSaida(nomeParceiro: string | null | undefined): { aviso: string; acao: string } {
  return nomeParceiro
    ? { aviso: `Isso encerra o terraço para ${nomeParceiro} também.`, acao: "Encerrar para os dois" }
    : { aviso: "Isso encerra o terraço.", acao: "Encerrar o terraço" };
}

/**
 * motivo: SAIU | EXPIROU | SALA_INEXISTENTE | VOCE_SAIU (vem do servidor; outro valor cai no último
 * texto). Quem fica (SAIU) não ouve quem saiu: a despedida agradece o encontro, sem culpa.
 */
export function textoEncerrado(motivo: string, nomeParceiro?: string | null): { titulo: string; texto: string } {
  switch (motivo) {
    case "SAIU":
      return {
        titulo: "O encontro terminou",
        texto: `Que bom montar o terraço ${nomeParceiro ? `com ${nomeParceiro}` : "a dois"}.`,
      };
    case "VOCE_SAIU":
      return { titulo: "Você saiu do terraço", texto: "O encontro terminou para os dois." };
    case "EXPIROU":
      return { titulo: "Terraço encerrado", texto: "O terraço expirou depois de 30 minutos sem ações." };
    default:
      return {
        titulo: "Terraço encerrado",
        texto: "Este terraço não existe mais (o servidor reiniciou ou ele expirou).",
      };
  }
}

export function textoMotivoConfirmacao(visao: Visao): string {
  const { faltam } = visao.confirmacao;
  if (faltam > 0) {
    return faltam === 1 ? "Falta 1 peça no terraço." : `Faltam ${String(faltam)} peças no terraço.`;
  }
  if (visao.prontos.voce && !visao.prontos.parceiro) {
    return `Você confirmou. Esperando ${nomeDoParceiro(visao)}.`;
  }
  if (visao.prontos.parceiro && !visao.prontos.voce) {
    return `${nomeDoParceiro(visao)} já confirmou esta composição.`;
  }
  return "";
}

/**
 * Título e texto da noite revelada: a legenda do cenário preenchida com o resultado e os nomes,
 * seguida das frases das peças que ficaram perto uma da outra.
 */
export function textoResultado(c: CenarioFront, visao: Visao): { titulo: string; texto: string } {
  if (visao.resultado === null) {
    throw new Error("Visão sem resultado: a noite ainda não foi revelada");
  }
  const { titulo, texto } = c.legenda(visao.resultado, { A: nomeNoAssento(visao, "A"), B: nomeNoAssento(visao, "B") });
  const colocadas = visao.pecas.flatMap((p) => (p.lugar === null ? [] : [{ peca: p.peca, lugar: p.lugar }]));
  return { titulo, texto: [texto, ...c.frasesDeRelacao(colocadas)].join(" ") };
}

/** Nome de quem ocupa o assento; a noite só é revelada com os dois assentos ocupados. */
function nomeNoAssento(visao: Visao, assento: Assento): string {
  if (visao.voce.assento === assento) {
    return visao.voce.nome;
  }
  if (visao.parceiro?.assento === assento) {
    return visao.parceiro.nome;
  }
  throw new Error(`Visão revelada sem quem está no assento ${assento}`);
}

/** O que o parceiro fez com as peças dele entre duas visões; null se nada mudou. */
export function textoPecasDoParceiro(c: CenarioFront, anterior: Visao, visao: Visao): string | null {
  const nome = nomeDoParceiro(visao);
  const lugarAntes = new Map(anterior.pecas.map((p) => [p.peca, p.lugar]));
  const frases: string[] = [];
  for (const p of visao.pecas) {
    if (p.assento === visao.voce.assento || !lugarAntes.has(p.peca)) {
      continue;
    }
    const antes = lugarAntes.get(p.peca);
    const agora = p.lugar;
    if (antes === agora) {
      continue;
    }
    const { artigo } = c.peca(p.peca);
    if (!agora) {
      frases.push(`${nome} tirou ${artigo} do terraço.`);
    } else {
      frases.push(`${nome} ${antes ? "moveu" : "colocou"} ${artigo}: ${c.lugar(agora).nome}.`);
    }
  }
  return frases.length ? frases.join(" ") : null;
}

/** Presença do parceiro: só quando muda entre duas visões. */
export function textoConexaoDoParceiro(anterior: Visao, visao: Visao): string | null {
  if (!anterior.parceiro || !visao.parceiro || anterior.parceiro.conectado === visao.parceiro.conectado) {
    return null;
  }
  return visao.parceiro.conectado ? `${visao.parceiro.nome} está de volta.` : `${visao.parceiro.nome} ficou sem sinal.`;
}
