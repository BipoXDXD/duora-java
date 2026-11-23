// Textos que a sessão deriva da visão: o que mudou do lado do parceiro e a instrução da vez.

import type { CenarioFront } from "../cenario/tipos";
import { situacaoDoAjuste, textoDoPedido } from "../dominio/ajuste";
import {
  nomeDoParceiro,
  nomeDoParceiroNoMeio,
  textoConexaoDoParceiro,
  textoPecasDoParceiro,
} from "../dominio/mensagens";
import { aberturaAtiva, lanternasAcenderam, sinalRecebido, type Visao, type VisaoSinal } from "../dominio/visao";
import type { Acao } from "./tipos";

/**
 * O que mudou do lado do parceiro e merece aviso no aria-live; null se nada.
 * propria: a visão veio da resposta a uma ação desta pessoa.
 */
export function novidade(c: CenarioFront, anterior: Visao, visao: Visao, propria: boolean): string | null {
  // Em todos os ramos que o usam, o nome abre a frase.
  const parceiro = nomeDoParceiro(visao);
  const sinal = sinalRecebido(visao);
  const sinalNovo =
    sinal !== null &&
    (anterior.sinal?.peca !== sinal.peca ||
      anterior.sinal.deAssento !== sinal.deAssento ||
      anterior.sinal.lugar !== sinal.lugar);
  // Vale também para quem deu o segundo toque: a resposta da própria ação traz as lanternas acesas.
  if (lanternasAcenderam(anterior, visao)) {
    return "As lanternas acenderam.";
  }
  if (sinalNovo) {
    return textoSinalRecebido(c, visao, sinal);
  }
  // Só o parceiro mexe nas peças dele: vale também quando a visão veio da própria ação.
  const pecas = textoPecasDoParceiro(c, anterior, visao);
  if (propria) {
    return pecas;
  }
  if (!anterior.parceiro && visao.parceiro) {
    // Na abertura, o painel ainda não mostra as peças: o convite vem das lanternas, sem palavras.
    return aberturaAtiva(visao)
      ? `${parceiro} entrou no terraço.`
      : `${parceiro} entrou no terraço. Escolham as primeiras peças.`;
  }
  if (anterior.fase === "MONTANDO" && visao.fase === "REVELADO") {
    return TEXTO_NOITE_REVELADA;
  }
  // Só o aceite volta à montagem; vindo do polling, quem aceitou foi o parceiro.
  if (anterior.fase === "REVELADO" && visao.fase === "MONTANDO") {
    return `${parceiro} aceitou. De volta à montagem.`;
  }
  // O ajuste não esconde a conexão do parceiro que mudou no mesmo polling.
  const frases = [textoAjusteDoParceiro(anterior, visao, parceiro), pecas, textoConexaoDoParceiro(anterior, visao)];
  if (anterior.prontos.voce && !visao.prontos.voce) {
    frases.push("A composição mudou e sua confirmação foi desfeita. Confirme de novo quando quiser.");
  } else if (!anterior.prontos.parceiro && visao.prontos.parceiro) {
    frases.push(`${parceiro} confirmou esta composição.`);
  }
  return frases.filter(Boolean).join(" ") || null;
}

/** O que mudou no pedido de ajuste da noite revelada; null se nada mudou. */
function textoAjusteDoParceiro(anterior: Visao, visao: Visao, parceiro: string): string | null {
  const ajusteAntes = situacaoDoAjuste(anterior);
  if (visao.fase !== "REVELADO" || ajusteAntes === situacaoDoAjuste(visao)) {
    return null;
  }
  switch (ajusteAntes) {
    case "meu":
      return `${parceiro} prefere manter esta noite.`;
    case "do-outro":
      return `${parceiro} cancelou o pedido.`;
    case "nenhum":
      return textoDoPedido(visao);
  }
}

/** A noite se revela: o mesmo texto para quem confirmou por último e para quem soube pelo polling. */
export const TEXTO_NOITE_REVELADA = "Vocês confirmaram. Olhem o céu.";

/** O aviso de quem recebe uma sugestão do parceiro. */
export function textoSinalRecebido(c: CenarioFront, visao: Visao, sinal: VisaoSinal): string {
  return `${nomeDoParceiro(visao)} sugeriu mover ${c.peca(sinal.peca).artigo}: ${c.lugar(sinal.lugar).nome}.`;
}

/** O aviso de quem sugeriu, depois que o servidor aceitou o sinal. */
export function textoSinalEnviado(c: CenarioFront, visao: Visao, peca: string, lugar: string): string {
  return `Você sugeriu que ${nomeDoParceiroNoMeio(visao)} mova ${c.peca(peca).artigo}: ${c.lugar(lugar).nome}.`;
}

/** O fim da instrução de escolher um lugar com teclado à mão: o Esc desfaz a escolha. */
export const SUFIXO_ESC_CANCELA = " Esc cancela.";

/** O que a tela tem em mãos além da visão: a escolha em curso e a ação em envio. */
export interface EscolhaDaVez {
  selecionada: string | null;
  enviando: Acao | null;
  /** A peça do parceiro cujo destino você está escolhendo. */
  sugerindo?: string | null;
  /** Como desfazer a escolha do lugar ("" no toque, onde não há Esc). */
  sufixoCancelar?: string;
}

/** A instrução da montagem: o próximo passo de quem está na tela. */
export function instrucao(
  c: CenarioFront,
  visao: Visao,
  { selecionada, enviando, sugerindo = null, sufixoCancelar = SUFIXO_ESC_CANCELA }: EscolhaDaVez,
): string {
  // A abertura é sem palavras: a lanterna na cena é o único convite, também durante o toque.
  if (aberturaAtiva(visao)) {
    return "";
  }
  if (enviando) {
    return "Enviando…";
  }
  if (visao.fase === "REVELADO") {
    return "Olhem para cima.";
  }
  if (sugerindo) {
    return `Escolha onde ${c.peca(sugerindo).artigo} de ${nomeDoParceiroNoMeio(visao)} ficaria melhor.${sufixoCancelar}`;
  }
  if (selecionada) {
    const { artigo, zona } = c.peca(selecionada);
    return `Escolha onde ${artigo} fica: ${c.zona(zona).instrucao}.${sufixoCancelar}`;
  }
  if (visao.prontos.voce) {
    return `Esperando ${nomeDoParceiroNoMeio(visao)} confirmar a mesma composição.`;
  }
  if (visao.confirmacao.podeConfirmar) {
    return "Tudo no lugar. Quando vocês confirmarem, o céu escurece.";
  }
  const minhas = visao.pecas.filter((p) => p.assento === visao.voce.assento && p.lugar);
  if (minhas.length === 0) {
    return "Escolha uma das suas peças e depois um lugar no terraço.";
  }
  return "Escolha sua segunda peça ou reorganize as que já colocou.";
}
