// Telas do painel fora da montagem: carregando, entrada (apelido), convite e encerrado.
// Só mostra e esconde; quem decide a transição é o main.

const CONTROLE = /\p{Cc}/u;

export function criarTelas(el, { aoEnviarApelido, aoCriarOutro, avisar }) {
  let comConvite = false;

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
    enviandoSair(enviando) {
      el.sair.textContent = enviando ? "Enviando…" : "Sair";
      if (enviando) {
        el.sair.setAttribute("aria-disabled", "true");
      } else {
        el.sair.removeAttribute("aria-disabled");
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
