// Orquestra a sessão: consulta a sala, envia ações e publica o estado da tela a partir da visão
// que o servidor devolve. As regras ficam no servidor. É o main.js antigo portado decisão por
// decisão: no lugar de desenhar o DOM, publica um retrato para o useSyncExternalStore, e no lugar
// de .focus(), deixa um PedidoDeFoco que a tela aplica depois do render.

import { type Api, ErroApi, type Resposta } from "../api/cliente";
import { criarPolling as criarPollingPadrao, type GanchosDoPolling, type Polling } from "../api/polling";
import type { CenarioFront } from "../cenario/tipos";
import { type ErroComCodigo, mensagemDeErro, textoEncerrado } from "../dominio/mensagens";
import type { Visao } from "../dominio/visao";
import type { Aviso } from "./aviso";
import { type Agendar, agendarComSetTimeout, DURACAO_DESTAQUE_MS, pecasQueMudaram } from "./destaques";
import { instrucao, novidade } from "./novidade";
import type { Acao, EstadoDaSessao, EstadoDaTela, PedidoDeFoco } from "./tipos";

export type { Acao, EstadoDaSessao, EstadoDaTela, PedidoDeFoco, Tela } from "./tipos";

export interface OpcoesDaSessao {
  api: Api;
  aviso: Aviso;
  /** O token de #convite= que a página trouxe, já tirado da barra de endereço. */
  convite: string | null;
  /** O cenário em jogo, já carregado antes de o app montar. */
  cenario: CenarioFront;
  criarPolling?: (api: Api, ganchos: GanchosDoPolling) => Polling;
  /** Agenda o fim de cada destaque; o padrão é o setTimeout. */
  agendar?: Agendar;
}

export interface Sessao {
  /** O retrato atual; o mesmo objeto enquanto nada mudar. */
  obter: () => EstadoDaSessao;
  /** Chama o ouvinte a cada mudança; devolve a função que cancela a assinatura. */
  assinar: (ouvinte: () => void) => () => void;
  iniciar: () => void;
  /** Para o polling (desmontagem, StrictMode); iniciar() retoma. */
  parar: () => void;
  escolherPeca: (peca: string, minha: boolean) => Promise<void>;
  /** bloqueio: o motivo de o lugar não aceitar a peça selecionada ("ocupado"...), ou null. */
  escolherLugar: (lugar: string, bloqueio: string | null) => Promise<void>;
  retirar: () => Promise<void>;
  confirmar: () => Promise<void>;
  /** Pede o ajuste (ou aceita o do outro): até a F5, é o que o "Ajustar de novo" faz. */
  ajustar: () => Promise<void>;
  enviarApelido: (apelido: string) => Promise<void>;
  /** O campo voltou a ter um apelido válido: o erro do servidor some (a validação fica na tela). */
  limparErroApelido: () => void;
  criarOutro: () => void;
  abrirSaida: () => void;
  fecharSaida: () => void;
  encerrar: () => Promise<void>;
  cancelarSelecao: () => void;
  alternarSom: () => void;
  /** O foco pedido desde a última leitura (o último vence), ou null; ler limpa o pedido. */
  consumirFoco: () => PedidoDeFoco | null;
}

/**
 * As ações que o painel e as folhas dele pedem à sessão, num objeto só: uma ação nova entra aqui e
 * na folha que a usa, sem tocar no App nem no Painel.
 */
export type AcoesDaSessao = Pick<
  Sessao,
  "escolherPeca" | "retirar" | "confirmar" | "ajustar" | "criarOutro" | "enviarApelido" | "limparErroApelido"
>;

/** Telas sem sala; com visão, a tela sai da fase dela. */
type TelaSemSala = Extract<EstadoDaTela, { tela: "carregando" | "entrada" | "encerrado" }>;

/**
 * A tela mostrada sem visão: uma tela sem sala ou, depois de um erro fora da API, a última tela
 * da sala, que fica parada como estava.
 */
