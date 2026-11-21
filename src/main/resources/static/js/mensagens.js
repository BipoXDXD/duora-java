// Textos montados no cliente a partir dos códigos e dados estruturados da API.

import { LUGARES, PECAS } from "./dados.js";

const nomeDoParceiro = (visao) => visao?.parceiro?.nome ?? "A outra pessoa";

/** Mensagem curta para um ErroApi: diz o que aconteceu e o que fazer. */
export function mensagemDeErro(erro, visao) {
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
    default:
      return `O servidor não aceitou o pedido (${erro.status}). Tente de novo.`;
  }
}

/** Texto de estado para uma peça própria que não pode ser colocada agora. */
export function textoBloqueio(codigo, visao) {
  if (codigo === "ESPERE_PRIMEIRA_PECA") {
    return `Espere ${visao?.parceiro?.nome ?? "a outra pessoa"} colocar a primeira peça.`;
  }
  return "Indisponível agora.";
}

/** Aviso e ação do bloco que confirma a saída; sem parceiro, só o próprio terraço acaba. */
export function textoConfirmarSaida(nomeParceiro) {
  return nomeParceiro
    ? { aviso: `Isso encerra o terraço para ${nomeParceiro} também.`, acao: "Encerrar para os dois" }
    : { aviso: "Isso encerra o terraço.", acao: "Encerrar o terraço" };
}

/** motivo: SAIU | EXPIROU | SALA_INEXISTENTE | VOCE_SAIU. */
export function textoEncerrado(motivo, nomeParceiro) {
  switch (motivo) {
    case "SAIU":
      return nomeParceiro
        ? `${nomeParceiro} saiu e o terraço foi encerrado.`
        : "A outra pessoa saiu e o terraço foi encerrado.";
    case "EXPIROU":
      return "O terraço expirou depois de 30 minutos sem ações.";
    case "VOCE_SAIU":
      return "Você saiu e o terraço foi encerrado.";
    default:
      return "Este terraço não existe mais (o servidor reiniciou ou ele expirou).";
  }
}

export function textoMotivoConfirmacao(visao) {
  const { faltam } = visao.confirmacao;
  if (faltam > 0) {
    return faltam === 1 ? "Falta 1 peça no terraço." : `Faltam ${faltam} peças no terraço.`;
  }
  if (visao.prontos.voce && !visao.prontos.parceiro) {
    return `Você confirmou. Esperando ${nomeDoParceiro(visao)}.`;
  }
  if (visao.prontos.parceiro && !visao.prontos.voce) {
    return `${nomeDoParceiro(visao)} já confirmou esta composição.`;
  }
  return "";
}

/** Título e frase da noite revelada, a partir de resultado { secundariaA, secundariaB, ladoTelescopio }. */
export function textoResultado(visao) {
  const { secundariaA, secundariaB, ladoTelescopio } = visao.resultado;
  const nomes = { [visao.voce.assento]: visao.voce.nome };
  if (visao.parceiro) {
    nomes[visao.parceiro.assento] = visao.parceiro.nome;
  }
  const a = PECAS[secundariaA];
  const b = PECAS[secundariaB];
  return {
    titulo: `Uma noite de ${a.nome.toLowerCase()} e ${b.nome.toLowerCase()}`,
    texto:
      `O telescópio de ${nomes.A} aponta para o lado ${ladoTelescopio} do céu, e é por ali que os meteoros passam. ` +
      `${nomes.A} trouxe ${a.artigo}; ${nomes.B}, o banco e ${b.artigo}.`,
  };
}

/** O que o parceiro fez com as peças dele entre duas visões; null se nada mudou. */
export function textoPecasDoParceiro(anterior, visao) {
  const nome = nomeDoParceiro(visao);
  const lugarAntes = new Map(anterior.pecas.map((p) => [p.peca, p.lugar ?? null]));
  const frases = [];
  for (const p of visao.pecas) {
    if (p.assento === visao.voce.assento || !lugarAntes.has(p.peca)) {
      continue;
    }
    const antes = lugarAntes.get(p.peca);
    const agora = p.lugar ?? null;
    if (antes === agora) {
      continue;
    }
    const { artigo } = PECAS[p.peca];
    if (!agora) {
      frases.push(`${nome} tirou ${artigo} do terraço.`);
    } else {
      frases.push(`${nome} ${antes ? "moveu" : "colocou"} ${artigo}: ${LUGARES[agora].nome}.`);
    }
  }
  return frases.length ? frases.join(" ") : null;
}

/** Presença do parceiro: só quando muda entre duas visões. */
export function textoConexaoDoParceiro(anterior, visao) {
  if (!anterior.parceiro || !visao.parceiro || anterior.parceiro.conectado === visao.parceiro.conectado) {
    return null;
  }
  return visao.parceiro.conectado
    ? `${visao.parceiro.nome} está de volta.`
    : `${visao.parceiro.nome} ficou sem sinal.`;
}
