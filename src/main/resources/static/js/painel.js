// Painel de ações: quem está na sala, instrução, o que falta, peças e confirmação.

import { LARGURA_MINIATURA, LUGARES, PECAS, sprite, urlSprite } from "./dados.js";
import { textoBloqueio, textoMotivoConfirmacao, textoResultado } from "./mensagens.js";

const ICONE_FEITO =
  '<svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="10" cy="10" r="9" fill="currentColor" opacity=".25"/><path d="M5.5 10.5l3 3 6-7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const ICONE_PENDENTE =
  '<svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="10" cy="10" r="8" fill="none" stroke="currentColor" stroke-width="1.5" stroke-dasharray="3 3"/></svg>';

export function criarPainel(el, { aoEscolherPeca, aoRetirar, aoConfirmar, aoAjustar }) {
  el.pronto.addEventListener("click", () => {
    if (el.pronto.getAttribute("aria-disabled") !== "true") {
      aoConfirmar();
    }
  });
  el.retirar.addEventListener("click", () => {
    if (el.retirar.getAttribute("aria-disabled") !== "true") {
      aoRetirar();
    }
  });
  el.ajustar.addEventListener("click", () => {
    if (el.ajustar.getAttribute("aria-disabled") !== "true") {
      aoAjustar();
    }
  });

  function desenharPar(visao) {
    const itens = [
      `<li><span class="marca marca-voce" aria-hidden="true"></span><span class="nome"></span> (você)</li>`,
    ];
    if (visao.parceiro) {
      itens.push(`<li><span class="marca" aria-hidden="true"></span><span class="nome"></span><span class="conexao"></span></li>`);
    }
    el.par.innerHTML = itens.join("");
    const nomes = el.par.querySelectorAll(".nome");
    nomes[0].textContent = visao.voce.nome;
    if (visao.parceiro) {
      nomes[1].textContent = visao.parceiro.nome;
      el.par.querySelector(".conexao").textContent = visao.parceiro.conectado ? "" : " · sem sinal";
    }
  }

  function desenharFalta(visao) {
    const deParceiro = (p) => p.assento !== visao.voce.assento;
    const nomeParceiro = visao.parceiro?.nome ?? "a outra pessoa";
    const minhas = visao.pecas.filter((p) => !deParceiro(p));
    const dele = visao.pecas.filter(deParceiro);
    const obrigatoria = (lista) => lista.find((p) => PECAS[p.peca].obrigatoria);
    const segunda = (lista) => lista.some((p) => !PECAS[p.peca].obrigatoria && p.lugar);
    const centralDele = obrigatoria(dele);
    const linhas = [
      [`${PECAS[obrigatoria(minhas).peca].nome} no centro`, Boolean(obrigatoria(minhas).lugar)],
      [`${centralDele ? PECAS[centralDele.peca].nome : "Peça central"} no centro (${nomeParceiro})`, Boolean(centralDele?.lugar)],
      ["Sua segunda peça", segunda(minhas)],
      [`Segunda peça de ${nomeParceiro}`, segunda(dele)],
    ];
    el.falta.replaceChildren(
      ...linhas.map(([texto, feito]) => {
        const li = document.createElement("li");
        li.dataset.feito = String(feito);
        li.innerHTML = feito ? ICONE_FEITO : ICONE_PENDENTE;
        const estado = document.createElement("span");
        estado.className = "so-leitor";
        estado.textContent = feito ? ", pronto" : ", falta";
        li.append(document.createTextNode(texto), estado);
        return li;
      }),
    );
  }

  function desenharInventario(visao, selecionada, enviando) {
    const minhas = visao.pecas.filter((p) => p.assento === visao.voce.assento);
    const itens = [];
    let grupoAberto = false;
    for (const p of minhas) {
      const info = PECAS[p.peca];
      if (!info.obrigatoria && !grupoAberto) {
        const aviso = document.createElement("li");
        aviso.className = "grupo-escolha";
        aviso.textContent = "Escolha uma destas para as laterais:";
        itens.push(aviso);
        grupoAberto = true;
      }
      const li = document.createElement("li");
      const botao = document.createElement("button");
      botao.type = "button";
      botao.className = "item";
      botao.dataset.peca = p.peca;
      botao.setAttribute("aria-pressed", String(selecionada === p.peca));
      if (p.bloqueio || enviando) {
        botao.setAttribute("aria-disabled", "true");
      }
      const img = new Image();
      img.className = "miniatura";
      img.alt = "";
      img.src = urlSprite(sprite(p.peca, p.lugar ?? "C2"), "crepusculo", LARGURA_MINIATURA);
      const texto = document.createElement("span");
      const nome = document.createElement("span");
      nome.className = "nome-item";
      nome.textContent = info.nome;
      const estado = document.createElement("span");
      estado.className = "estado-item";
      const outraSecundaria = minhas.find((o) => o !== p && !PECAS[o.peca].obrigatoria && o.lugar);
      estado.textContent = p.bloqueio
        ? textoBloqueio(p.bloqueio, visao)
        : (p.lugar
          ? `No terraço: ${LUGARES[p.lugar].nome}`
          : !info.obrigatoria && outraSecundaria
            ? `Troca ${PECAS[outraSecundaria.peca].artigo} por ${info.artigo}`
            : descreverZona(info));
      texto.append(nome, estado);
      botao.append(img, texto);
      botao.addEventListener("click", () => {
        if (botao.getAttribute("aria-disabled") !== "true") {
          aoEscolherPeca(p.peca, true);
        }
      });
      li.append(botao);
      itens.push(li);
    }
    el.inventario.replaceChildren(...itens);
    const selecionadaColocada = minhas.find((p) => p.peca === selecionada && p.lugar);
    el.retirar.hidden = !selecionadaColocada || PECAS[selecionada].obrigatoria;
    marcarEnvio(el.retirar, enviando === "retirar", "Tirar do terraço", enviando);
  }

  function desenharAcoes(visao, enviando) {
    const { podeConfirmar } = visao.confirmacao;
    const voceConfirmou = visao.prontos.voce;
    const texto = voceConfirmou ? "Voltar a ajustar" : "Pronto para olhar o céu";
    marcarEnvio(el.pronto, enviando === "pronto", texto, enviando);
    if (!enviando && !podeConfirmar && !voceConfirmou) {
      el.pronto.setAttribute("aria-disabled", "true");
    }
    el.motivo.textContent = textoMotivoConfirmacao(visao);
    // Em REVELADO some só a confirmação; o som continua acessível para os meteoros.
    el.pronto.hidden = visao.fase === "REVELADO";
    el.motivo.hidden = visao.fase === "REVELADO";
  }

  function desenharResultado(visao, enviando) {
    el.resultado.hidden = visao.fase !== "REVELADO" || !visao.resultado;
    if (el.resultado.hidden) {
      return;
    }
    const { titulo, texto } = textoResultado(visao);
    el.resultadoTitulo.textContent = titulo;
    el.resultadoTexto.textContent = texto;
    marcarEnvio(el.ajustar, enviando === "ajustar", "Ajustar de novo", enviando);
  }

  return {
    /** enviando: null ou o nome da ação aguardando o servidor ("pecas", "retirar", "pronto"...). */
    desenhar(visao, selecionada, instrucao, enviando = null) {
      desenharPar(visao);
      el.instrucao.textContent = instrucao;
      if (visao.fase === "AGUARDANDO_PAR") {
        return;
      }
      desenharFalta(visao);
      desenharInventario(visao, selecionada, enviando);
      desenharAcoes(visao, enviando);
      desenharResultado(visao, enviando);
      el.falta.closest("section").hidden = visao.fase === "REVELADO";
      el.inventario.closest("section").hidden = visao.fase === "REVELADO";
    },
    avisar(texto, tipo = "info") {
      el.aviso.dataset.tipo = tipo;
      el.aviso.textContent = texto;
    },
    /**
     * Aviso com uma parte que muda sem ser reanunciada: o leitor de tela ouve `fixo` uma vez;
     * `variavel` fica visível, mas aria-hidden, e só ela é atualizada enquanto `fixo` não mudar.
     */
    avisarComDetalhe(fixo, variavel, tipo = "info") {
      el.aviso.dataset.tipo = tipo;
      const [texto, detalhe] = el.aviso.childNodes;
      if (texto?.nodeType === Node.TEXT_NODE && texto.data === fixo && detalhe?.dataset?.detalhe) {
        detalhe.textContent = ` ${variavel}`;
        return;
      }
      const span = document.createElement("span");
      span.dataset.detalhe = "true";
      span.setAttribute("aria-hidden", "true");
      span.textContent = ` ${variavel}`;
      el.aviso.replaceChildren(document.createTextNode(fixo), span);
    },
    focarItem(peca) {
      el.inventario.querySelector(`[data-peca="${peca}"]`)?.focus();
    },
  };
}

/** Botão de ação: "Enviando…" enquanto é a ação em andamento; indisponível durante qualquer envio. */
function marcarEnvio(botao, emAndamento, texto, enviando) {
  botao.textContent = emAndamento ? "Enviando…" : texto;
  if (enviando) {
    botao.setAttribute("aria-disabled", "true");
  } else {
    botao.removeAttribute("aria-disabled");
  }
}

function descreverZona(info) {
  return info.zona === "centro" ? "Vai na área de observação, no centro" : "Vai em um dos lugares laterais";
}