type TelaSemVisao = TelaSemSala | Extract<EstadoDaTela, { tela: "convite" | "montagem" }>;

/** O aviso de um erro fora da API (um defeito, não uma resposta do servidor). */
const MENSAGEM_DE_DEFEITO = "Algo deu errado ao falar com o servidor. Recarregue a página.";

/** O mesmo array em todo retrato: a cena só redesenha quando os destaques mudam de fato. */
const SEM_DESTAQUES: readonly string[] = [];

const CODIGOS_SEM_SALA = new Set(["SEM_ASSENTO", "SALA_INEXISTENTE", "SALA_ENCERRADA"]);

const semSala = (erro: unknown): boolean => erro instanceof ErroApi && CODIGOS_SEM_SALA.has(erro.codigo);

/** A peça selecionada segue escolhível na visão nova: ainda na montagem e ainda sua. */
function selecaoContinua(visao: Visao, selecionada: string | null): boolean {
  return (
    visao.fase === "MONTANDO" && visao.pecas.some((p) => p.peca === selecionada && p.assento === visao.voce.assento)
  );
}

/** Erro fora da API (um defeito, não uma resposta) lido como ErroComCodigo, para a mensagem genérica. */
function comoErroComCodigo(erro: unknown): ErroComCodigo {
  return erro instanceof ErroApi ? erro : { codigo: "DESCONHECIDO", status: 0, motivo: null };
}

/** As mutações aceitas trazem a visão nova; sem ela, o contrato quebrou. */
function exigirVisao(resposta: Resposta): Visao {
  if (resposta.visao === null) {
    throw new Error("Resposta da API sem a visão da sala");
  }
  return resposta.visao;
}

