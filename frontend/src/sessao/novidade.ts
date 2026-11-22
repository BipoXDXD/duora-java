// Textos que a sessão deriva da visão: o que mudou do lado do parceiro e a instrução da vez.

import { dadosDaPeca } from "../dominio/dados";
import { textoConexaoDoParceiro, textoPecasDoParceiro } from "../dominio/mensagens";
import type { Visao } from "../dominio/visao";
import type { Acao } from "./tipos";

/**
 * O que mudou do lado do parceiro e merece aviso no aria-live; null se nada.
 * propria: a visão veio da resposta a uma ação desta pessoa.
 */
export function novidade(anterior: Visao, visao: Visao, propria: boolean): string | null {
  // Nos ramos que o usam, o parceiro existe; String() mantém o texto do main.js antigo se não existir.
  const parceiro = String(visao.parceiro?.nome);
  const sinal = visao.sinal;
  const sinalNovo =
    sinal !== null &&
    sinal.deAssento !== visao.voce.assento &&
    (anterior.sinal?.peca !== sinal.peca || anterior.sinal.deAssento !== sinal.deAssento);
  if (sinalNovo) {
    return `${parceiro} sugeriu mover ${dadosDaPeca(sinal.peca).artigo}.`;
  }
  // Só o parceiro mexe nas peças dele: vale também quando a visão veio da própria ação.
  const pecas = textoPecasDoParceiro(anterior, visao);
  if (propria) {
    return pecas;
  }
  if (!anterior.parceiro && visao.parceiro) {
    return `${parceiro} entrou no terraço. Escolham as primeiras peças.`;
  }
  if (anterior.fase === "MONTANDO" && visao.fase === "REVELADO") {
    return "Os dois confirmaram. Olhem o céu.";
  }
  if (anterior.fase === "REVELADO" && visao.fase === "MONTANDO") {
    return `${parceiro} voltou a ajustar o terraço.`;
  }
  const frases = [pecas, textoConexaoDoParceiro(anterior, visao)];
  if (anterior.prontos.voce && !visao.prontos.voce) {
    frases.push("A composição mudou e sua confirmação foi desfeita. Confirme de novo quando quiser.");
  } else if (!anterior.prontos.parceiro && visao.prontos.parceiro) {
    frases.push(`${parceiro} confirmou esta composição.`);
  }
  return frases.filter(Boolean).join(" ") || null;
}

/** A instrução da montagem: o próximo passo de quem está na tela. */
export function instrucao(visao: Visao, selecionada: string | null, enviando: Acao | null): string {
  if (enviando) {
    return "Enviando…";
  }
  if (visao.fase === "REVELADO") {
    return "Olhem para cima.";
  }
  if (selecionada) {
    const { artigo, zona } = dadosDaPeca(selecionada);
    const onde = zona === "centro" ? "na área de observação, no centro" : "em um lugar lateral";
    return `Escolha onde ${artigo} fica: ${onde}. Esc cancela.`;
  }
  if (visao.prontos.voce) {
    return `Esperando ${visao.parceiro?.nome ?? "a outra pessoa"} confirmar a mesma composição.`;
  }
  if (visao.confirmacao.podeConfirmar) {
    return "Tudo no lugar. Quando os dois confirmarem, o céu escurece.";
  }
  const minhas = visao.pecas.filter((p) => p.assento === visao.voce.assento && p.lugar);
  if (minhas.length === 0) {
    return "Escolha uma das suas peças e depois um lugar no terraço.";
  }
  return "Escolha sua segunda peça ou reorganize as que já colocou.";
}
