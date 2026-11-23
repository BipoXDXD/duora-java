// Textos que a sessão deriva da visão: o que mudou do lado do parceiro e a instrução da vez.

import type { CenarioFront } from "../cenario/tipos";
import { situacaoDoAjuste, textoDoPedido } from "../dominio/ajuste";
import { type ErroComCodigo, mensagemDeErro, textoConexaoDoParceiro, textoPecasDoParceiro } from "../dominio/mensagens";
import { aberturaAtiva, lanternasAcenderam, type Visao } from "../dominio/visao";
import type { Acao } from "./tipos";

/**
 * O que mudou do lado do parceiro e merece aviso no aria-live; null se nada.
 * propria: a visão veio da resposta a uma ação desta pessoa.
 */
export function novidade(c: CenarioFront, anterior: Visao, visao: Visao, propria: boolean): string | null {
  // Nos ramos que o usam, o parceiro existe; String() mantém o texto do main.js antigo se não existir.
  const parceiro = String(visao.parceiro?.nome);
  const sinal = visao.sinal;
  const sinalNovo =
    sinal !== null &&
    sinal.deAssento !== visao.voce.assento &&
    (anterior.sinal?.peca !== sinal.peca ||
      anterior.sinal.deAssento !== sinal.deAssento ||
      anterior.sinal.lugar !== sinal.lugar);
  // Vale também para quem deu o segundo toque: a resposta da própria ação traz as lanternas acesas.
  if (lanternasAcenderam(anterior, visao)) {
    return "As lanternas acenderam.";
  }
  if (sinalNovo) {
    return `${parceiro} sugeriu mover ${c.peca(sinal.peca).artigo}: ${c.lugar(sinal.lugar).nome}.`;
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
    return "Os dois confirmaram. Olhem o céu.";
  }
  // Só o aceite volta à montagem; vindo do polling, quem aceitou foi o parceiro.
  if (anterior.fase === "REVELADO" && visao.fase === "MONTANDO") {
    return `${parceiro} aceitou. De volta à montagem.`;
  }
  const ajusteAntes = situacaoDoAjuste(anterior);
  if (visao.fase === "REVELADO" && ajusteAntes !== situacaoDoAjuste(visao)) {
    if (ajusteAntes === "meu") {
      return `${parceiro} prefere manter esta noite.`;
    }
    return ajusteAntes === "do-outro" ? "Pedido cancelado." : textoDoPedido(visao);
  }
  const frases = [pecas, textoConexaoDoParceiro(anterior, visao)];
  if (anterior.prontos.voce && !visao.prontos.voce) {
    frases.push("A composição mudou e sua confirmação foi desfeita. Confirme de novo quando quiser.");
  } else if (!anterior.prontos.parceiro && visao.prontos.parceiro) {
    frases.push(`${parceiro} confirmou esta composição.`);
  }
  return frases.filter(Boolean).join(" ") || null;
}

/** O aviso de quem sugeriu, depois que o servidor aceitou o sinal. */
export function textoSinalEnviado(c: CenarioFront, visao: Visao, peca: string, lugar: string): string {
  return `Você sugeriu que ${String(visao.parceiro?.nome)} mova ${c.peca(peca).artigo}: ${c.lugar(lugar).nome}.`;
}

/**
 * A recusa de um sinal. Os textos gerais de PECA_ALHEIA e LUGAR_OCUPADO falam de colocar a própria
 * peça; aqui a peça é do parceiro e o lugar pode estar ocupado por qualquer um dos dois.
 */
export function textoRecusaDoSinal(erro: ErroComCodigo, visao: Visao | null): string {
  switch (erro.codigo) {
    case "PECA_ALHEIA":
      return "Essa peça é sua. Para mudá-la de lugar, escolha-a em Suas peças.";
    case "LUGAR_OCUPADO":
      return "Este lugar não está mais livre. Escolha outro.";
    default:
      return mensagemDeErro(erro, visao);
  }
}

/** O fim da instrução de escolher um lugar com teclado à mão: o Esc desfaz a escolha. */
export const SUFIXO_ESC_CANCELA = " Esc cancela.";

/**
 * A instrução da montagem: o próximo passo de quem está na tela. sugerindo: a peça do parceiro
 * cujo destino você está escolhendo. sufixoCancelar: como desfazer a escolha do lugar ("" no
 * toque, onde não há Esc).
 */
export function instrucao(
  c: CenarioFront,
  visao: Visao,
  selecionada: string | null,
  enviando: Acao | null,
  sugerindo: string | null = null,
  sufixoCancelar = SUFIXO_ESC_CANCELA,
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
    return `Escolha onde ${c.peca(sugerindo).artigo} de ${String(visao.parceiro?.nome)} ficaria melhor.${sufixoCancelar}`;
  }
  if (selecionada) {
    const { artigo, zona } = c.peca(selecionada);
    return `Escolha onde ${artigo} fica: ${c.zona(zona).instrucao}.${sufixoCancelar}`;
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
