// Orquestra a sessão: lê o convite, consulta a sala, envia ações e redesenha a partir
// da visão que o servidor devolve. As regras ficam no servidor.

import { carregarCena, PECAS } from "./dados.js";
import { criarCena, desenharCeu, prepararCeuPintado } from "./cena.js";
import { criarMeteoros } from "./meteoros.js";
import { criarSom } from "./som.js";
import { criarPainel } from "./painel.js";
import { criarTelas } from "./telas.js";
import { api, criarPolling, ErroApi } from "./api.js";
import { mensagemDeErro, textoConexaoDoParceiro, textoEncerrado, textoPecasDoParceiro } from "./mensagens.js";

const $ = (id) => document.getElementById(id);

/** Lê #convite=... e tira o fragmento da barra de endereço. */
function lerConvite() {
  const convite = new URLSearchParams(location.hash.slice(1)).get("convite");
  if (location.hash) {
    history.replaceState(null, "", location.pathname + location.search);
  }
  return convite || null;
}

async function iniciar() {
  const app = document.querySelector(".app");
  desenharCeu($("estrelas"), $("janelas"));
  prepararCeuPintado(app);
  const som = criarSom();
  const movimentoReduzido = window.matchMedia("(prefers-reduced-motion: reduce)");
  const meteoros = criarMeteoros($("meteoros"), {
    aoMeteoro: () => som.meteoro(),
    movimentoReduzido: () => movimentoReduzido.matches,
  });
  movimentoReduzido.addEventListener("change", () => meteoros.reiniciar());
  const geometria = await carregarCena();

  const estado = {
    convite: lerConvite(),
    visao: null,
    selecionada: null,
    // Nome da ação aguardando o servidor; só uma por vez.
    enviando: null,
    // Para dizer quem saiu depois que a visão já foi descartada.
    ultimoParceiro: null,
  };

  const painel = criarPainel(
    {
      par: $("par"),
      instrucao: $("instrucao"),
      falta: $("falta"),
      inventario: $("inventario"),
      retirar: $("retirar"),
      pronto: $("pronto"),
      motivo: $("motivo"),
      acoes: document.querySelector(".acoes"),
      aviso: $("aviso"),
      resultado: $("resultado"),
      resultadoTitulo: $("resultado-titulo"),
      resultadoTexto: $("resultado-texto"),
      ajustar: $("ajustar"),
    },
    {
      aoEscolherPeca: escolherPeca,
      aoRetirar: () => moverPeca(estado.selecionada, null, "retirar"),
      aoConfirmar: () =>
        enviar("pronto", () => api.marcarPronto(estado.visao.versao, !estado.visao.prontos.voce), (visao) => {
          if (visao.fase === "REVELADO") {
            return "Os dois confirmaram. Olhem o céu.";
          }
          return visao.prontos.voce ? null : "Confirmação desfeita. Ajuste à vontade.";
        }),
      aoAjustar: () =>
        enviar("ajustar", () => api.ajustar(), () => "De volta à montagem. As confirmações foram zeradas."),
    },
  );

  const telas = criarTelas(
    {
      entrada: $("entrada"),
      apelido: $("apelido"),
      erroApelido: $("apelido-erro"),
      botaoEntrada: $("entrar"),
      convite: $("convite"),
      linkConvite: $("link-convite"),
      copiar: $("copiar"),
      encerrado: $("encerrado"),
      tituloEncerrado: $("encerrado-titulo"),
      textoEncerrado: $("encerrado-texto"),
      criarOutro: $("criar-outro"),
      montagem: $("montagem"),
      par: $("par"),
      instrucao: $("instrucao"),
      sair: $("sair"),
      confirmarSaida: $("confirmar-saida"),
      textoConfirmarSaida: $("confirmar-saida-texto"),
      continuar: $("continuar"),
      encerrar: $("encerrar"),
    },
    {
      aoEnviarApelido: entrarOuCriar,
      aoEncerrar: sair,
      aoCriarOutro: () => {
        painel.avisar("");
        telas.mostrarEntrada(Boolean(estado.convite));
      },
      avisar: (texto) => painel.avisar(texto),
    },
  );

  const cena = criarCena({
    palco: $("palco"),
    camadaPecas: $("pecas"),
    camadaLugares: $("lugares"),
    geometria,
    aoEscolherPeca: escolherPeca,
    aoEscolherLugar: (lugar, bloqueio) => {
      if (estado.enviando) {
        return;
      }
      if (bloqueio) {
        painel.avisar(`Este lugar está ${bloqueio}. Escolha outro.`, "erro");
        return;
      }
      moverPeca(estado.selecionada, lugar, "pecas");
    },
  });

  let semConexao = false;
  const polling = criarPolling({
    aoReceber: (visao) => aplicarVisao(visao),
    aoFalhar: (erro, esperaMs) => {
      if (esperaMs !== null) {
        semConexao = true;
        // Anunciado uma vez por queda; a contagem muda só na parte visual.
        painel.avisarComDetalhe(
          "Sem conexão com o servidor.",
          `Nova tentativa em ${Math.round(esperaMs / 1000)} s.`,
          "erro",
        );
        return;
      }
      encerrarSessao(erro);
    },
    aoRecuperar: () => {
      if (semConexao) {
        semConexao = false;
        painel.avisar("Conexão de volta.");
      }
    },
  });

  // ------------------------------------------------------------ visão

  function aplicarVisao(visao, { propria = false } = {}) {
    const anterior = estado.visao;
    estado.visao = visao;
    if (visao.parceiro) {
      estado.ultimoParceiro = visao.parceiro.nome;
    }
    const minha = visao.pecas.find((p) => p.peca === estado.selecionada && p.assento === visao.voce.assento);
    if (visao.fase !== "MONTANDO" || !minha) {
      estado.selecionada = null;
    }
    let aviso = anterior ? novidade(anterior, visao, propria) : null;
    if (!anterior && estado.convite) {
      // Abrir o próprio convite no mesmo navegador cai na sala que já existe.
      estado.convite = null;
      aviso = "Este navegador já está em um terraço. Para usar o convite, abra-o em uma janela anônima ou em outro navegador.";
    }
    if (aviso) {
      painel.avisar(aviso);
    }
    desenhar();
  }

  /** O que mudou do lado do parceiro e merece aviso no aria-live. */
  function novidade(anterior, visao, propria) {
    const parceiro = visao.parceiro?.nome;
    const sinal = visao.sinal;
    const sinalNovo =
      sinal &&
      sinal.deAssento !== visao.voce.assento &&
      (anterior.sinal?.peca !== sinal.peca || anterior.sinal?.deAssento !== sinal.deAssento);
    if (sinalNovo) {
      return `${parceiro} sugeriu mover ${PECAS[sinal.peca].artigo}.`;
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

  function instrucao(visao) {
    if (estado.enviando) {
      return "Enviando…";
    }
    if (visao.fase === "REVELADO") {
      return "Olhem para cima.";
    }
    const selecionada = estado.selecionada;
    if (selecionada) {
      const zona = PECAS[selecionada].zona === "centro" ? "na área de observação, no centro" : "em um lugar lateral";
      return `Escolha onde ${PECAS[selecionada].artigo} fica: ${zona}. Esc cancela.`;
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

  /** Revelado: noite com meteoros do lado do telescópio. Qualquer outra fase: crepúsculo. */
  let momentoAtual = null;
  function definirMomento(visao) {
    const noite = visao?.fase === "REVELADO";
    const momento = noite ? "noite" : "crepusculo";
    // O acorde só toca na passagem vista ao vivo, não ao recarregar já em REVELADO.
    if (momentoAtual === "crepusculo" && momento === "noite") {
      som.revelacao();
    }
    momentoAtual = momento;
    app.dataset.momento = momento;
    if (noite) {
      meteoros.mostrar(visao.resultado?.ladoTelescopio);
    } else {
      meteoros.limpar();
    }
  }

  // Fase do último desenho: trocar de tela com o foco no body também leva o foco ao destino.
  let faseDesenhada = null;

  function desenhar() {
    const visao = estado.visao;
    if (!visao) {
      return;
    }
    const ativo = document.activeElement;
    const foco = lembrarFoco();
    definirMomento(visao);
    $("palco").dataset.enviando = String(Boolean(estado.enviando));
    telas.atualizarConfirmacaoSaida(visao.parceiro?.nome ?? null);
    if (visao.fase === "AGUARDANDO_PAR") {
      cena.limpar();
      telas.mostrarConvite(visao.convite ?? "");
      painel.desenhar(visao, null, $("instrucao").textContent);
    } else {
      telas.mostrarMontagem();
      cena.desenhar(visao, estado.selecionada);
      painel.desenhar(visao, estado.selecionada, instrucao(visao), estado.enviando);
    }
    foco();
    // A primeira tela da sessão (faseDesenhada nula) também conta como troca: no Safari o clique
    // não foca o botão, então o foco cai no body quando a tela de entrada some.
    levarFocoSeSumiu(ativo, faseDesenhada !== visao.fase);
    faseDesenhada = visao.fase;
  }

  /**
   * Quando o elemento focado some no redesenho (botão de entrar, convite, "Pronto", resultado),
   * leva o foco ao destino lógico da tela atual em vez de deixá-lo cair no body. Não mexe em
   * foco que continua visível em outro controle.
   */
  function levarFocoSeSumiu(ativo, trocouDeFase) {
    const noBody = !ativo || ativo === document.body;
    const sumiu = !noBody && !visivel(ativo);
    if (!sumiu && !(noBody && trocouDeFase)) {
      return;
    }
    const atual = document.activeElement;
    if (atual && atual !== document.body && atual !== ativo && visivel(atual)) {
      return; // lembrarFoco já devolveu o foco ao equivalente
    }
    destinoDoFoco(estado.visao.fase)?.focus();
  }

  function destinoDoFoco(fase) {
    const candidatos = {
      AGUARDANDO_PAR: [$("link-convite")],
      REVELADO: [$("resultado-titulo"), $("som")],
      MONTANDO: [$("inventario").querySelector("button"), $("pronto")],
    }[fase] ?? [];
    return candidatos.find((el) => el && visivel(el));
  }

  function visivel(el) {
    return el.isConnected && el.getClientRects().length > 0;
  }

  /** O redesenho troca os botões da cena e do inventário; devolve o foco ao equivalente. */
  function lembrarFoco() {
    const ativo = document.activeElement;
    const peca = ativo?.dataset?.peca;
    const lugar = ativo?.dataset?.lugar;
    const camada = ativo?.closest?.("#pecas, #lugares, #inventario")?.id;
    if (!camada || ativo.isConnected === false) {
      return () => {};
    }
    return () => {
      if (document.activeElement && document.activeElement !== document.body) {
        return;
      }
      const seletor = lugar ? `[data-lugar="${lugar}"]` : `[data-peca="${peca}"]`;
      $(camada).querySelector(seletor)?.focus();
    };
  }

  // ------------------------------------------------------------ ações

  function escolherPeca(peca, minha) {
    const visao = estado.visao;
    if (!visao || visao.fase !== "MONTANDO" || estado.enviando) {
      return;
    }
    if (!minha) {
      enviar("sinal", () => api.sinalizar(peca), () => `Você sugeriu que ${visao.parceiro?.nome} mova ${PECAS[peca].artigo}.`);
      return;
    }
    estado.selecionada = estado.selecionada === peca ? null : peca;
    desenhar();
    if (estado.selecionada) {
      cena.focarPrimeiroLugar();
    }
  }

  function moverPeca(peca, lugar, acao) {
    if (!peca) {
      return;
    }
    enviar(
      acao,
      () => api.moverPeca(peca, lugar, estado.visao.versao),
      () => {
        estado.selecionada = null;
        queueMicrotask(() => painel.focarItem(peca));
        return lugar ? null : `${PECAS[peca].nome} voltou para as suas peças.`;
      },
      peca,
    );
  }

  /**
   * Envia uma mutação; a cena só muda com a visão aceita pelo servidor. peca: a peça movida, para
   * onde o foco volta se o servidor recusar.
   */
  async function enviar(acao, chamada, aoAceitar, peca = null) {
    if (estado.enviando || !estado.visao) {
      return;
    }
    estado.enviando = acao;
    painel.avisar("");
    desenhar();
    try {
      const resposta = await chamada();
      if (!estado.visao) {
        return; // a sessão terminou durante o envio
      }
      polling.aceitar(resposta.etag);
      const mensagem = aoAceitar(resposta.visao);
      estado.enviando = null;
      aplicarVisao(resposta.visao, { propria: true });
      if (mensagem) {
        painel.avisar(mensagem);
      }
    } catch (erro) {
      estado.enviando = null;
      tratarErroDeAcao(erro, peca);
    } finally {
      estado.enviando = null;
    }
  }

  function tratarErroDeAcao(erro, peca) {
    if (!(erro instanceof ErroApi)) {
      throw erro;
    }
    if (["SEM_ASSENTO", "SALA_INEXISTENTE", "SALA_ENCERRADA"].includes(erro.codigo)) {
      polling.parar();
      encerrarSessao(erro);
      return;
    }
    const visaoAntes = estado.visao;
    if (erro.visao) {
      polling.aceitar(null);
      aplicarVisao(erro.visao, { propria: true });
      if (erro.status === 409 && peca) {
        focarAposRecusa(peca);
      }
    } else {
      desenhar();
      if (erro.status === 409) {
        polling.consultarAgora();
      }
    }
    painel.avisar(mensagemDeErro(erro, estado.visao ?? visaoAntes), "erro");
  }

  /**
   * A recusa redesenha a cena: o lugar escolhido pode ter virado bloqueado. Com a peça ainda
   * selecionada, o foco vai ao primeiro lugar livre; sem ela, volta à peça no inventário.
   */
  function focarAposRecusa(peca) {
    if (estado.selecionada) {
      cena.focarPrimeiroLugar();
    } else {
      painel.focarItem(peca);
    }
  }

  async function entrarOuCriar(apelido) {
    if (estado.enviando) {
      return;
    }
    estado.enviando = "entrada";
    telas.enviandoEntrada(true);
    painel.avisar("");
    const convite = estado.convite;
    try {
      const resposta = convite ? await api.entrar(convite, apelido) : await api.criarSala(apelido);
      estado.convite = null;
      estado.enviando = null;
      telas.enviandoEntrada(false);
      comecarSessao(resposta);
      if (convite) {
        painel.avisar(`Você entrou no terraço de ${resposta.visao.parceiro?.nome ?? "seu par"}.`);
      }
    } catch (erro) {
      estado.enviando = null;
      telas.enviandoEntrada(false);
      if (!(erro instanceof ErroApi)) {
        throw erro;
      }
      if (erro.codigo === "APELIDO_INVALIDO") {
        telas.erroApelido(mensagemDeErro(erro, null));
        return;
      }
      if (erro.codigo === "CONVITE_INVALIDO") {
        estado.convite = null;
        telas.mostrarEntrada(false);
      }
      painel.avisar(mensagemDeErro(erro, null), "erro");
    }
  }

  function comecarSessao(resposta) {
    estado.visao = null;
    estado.selecionada = null;
    estado.ultimoParceiro = null;
    polling.parar();
    polling.aceitar(resposta.etag);
    aplicarVisao(resposta.visao);
    polling.iniciar();
  }

  async function sair() {
    if (estado.enviando || !estado.visao) {
      return;
    }
    estado.enviando = "sair";
    telas.enviandoSair(true);
    desenhar();
    polling.parar();
    try {
      await api.sair();
    } catch (erro) {
      const semSala = erro instanceof ErroApi && ["SEM_ASSENTO", "SALA_INEXISTENTE", "SALA_ENCERRADA"].includes(erro.codigo);
      if (!semSala) {
        estado.enviando = null;
        telas.enviandoSair(false);
        desenhar();
        polling.iniciar();
        painel.avisar(mensagemDeErro(erro, estado.visao), "erro");
        return;
      }
    }
    estado.enviando = null;
    telas.enviandoSair(false);
    limparSessao();
    painel.avisar("");
    telas.mostrarEncerrado(textoEncerrado("VOCE_SAIU"));
  }

  function limparSessao() {
    faseDesenhada = null;
    estado.visao = null;
    estado.selecionada = null;
    cena.limpar();
    definirMomento(null);
    telas.fecharConfirmacaoSaida();
    // O botão some com a montagem: o som não pode continuar sem controle na tela.
    definirSom(false);
  }

  /** Estado do som: aria-pressed para leitores de tela; o texto visível fica fora do nome. */
  function definirSom(ligado) {
    $("som").setAttribute("aria-pressed", String(ligado));
    $("estado-som").textContent = ligado ? "ligado" : "desligado";
    som.definir(ligado);
  }

  /** Erro definitivo da consulta ou de uma ação: decide entre entrada e encerrado. */
  function encerrarSessao(erro) {
    const estavaNaSala = Boolean(estado.visao);
    const parceiro = estado.visao?.parceiro?.nome ?? estado.ultimoParceiro;
    limparSessao();
    if (!(erro instanceof ErroApi)) {
      painel.avisar("Algo deu errado ao falar com o servidor. Recarregue a página.", "erro");
      return;
    }
    const semAssento = erro.status === 401 || (erro.status === 404 && !estavaNaSala);
    if (semAssento || (estado.convite && erro.status !== 403)) {
      // Ao carregar sem sala (ou com convite em mãos), o caminho é entrar.
      telas.mostrarEntrada(Boolean(estado.convite), { focar: estavaNaSala });
      painel.avisar(estavaNaSala ? mensagemDeErro(erro, null) : "");
      return;
    }
    if (erro.codigo === "SALA_ENCERRADA" || erro.codigo === "SALA_INEXISTENTE") {
      painel.avisar("");
      telas.mostrarEncerrado(textoEncerrado(erro.motivo ?? "SALA_INEXISTENTE", parceiro));
      return;
    }
    telas.mostrarEntrada(false, { focar: false });
    painel.avisar(mensagemDeErro(erro, null), "erro");
  }

  // ------------------------------------------------------------ eventos globais

  document.addEventListener("keydown", (evento) => {
    if (evento.key === "Escape" && estado.selecionada) {
      const peca = estado.selecionada;
      estado.selecionada = null;
      desenhar();
      painel.focarItem(peca);
    }
  });

  $("som").addEventListener("click", (evento) => {
    definirSom(evento.currentTarget.getAttribute("aria-pressed") !== "true");
  });

  // ------------------------------------------------------------ início

  telas.mostrarCarregando();
  polling.iniciar();
}

iniciar().catch((erro) => {
  document.getElementById("aviso").textContent = `Não foi possível carregar a cena: ${erro.message}`;
});
