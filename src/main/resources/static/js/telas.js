// Telas do painel fora da montagem: carregando, entrada (apelido), convite e encerrado, além do
// bloco que confirma a saída. Só mostra e esconde; quem decide a transição é o main.

import { textoConfirmarSaida } from "./mensagens.js";

const CONTROLE = /\p{Cc}/u;

export function criarTelas(el, { aoEnviarApelido, aoCriarOutro, aoEncerrar, avisar }) {
  let comConvite = false;
  let enviandoSaida = false;
  let acaoEncerrar = el.encerrar.textContent;

  el.entrada.addEventListener("submit", (evento) => {
    evento.preventDefault();
    if (el.botaoEntrada.getAttribute("aria-disabled") === "true") {
      return;
    }
    const apelido = el.apelido.value.trim();
    const erro = validarApelido(apelido);
    if (erro) {
      marcarErroApelido(erro);
      el.apelido.focus();
      return;
    }
    marcarErroApelido("");
    aoEnviarApelido(apelido);
  });

  el.apelido.addEventListener("input", () => {
    if (el.apelido.getAttribute("aria-invalid") === "true" && !validarApelido(el.apelido.value.trim())) {
      marcarErroApelido("");
    }
  });

  el.copiar.addEventListener("click", copiarConvite);
  el.criarOutro.addEventListener("click", aoCriarOutro);

  el.sair.addEventListener("click", () => {
    if (el.sair.getAttribute("aria-disabled") === "true") {
      return;
    }
    if (el.confirmarSaida.hidden) {
      abrirConfirmacaoSaida();
    } else {
      fecharConfirmacaoSaida({ focarSair: true });
    }
  });
  el.continuar.addEventListener("click", () => {
    // Com a saída em envio, o bloco fica aberto até o servidor responder (como no Esc).
    if (!enviandoSaida) {
      fecharConfirmacaoSaida({ focarSair: true });
    }
  });
  el.encerrar.addEventListener("click", () => {
    if (el.encerrar.getAttribute("aria-disabled") !== "true") {
      aoEncerrar();
    }
  });
  for (const alvo of [el.sair, el.confirmarSaida]) {
    alvo.addEventListener("keydown", (evento) => {
      if (evento.key === "Escape" && !el.confirmarSaida.hidden && !enviandoSaida) {
        // Esc aqui só fecha a confirmação; a peça selecionada (se houver) continua escolhida.
        evento.stopPropagation();
        fecharConfirmacaoSaida({ focarSair: true });
      }
    });
  }

  function abrirConfirmacaoSaida() {
    el.confirmarSaida.hidden = false;
    el.sair.setAttribute("aria-expanded", "true");
    el.sair.setAttribute("aria-controls", el.confirmarSaida.id);
    el.continuar.focus();
  }

  function fecharConfirmacaoSaida({ focarSair = false } = {}) {
    el.confirmarSaida.hidden = true;
    el.sair.setAttribute("aria-expanded", "false");
    el.sair.removeAttribute("aria-controls");
    if (focarSair) {
      el.sair.focus();
    }
  }

  function marcarErroApelido(texto) {
    el.erroApelido.textContent = texto;
    if (texto) {
      el.apelido.setAttribute("aria-invalid", "true");
    } else {
      el.apelido.removeAttribute("aria-invalid");
    }
  }

  async function copiarConvite() {
    const link = el.linkConvite.value;
    try {
      await navigator.clipboard.writeText(link);
      avisar("Convite copiado. Abra-o em outra janela anônima ou em outro navegador.");
      return;
    } catch {
      // Sem Clipboard API (ou sem permissão): cai para a seleção do campo.
    }
    el.linkConvite.focus();
    el.linkConvite.select();
    let copiou = false;
    try {
      copiou = document.execCommand("copy");
    } catch {
      copiou = false;
    }
    avisar(copiou ? "Convite copiado." : "O link está selecionado. Copie com Ctrl+C (ou ⌘+C).");
  }

  function mostrar(tela) {
    el.entrada.hidden = tela !== "entrada";
    el.convite.hidden = tela !== "convite";
    el.encerrado.hidden = tela !== "encerrado";
    el.montagem.hidden = tela !== "montagem";
    el.par.hidden = tela !== "convite" && tela !== "montagem";
    el.instrucao.hidden = tela === "encerrado";
    el.sair.hidden = tela !== "convite" && tela !== "montagem";
    if (el.sair.hidden) {
      fecharConfirmacaoSaida();
    }
  }

  return {
    mostrarCarregando() {
      mostrar("carregando");
      el.instrucao.textContent = "Procurando seu terraço…";
    },
    /** convite: true quando a URL trouxe #convite= (a ação vira "Entrar no terraço"). */
    mostrarEntrada(convite, { focar = true } = {}) {
      comConvite = convite;
      mostrar("entrada");
      el.instrucao.textContent = convite
        ? "Você foi chamado para montar um terraço a dois. Diga como quer ser chamado."
        : "Monte um terraço a dois antes de a noite cair. Comece pelo seu apelido.";
      el.botaoEntrada.textContent = convite ? "Entrar no terraço" : "Criar terraço";
      el.botaoEntrada.removeAttribute("aria-disabled");
      marcarErroApelido("");
      if (focar) {
        el.apelido.focus();
      }
    },
    enviandoEntrada(enviando) {
      if (enviando) {
        el.botaoEntrada.textContent = "Enviando…";
        el.botaoEntrada.setAttribute("aria-disabled", "true");
      } else {
        el.botaoEntrada.textContent = comConvite ? "Entrar no terraço" : "Criar terraço";
        el.botaoEntrada.removeAttribute("aria-disabled");
      }
    },
    erroApelido(texto) {
      marcarErroApelido(texto);
      el.apelido.focus();
    },
    mostrarConvite(token) {
      mostrar("convite");
      el.instrucao.textContent =
        "Mande o convite para a outra pessoa. O terraço abre sozinho quando ela entrar; deixe esta janela aberta.";
      el.linkConvite.value = `${location.origin}/#convite=${token}`;
    },
    mostrarMontagem() {
      mostrar("montagem");
    },
    mostrarEncerrado(texto) {
      mostrar("encerrado");
      el.textoEncerrado.textContent = texto;
      el.tituloEncerrado.focus();
    },
    /** Nome do parceiro (ou nulo, antes de ele entrar) no aviso e na ação da confirmação. */
    atualizarConfirmacaoSaida(nomeParceiro) {
      const { aviso, acao } = textoConfirmarSaida(nomeParceiro);
      el.textoConfirmarSaida.textContent = aviso;
      acaoEncerrar = acao;
      if (!enviandoSaida) {
        el.encerrar.textContent = acao;
      }
    },
    fecharConfirmacaoSaida,
    enviandoSair(enviando) {
      enviandoSaida = enviando;
      el.encerrar.textContent = enviando ? "Enviando…" : acaoEncerrar;
      for (const botao of [el.sair, el.encerrar]) {
        if (enviando) {
          botao.setAttribute("aria-disabled", "true");
        } else {
          botao.removeAttribute("aria-disabled");
        }
      }
    },
  };
}

function validarApelido(apelido) {
  const tamanho = [...apelido].length;
  if (tamanho === 0) {
    return "Escreva um apelido.";
  }
  if (tamanho > 20) {
    return "Use no máximo 20 caracteres.";
  }
  if (CONTROLE.test(apelido)) {
    return "Use só letras, números e símbolos comuns.";
  }
  return "";
}