export function criarSessao({
  api,
  aviso,
  convite,
  cenario,
  criarPolling = criarPollingPadrao,
  agendar = agendarComSetTimeout,
}: OpcoesDaSessao): Sessao {
  const estado = {
    convite,
    visao: null as Visao | null,
    selecionada: null as string | null,
    // Ação aguardando o servidor; só uma por vez.
    enviando: null as Acao | null,
    // Para dizer quem saiu depois que a visão já foi descartada.
    ultimoParceiro: null as string | null,
    semVisao: { tela: "carregando" } as TelaSemVisao,
    confirmandoSaida: false,
    somLigado: false,
  };
  // Muda a cada sessão encerrada: respostas de mutações enviadas antes disso são descartadas, como
  // as consultas velhas do polling.
  let epoca = 0;
  let foco: PedidoDeFoco | null = null;
  const ouvintes = new Set<() => void>();
  // Peça em destaque → cancela o fim dele. A lista muda de objeto só quando o conjunto muda.
  const fimDosDestaques = new Map<string, () => void>();
  let destaques: readonly string[] = SEM_DESTAQUES;
  let retrato = montarRetrato();

  let semConexao = false;
  const polling = criarPolling(api, {
    aoReceber: (visao) => {
      aplicarVisao(visao, false);
    },
    aoFalhar: (erro, esperaMs) => {
      if (esperaMs !== null) {
        semConexao = true;
        // Anunciado uma vez por queda; a contagem muda só na parte visual.
        aviso.avisarComDetalhe(
          "Sem conexão com o servidor.",
          `Nova tentativa em ${String(Math.round(esperaMs / 1000))} s.`,
          "erro",
        );
        return;
      }
      encerrarSessao(erro);
    },
    aoRecuperar: () => {
      if (semConexao) {
        semConexao = false;
        aviso.avisar("Conexão de volta.");
      }
    },
  });

  // ------------------------------------------------------------ retrato

  function telaAtual(): EstadoDaTela {
    const { visao, selecionada, enviando } = estado;
    if (visao === null) {
      return estado.semVisao;
    }
    if (visao.fase === "AGUARDANDO_PAR") {
      return { tela: "convite", visao };
    }
    return {
      tela: "montagem",
      visao,
      selecionada,
      sugerindo: null,
      destaques,
      instrucao: instrucao(cenario, visao, selecionada, enviando),
    };
  }

  /** Revelado: noite com meteoros do lado do telescópio. Qualquer outra fase: crepúsculo. */
  function camposForaDaTela(): Omit<EstadoDaSessao, keyof EstadoDaTela> {
    const noite = estado.visao?.fase === "REVELADO";
    return {
      enviando: estado.enviando,
      cenaVazia: estado.visao === null,
      confirmandoSaida: estado.confirmandoSaida,
      somLigado: estado.somLigado,
      momento: noite ? "noite" : "crepusculo",
      ladoTelescopio: noite ? (estado.visao?.resultado?.["ladoTelescopio"] ?? null) : null,
    };
  }

  function montarRetrato(): EstadoDaSessao {
    return { ...telaAtual(), ...camposForaDaTela() };
  }

  /** O desenhar() de antes: um retrato novo para quem assina. */
  function publicar(): void {
    retrato = montarRetrato();
    for (const ouvinte of ouvintes) {
      ouvinte();
    }
  }

  /** Pedido feito antes da publicação que o acompanha: a tela o lê no commit seguinte. */
  function pedirFoco(pedido: PedidoDeFoco): void {
    foco = pedido;
  }

  // ------------------------------------------------------------ telas sem sala

  function mostrarSemSala(tela: TelaSemSala, pedido: PedidoDeFoco | null): void {
    estado.semVisao = tela;
    // O botão de sair some fora do convite e da montagem; a confirmação vai junto.
    estado.confirmandoSaida = false;
    if (pedido) {
      pedirFoco(pedido);
    }
    publicar();
  }

  /** comConvite: a página trouxe #convite= (a ação vira "Entrar no terraço"). */
  function mostrarEntrada(comConvite: boolean, focar = true): void {
    mostrarSemSala(
      { tela: "entrada", comConvite, trocaDeSala: false, erroApelido: "" },
      focar ? { alvo: "apelido" } : null,
    );
  }

  function mostrarEncerrado({ titulo, texto }: { titulo: string; texto: string }): void {
    mostrarSemSala({ tela: "encerrado", titulo, texto }, { alvo: "titulo-encerrado" });
  }

  // ------------------------------------------------------------ destaques

  function listarDestaques(): void {
    destaques = fimDosDestaques.size === 0 ? SEM_DESTAQUES : [...fimDosDestaques.keys()];
  }

  /** A mesma peça de novo reinicia o tempo dela. */
  function destacar(pecas: readonly string[]): void {
    for (const peca of pecas) {
      fimDosDestaques.get(peca)?.();
      fimDosDestaques.delete(peca);
      fimDosDestaques.set(
        peca,
        agendar(() => {
          fimDosDestaques.delete(peca);
          listarDestaques();
          publicar();
        }, DURACAO_DESTAQUE_MS),
      );
    }
    if (pecas.length > 0) {
      listarDestaques();
    }
  }

  function apagarDestaques(): void {
    for (const cancelar of fimDosDestaques.values()) {
      cancelar();
    }
    fimDosDestaques.clear();
    listarDestaques();
  }

  // ------------------------------------------------------------ visão

  function aplicarVisao(visao: Visao, propria: boolean): void {
    const anterior = estado.visao;
    estado.visao = visao;
    if (visao.parceiro) {
      estado.ultimoParceiro = visao.parceiro.nome;
    }
    if (!selecaoContinua(visao, estado.selecionada)) {
      estado.selecionada = null;
    }
    if (anterior) {
      destacar(pecasQueMudaram(anterior, visao));
    }
    let texto = anterior ? novidade(cenario, anterior, visao, propria) : null;
    if (!anterior && estado.convite) {
      // Abrir o próprio convite no mesmo navegador cai na sala que já existe.
      estado.convite = null;
      texto =
        "Este navegador já está em um terraço. Para usar o convite, abra-o em uma janela anônima ou em outro navegador.";
    }
    if (texto) {
      aviso.avisar(texto);
    }
    publicar();
  }

  // ------------------------------------------------------------ ações

  async function escolherPeca(peca: string, minha: boolean): Promise<void> {
    const visao = estado.visao;
    if (!visao || visao.fase !== "MONTANDO" || estado.enviando) {
      return;
    }
    if (!minha) {
      // Até a escolha do destino (F3), sugere o primeiro destino livre; sem destino, o toque não faz nada.
      const destino = visao.pecas.find((p) => p.peca === peca)?.destinosSugeriveis?.[0];
      if (destino === undefined) {
        return;
      }
      await enviar(
        "sinal",
        () => api.sinalizar(peca, destino),
        () => `Você sugeriu que ${String(visao.parceiro?.nome)} mova ${cenario.peca(peca).artigo}.`,
      );
      return;
    }
    estado.selecionada = estado.selecionada === peca ? null : peca;
    if (estado.selecionada) {
      pedirFoco({ alvo: "primeiro-lugar" });
    }
    publicar();
  }

  async function escolherLugar(lugar: string, bloqueio: string | null): Promise<void> {
    if (estado.enviando) {
      return;
    }
    if (bloqueio) {
      aviso.avisar(`Este lugar está ${bloqueio}. Escolha outro.`, "erro");
      return;
    }
    await moverPeca(estado.selecionada, lugar, "pecas");
  }

  async function moverPeca(peca: string | null, lugar: string | null, acao: "pecas" | "retirar"): Promise<void> {
    if (!peca) {
      return;
    }
    await enviar(
      acao,
      (visao) => api.moverPeca(peca, lugar, visao.versao),
      () => {
        estado.selecionada = null;
        pedirFoco({ alvo: "item", peca });
        return lugar ? null : `${cenario.peca(peca).nome} voltou para as suas peças.`;
      },
      peca,
    );
  }

  /**
   * Envia uma mutação; a cena só muda com a visão aceita pelo servidor. peca: a peça movida, para
   * onde o foco volta se o servidor recusar.
   */
  async function enviar(
    acao: Acao,
    chamada: (visao: Visao) => Promise<Resposta>,
    aoAceitar: (visao: Visao) => string | null,
    peca: string | null = null,
  ): Promise<void> {
    const visao = estado.visao;
    if (estado.enviando || !visao) {
      return;
    }
    const minhaEpoca = epoca;
    estado.enviando = acao;
    aviso.avisar("");
    publicar();
    try {
      const resposta = await chamada(visao);
      if (minhaEpoca !== epoca) {
        // A sessão terminou durante o envio (e já soltou o "enviando"): a resposta é de outra sala.
        return;
      }
      polling.aceitar(resposta.etag);
      const nova = exigirVisao(resposta);
      const mensagem = aoAceitar(nova);
      estado.enviando = null;
      aplicarVisao(nova, true);
      if (mensagem) {
        aviso.avisar(mensagem);
      }
    } catch (erro) {
      if (minhaEpoca !== epoca) {
        if (!(erro instanceof ErroApi)) {
          throw erro;
        }
        return;
      }
      estado.enviando = null;
      tratarErroDeAcao(erro, peca);
    }
  }

  function tratarErroDeAcao(erro: unknown, peca: string | null): void {
    if (!(erro instanceof ErroApi)) {
      // Um defeito, não uma resposta: a tela sai do "enviando" e avisa antes de o erro seguir.
      publicar();
      aviso.avisar(MENSAGEM_DE_DEFEITO, "erro");
      throw erro;
    }
    if (CODIGOS_SEM_SALA.has(erro.codigo)) {
      polling.parar();
      encerrarSessao(erro);
      return;
    }
    const visaoAntes = estado.visao;
    if (erro.visao) {
      polling.aceitar(null);
      if (erro.status === 409 && peca) {
        focarAposRecusa(erro.visao, peca);
      }
      aplicarVisao(erro.visao, true);
    } else {
      publicar();
      if (erro.status === 409) {
        polling.consultarAgora();
      }
    }
    aviso.avisar(mensagemDeErro(erro, estado.visao ?? visaoAntes), "erro");
  }

  /**
   * A recusa redesenha a cena: o lugar escolhido pode ter virado bloqueado. Com a peça ainda
   * selecionada na visão nova, o foco vai ao primeiro lugar livre (ou à peça, se não houver);
   * senão, volta à peça no inventário. Decidido antes de aplicar a visão, que publica o retrato.
   */
  function focarAposRecusa(visao: Visao, peca: string): void {
    pedirFoco(selecaoContinua(visao, estado.selecionada) ? { alvo: "apos-recusa", peca } : { alvo: "item", peca });
  }

  async function entrarOuCriar(apelido: string): Promise<void> {
    if (estado.enviando) {
      return;
    }
    estado.enviando = "entrada";
    // Como o submit do form antigo: cada tentativa começa sem o erro da anterior.
    definirErroApelido("");
    publicar();
    aviso.avisar("");
    const conviteEmMaos = estado.convite;
    try {
      const resposta = conviteEmMaos ? await api.entrar(conviteEmMaos, apelido) : await api.criarSala(apelido);
      estado.convite = null;
      estado.enviando = null;
      const visao = exigirVisao(resposta);
      comecarSessao(visao, resposta.etag);
      if (conviteEmMaos) {
        aviso.avisar(`Você entrou no terraço de ${visao.parceiro?.nome ?? "seu par"}.`);
      }
    } catch (erro) {
      estado.enviando = null;
      if (!(erro instanceof ErroApi)) {
        publicar();
        throw erro;
      }
      if (erro.codigo === "APELIDO_INVALIDO") {
        marcarErroApelido(mensagemDeErro(erro, null));
        return;
      }
      if (erro.codigo === "CONVITE_INVALIDO") {
        estado.convite = null;
        mostrarEntrada(false);
      } else {
        publicar();
      }
      aviso.avisar(mensagemDeErro(erro, null), "erro");
    }
  }

  /** Devolve se o erro mudou. */
  function definirErroApelido(texto: string): boolean {
    if (estado.semVisao.tela !== "entrada" || estado.semVisao.erroApelido === texto) {
      return false;
    }
    estado.semVisao = { ...estado.semVisao, erroApelido: texto };
    return true;
  }

  function marcarErroApelido(texto: string): void {
    definirErroApelido(texto);
    pedirFoco({ alvo: "apelido" });
    publicar();
  }

  function comecarSessao(visao: Visao, etag: string | null): void {
    estado.visao = null;
    estado.selecionada = null;
    estado.ultimoParceiro = null;
    polling.parar();
    polling.aceitar(etag);
    aplicarVisao(visao, false);
    polling.iniciar();
  }

  async function sair(): Promise<void> {
    if (estado.enviando || !estado.visao) {
      return;
    }
    estado.enviando = "sair";
    publicar();
    polling.parar();
    try {
      await api.sair();
    } catch (erro) {
      if (!semSala(erro)) {
        estado.enviando = null;
        publicar();
        polling.iniciar();
        aviso.avisar(mensagemDeErro(comoErroComCodigo(erro), estado.visao), "erro");
        return;
      }
    }
    limparSessao();
    aviso.avisar("");
    mostrarEncerrado(textoEncerrado("VOCE_SAIU"));
  }

  /**
   * Sem visão, o momento volta ao crepúsculo e a cena fica vazia. A ação em voo deixa de valer: o
   * envio é solto e a resposta dela, quando chegar, é descartada (a época mudou).
   */
  function limparSessao(): void {
    epoca += 1;
    estado.enviando = null;
    estado.visao = null;
    estado.selecionada = null;
    estado.confirmandoSaida = false;
    apagarDestaques();
    // O botão some com a montagem: o som não pode continuar sem controle na tela.
    estado.somLigado = false;
  }

  /** Erro definitivo da consulta ou de uma ação: decide entre entrada e encerrado. */
  function encerrarSessao(erro: unknown): void {
    const estavaNaSala = estado.visao !== null;
    const parceiro = estado.visao?.parceiro?.nome ?? estado.ultimoParceiro;
    const telaDaVez = telaAtual();
    limparSessao();
    if (!(erro instanceof ErroApi)) {
      // Como antes: a tela da vez fica parada, só a cena, o céu, o som e a confirmação de saída
      // voltam ao início.
      estado.semVisao = telaDaVez;
      publicar();
      aviso.avisar(MENSAGEM_DE_DEFEITO, "erro");
      return;
    }
    const semAssento = erro.status === 401 || (erro.status === 404 && !estavaNaSala);
    if (semAssento || (estado.convite && erro.status !== 403)) {
      // Ao carregar sem sala (ou com convite em mãos), o caminho é entrar.
      mostrarEntrada(Boolean(estado.convite), estavaNaSala);
      aviso.avisar(estavaNaSala ? mensagemDeErro(erro, null) : "");
      return;
    }
    if (erro.codigo === "SALA_ENCERRADA" || erro.codigo === "SALA_INEXISTENTE") {
      aviso.avisar("");
      mostrarEncerrado(textoEncerrado(erro.motivo ?? "SALA_INEXISTENTE", parceiro));
      return;
    }
    mostrarEntrada(false, false);
    aviso.avisar(mensagemDeErro(erro, null), "erro");
  }

  /** A confirmação de saída não abre nem fecha com a saída em envio. */
  function definirConfirmacaoSaida(aberta: boolean): void {
    if (estado.enviando === "sair") {
      return;
    }
    estado.confirmandoSaida = aberta;
    pedirFoco({ alvo: aberta ? "continuar" : "sair" });
    publicar();
  }

  return {
    obter: () => retrato,
    assinar(ouvinte) {
      ouvintes.add(ouvinte);
      return () => {
        ouvintes.delete(ouvinte);
      };
    },
    iniciar() {
      mostrarSemSala({ tela: "carregando" }, null);
      polling.iniciar();
    },
    parar() {
      polling.parar();
    },
    escolherPeca,
    escolherLugar,
    retirar: () => moverPeca(estado.selecionada, null, "retirar"),
    confirmar: () =>
      enviar(
        "pronto",
        (visao) => api.marcarPronto(visao.versao, !visao.prontos.voce),
        (visao) => {
          if (visao.fase === "REVELADO") {
            return "Os dois confirmaram. Olhem o céu.";
          }
          return visao.prontos.voce ? null : "Confirmação desfeita. Ajuste à vontade.";
        },
      ),
    ajustar: () =>
      enviar(
        "ajustar",
        () => api.pedirAjuste(),
        (visao) => (visao.fase === "MONTANDO" ? "De volta à montagem. As confirmações foram zeradas." : null),
      ),
    enviarApelido: entrarOuCriar,
    limparErroApelido() {
      if (definirErroApelido("")) {
        publicar();
      }
    },
    criarOutro() {
      aviso.avisar("");
      mostrarEntrada(Boolean(estado.convite));
    },
    abrirSaida: () => {
      definirConfirmacaoSaida(true);
    },
    fecharSaida: () => {
      definirConfirmacaoSaida(false);
    },
    encerrar: sair,
    cancelarSelecao() {
      const peca = estado.selecionada;
      if (!peca) {
        return;
      }
      estado.selecionada = null;
      pedirFoco({ alvo: "item", peca });
      publicar();
    },
    alternarSom() {
      estado.somLigado = !estado.somLigado;
      publicar();
    },
    consumirFoco() {
      const pedido = foco;
      foco = null;
      return pedido;
    },
  };
}
