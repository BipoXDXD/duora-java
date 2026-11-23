// Orquestra a sessão: consulta a sala, envia ações e publica o estado da tela a partir da visão
// que o servidor devolve. As regras ficam no servidor. É o main.js antigo portado decisão por
// decisão: no lugar de desenhar o DOM, publica um retrato para o useSyncExternalStore, e no lugar
// de .focus(), deixa um PedidoDeFoco que a tela aplica depois do render.

import { type Api, ErroApi, type Resposta } from "../api/cliente";
import { criarPolling as criarPollingPadrao, type GanchosDoPolling, type Polling } from "../api/polling";
import type { CenarioFront } from "../cenario/tipos";
import { resumirConvite } from "../convite";
import { ehToque as ehToqueDoDispositivo } from "../dispositivo";
import { situacaoDoAjuste, textoDoPedido } from "../dominio/ajuste";
import { type ErroComCodigo, mensagemDeErro, textoEncerrado, textoRecusaDoSinal } from "../dominio/mensagens";
import { sinalDaMontagem, type Visao } from "../dominio/visao";
import type { Aviso } from "./aviso";
import { type Agendar, agendarComSetTimeout, DURACAO_DESTAQUE_MS, pecasQueMudaram } from "./destaques";
import {
  instrucao,
  novidade,
  SUFIXO_ESC_CANCELA,
  TEXTO_NOITE_REVELADA,
  textoSinalEnviado,
  textoSinalRecebido,
} from "./novidade";
import type { Acao, EstadoDaSessao, EstadoDaTela, PedidoDeFoco } from "./tipos";

export type { Acao, EstadoDaSessao, EstadoDaTela, PedidoDeFoco, Tela } from "./tipos";

export interface OpcoesDaSessao {
  api: Api;
  aviso: Aviso;
  /** O token de #convite= que a página trouxe, já tirado da barra de endereço. */
  convite: string | null;
  /** Tela de toque, sem Esc à mão; o padrão pergunta ao navegador. */
  ehToque?: () => boolean;
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
  /**
   * Na montagem, a própria peça liga ou desliga a seleção; a do parceiro, a sugestão de destino
   * para ela. As duas não convivem: escolher uma desfaz a outra.
   */
  escolherPeca: (peca: string, minha: boolean) => Promise<void>;
  /**
   * Coloca a peça selecionada no lugar ou, durante a sugestão, sugere o lugar ao parceiro.
   * bloqueio: o motivo de o lugar não aceitar a peça selecionada ("ocupado"...), ou null.
   */
  escolherLugar: (lugar: string, bloqueio: string | null) => Promise<void>;
  /** Leva a própria peça ao lugar (o "Mover …" de uma sugestão do parceiro). */
  moverPara: (peca: string, lugar: string) => Promise<void>;
  retirar: () => Promise<void>;
  confirmar: () => Promise<void>;
  /** Pede o ajuste ou, com o pedido do outro pendente, aceita-o (R4). */
  pedirAjuste: () => Promise<void>;
  /** Recusa o pedido do outro ou cancela o próprio; a noite continua. */
  recusarAjuste: () => Promise<void>;
  /** Toca a própria lanterna da abertura; quem acende as duas é o servidor. */
  tocarAbertura: () => Promise<void>;
  enviarApelido: (apelido: string) => Promise<void>;
  /**
   * Um #convite= aberto nesta aba depois da carga. Sem sala, leva à entrada com o convite; o
   * convite do próprio terraço só avisa; o de outra sala pede a troca (que encerra a atual). Resolve
   * quando a decisão está na tela (reconhecer o próprio convite pode pedir o resumo dele).
   */
  receberConvite: (token: string) => Promise<void>;
  /** Desiste da troca de sala: o convite em espera cai e o terraço atual volta à tela. */
  continuarNoTerraco: () => void;
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
  | "escolherPeca"
  | "moverPara"
  | "retirar"
  | "confirmar"
  | "pedirAjuste"
  | "recusarAjuste"
  | "tocarAbertura"
  | "criarOutro"
  | "enviarApelido"
  | "limparErroApelido"
  | "continuarNoTerraco"
>;

/** Telas sem sala; com visão, a tela sai da fase dela. */
type TelaSemSala = Extract<EstadoDaTela, { tela: "carregando" | "entrada" | "encerrado" }>;

/**
 * A tela mostrada sem visão: uma tela sem sala ou, depois de um erro fora da API, a última tela
 * da sala, que fica parada como estava.
 */
type TelaSemVisao = TelaSemSala | Extract<EstadoDaTela, { tela: "convite" | "montagem" }>;

/** Abrir de novo o convite do próprio terraço enquanto ele espera a outra pessoa. */
const TEXTO_CONVITE_PROPRIO = "Este é o convite do seu terraço. Mande para a outra pessoa.";

/** Abrir de novo o convite do próprio terraço depois que a outra pessoa entrou (já foi usado). */
const TEXTO_JA_NESTE_TERRACO = "Você já está neste terraço.";

/**
 * Convite que a aba não conhece e não consegue resumir (sem Web Crypto): pode ser o desta sala, e a
 * troca o encerraria. Na dúvida, não há troca; a saída fica com quem decide sair.
 */
const TEXTO_CONVITE_SEM_CONFERIR =
  "Não deu para conferir este convite neste navegador. Se ele for de outro terraço, saia deste e abra o link de novo.";

/** O aviso de um erro fora da API (um defeito, não uma resposta do servidor). */
const MENSAGEM_DE_DEFEITO = "Algo deu errado ao falar com o servidor. Recarregue a página.";

/** O mesmo array em todo retrato: a cena só redesenha quando os destaques mudam de fato. */
const SEM_DESTAQUES: readonly string[] = [];

const CODIGOS_SEM_SALA = new Set(["SEM_ASSENTO", "SALA_INEXISTENTE", "SALA_ENCERRADA"]);

const semSala = (erro: unknown): boolean => erro instanceof ErroApi && CODIGOS_SEM_SALA.has(erro.codigo);

/** A sala atual acabou com a troca aberta: o convite continua de pé. */
const TEXTO_TROCA_SEM_SALA = "O terraço anterior terminou. Você pode entrar no convite.";

/** Na troca, a sala que acabou só libera o convite; fora dela, o texto do erro. */
const avisoDaSalaPerdida = (erro: ErroApi, emTroca: boolean): string =>
  emTroca && semSala(erro) ? TEXTO_TROCA_SEM_SALA : mensagemDeErro(erro, null);

/** Como cada tipo de ação reage a uma recusa do servidor: onde fica o foco e o que se diz. */
interface ReacaoARecusa {
  focar: (visao: Visao, peca: string) => void;
  texto: (erro: ErroApi, visao: Visao | null) => string;
}

/** A peça selecionada segue escolhível na visão nova: ainda na montagem e ainda sua. */
function selecaoContinua(visao: Visao, selecionada: string | null): boolean {
  return (
    visao.fase === "MONTANDO" && visao.pecas.some((p) => p.peca === selecionada && p.assento === visao.voce.assento)
  );
}

/** Destinos que a peça do parceiro aceita como sugestão; vazio fora da montagem ou para peça própria. */
function destinosSugeriveis(visao: Visao, peca: string | null): readonly string[] {
  if (visao.fase !== "MONTANDO") {
    return [];
  }
  const daVez = visao.pecas.find((p) => p.peca === peca && p.assento !== visao.voce.assento);
  return daVez?.destinosSugeriveis ?? [];
}

/** A sugestão segue de pé na visão nova: a peça do parceiro ainda tem para onde ir. */
const sugestaoContinua = (visao: Visao, sugerindo: string | null): boolean =>
  destinosSugeriveis(visao, sugerindo).length > 0;

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
  aviso: avisoDaTela,
  convite,
  cenario,
  criarPolling = criarPollingPadrao,
  agendar = agendarComSetTimeout,
  ehToque = ehToqueDoDispositivo,
}: OpcoesDaSessao): Sessao {
  const sufixoCancelar = ehToque() ? "" : SUFIXO_ESC_CANCELA;
  const estado = {
    // O convite em espera: o da carga ou um aberto depois; com sala, ele pede a troca.
    convite,
    visao: null as Visao | null,
    selecionada: null as string | null,
    // A peça do parceiro cujo destino você está escolhendo; nunca junto com a selecionada.
    sugerindo: null as string | null,
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
  // Os convites desta aba: o token com que ela entrou e todo visao.convite já visto. Abrir um deles
  // de novo não troca de sala.
  const convitesProprios = new Set<string>();
  let foco: PedidoDeFoco | null = null;
  const ouvintes = new Set<() => void>();
  // Peça em destaque → cancela o fim dele. A lista muda de objeto só quando o conjunto muda.
  const fimDosDestaques = new Map<string, () => void>();
  let destaques: readonly string[] = SEM_DESTAQUES;
  let retrato = montarRetrato();
  // O último texto escrito no #aviso: a sugestão que expira só apaga o aviso se ele ainda é o dela.
  let ultimoAviso = "";
  const aviso: Aviso = {
    avisar(...args) {
      ultimoAviso = args[0];
      avisoDaTela.avisar(...args);
    },
    avisarComDetalhe(...args) {
      ultimoAviso = args[0];
      avisoDaTela.avisarComDetalhe(...args);
    },
  };

  let semConexao = false;
  const polling = criarPolling(api, {
    aoReceber: (visao) => {
      // Com o próprio ajuste em voo, a mudança que o polling traz antes da resposta é desta pessoa:
      // o texto da ação a anuncia, e não "Bruno aceitou" ou "Bruno cancelou o pedido".
      aplicarVisao(visao, estado.enviando === "ajustar" || estado.enviando === "recusar-ajuste");
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
    const { visao, selecionada, sugerindo, enviando } = estado;
    // A troca de sala vence a sala atual enquanto o convite espera, mesmo com o polling seguindo.
    if (visao === null || emTroca()) {
      return estado.semVisao;
    }
    if (visao.fase === "AGUARDANDO_PAR") {
      return { tela: "convite", visao };
    }
    return {
      tela: "montagem",
      visao,
      selecionada,
      sugerindo,
      destaques,
      instrucao: instrucao(cenario, visao, { selecionada, enviando, sugerindo, sufixoCancelar }),
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

  /** A entrada que troca de sala: com o convite em espera, a sala atual fica por trás. */
  function mostrarTroca(): void {
    estado.semVisao = { tela: "entrada", comConvite: true, trocaDeSala: true, erroApelido: "" };
    estado.confirmandoSaida = false;
    // O botão do som some com a sala atual: o som não pode seguir sem controle na tela (WCAG 1.4.2).
    estado.somLigado = false;
    pedirFoco({ alvo: "apelido" });
  }

  function emTroca(): boolean {
    return estado.semVisao.tela === "entrada" && estado.semVisao.trocaDeSala;
  }

  /** Sai da troca sem tocar na sala: a tela volta a sair da visão. */
  function desfazerTroca(): void {
    estado.convite = null;
    estado.semVisao = { tela: "carregando" };
  }

  /**
   * O convite é desta sala: um que a aba já viu ou, numa aba sem essa memória (nova, recarregada,
   * de quem entrou), o que o servidor resume em conviteDaSala.
   */
  function conviteDestaSala(token: string, resumo: string | null): boolean {
    return convitesProprios.has(token) || (resumo !== null && resumo === estado.visao?.conviteDaSala);
  }

  /**
   * Com sala e convite em espera: o convite próprio só avisa; o de outra sala mostra a troca. Publica
   * a tela decidida. resumo: o do token, quando a aba não o conhece; null se ela o conhece ou se não
   * deu para calcular.
   */
  function decidirConvite(token: string, resumo: string | null): void {
    if (conviteDestaSala(token, resumo)) {
      desfazerTroca();
      aviso.avisar(estado.visao?.fase === "AGUARDANDO_PAR" ? TEXTO_CONVITE_PROPRIO : TEXTO_JA_NESTE_TERRACO);
    } else if (resumo === null) {
      desfazerTroca();
      aviso.avisar(TEXTO_CONVITE_SEM_CONFERIR);
    } else {
      estado.convite = token;
      mostrarTroca();
    }
    publicar();
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
    if (visao.convite) {
      convitesProprios.add(visao.convite);
    }
    if (visao.parceiro) {
      estado.ultimoParceiro = visao.parceiro.nome;
    }
    if (!selecaoContinua(visao, estado.selecionada)) {
      estado.selecionada = null;
    }
    if (!sugestaoContinua(visao, estado.sugerindo)) {
      estado.sugerindo = null;
    }
    if (anterior) {
      destacar(pecasQueMudaram(anterior, visao));
    }
    if (!anterior && estado.convite && !emTroca()) {
      // O convite chegou antes da sala (na carga): só agora dá para saber se é o próprio.
      const token = estado.convite;
      if (convitesProprios.has(token)) {
        decidirConvite(token, null);
        return;
      }
      void decidirConviteDaCarga(token);
    }
    // Na troca, a sala atual está fora da tela: as novidades dela não disputam o aviso da entrada.
    const texto = anterior && !emTroca() ? novidade(cenario, anterior, visao, propria) : null;
    if (texto) {
      aviso.avisar(texto);
    } else if (anterior && sinalDaMontagem(visao) === null && ultimoAviso === avisoDoSinal(anterior)) {
      // A sugestão expirou (ou foi atendida em silêncio): o aviso não pode seguir citando-a.
      aviso.avisar("");
    }
    publicar();
  }

  /** O aviso que a sugestão em curso deu, para quem sugeriu ou para quem recebeu; null sem sugestão. */
  function avisoDoSinal(visao: Visao): string | null {
    const sinal = sinalDaMontagem(visao);
    if (sinal === null) {
      return null;
    }
    return sinal.deAssento === visao.voce.assento
      ? textoSinalEnviado(cenario, visao, sinal.peca, sinal.lugar)
      : textoSinalRecebido(cenario, visao, sinal);
  }

  /** O convite da carga que a aba não conhece: decide quando o resumo chega, se nada mudou. */
  async function decidirConviteDaCarga(token: string): Promise<void> {
    const resumo = await resumirConvite(token);
    if (estado.convite !== token || estado.visao === null || emTroca() || estado.enviando === "entrada") {
      return;
    }
    decidirConvite(token, resumo);
  }

  /** receberConvite com o resumo já em mãos (ou sem precisar dele). */
  function receberConviteResumido(token: string, resumo: string | null): void {
    // A entrada em voo já levou o convite dela: outro no meio piscaria a tela sem mudar o destino.
    if (estado.enviando === "entrada") {
      return;
    }
    if (estado.visao === null) {
      estado.convite = token;
      // Carregando, a decisão fica para a primeira resposta da sala.
      if (estado.semVisao.tela !== "carregando") {
        aviso.avisar("");
        mostrarEntrada(true);
      }
      return;
    }
    decidirConvite(token, resumo);
  }

  // ------------------------------------------------------------ ações

  /** Não espera o servidor: seleção e sugestão são só da tela até a escolha do lugar. */
  function escolherPeca(peca: string, minha: boolean): Promise<void> {
    const visao = estado.visao;
    if (visao?.fase === "MONTANDO" && !estado.enviando) {
      if (minha) {
        alternarSelecao(peca);
      } else {
        alternarSugestao(visao, peca);
      }
    }
    return Promise.resolve();
  }

  function alternarSelecao(peca: string): void {
    estado.sugerindo = null;
    estado.selecionada = estado.selecionada === peca ? null : peca;
    if (estado.selecionada) {
      pedirFoco({ alvo: "primeiro-lugar" });
    }
    publicar();
  }

  /** A mesma peça desliga a sugestão; sem destino sugerível, o toque não faz nada. */
  function alternarSugestao(visao: Visao, peca: string): void {
    if (estado.sugerindo === peca) {
      estado.sugerindo = null;
    } else if (sugestaoContinua(visao, peca)) {
      estado.selecionada = null;
      estado.sugerindo = peca;
      pedirFoco({ alvo: "primeiro-lugar" });
    } else {
      return;
    }
    publicar();
  }

  async function sugerir(peca: string, lugar: string): Promise<void> {
    await enviar(
      "sinal",
      () => api.sinalizar(peca, lugar),
      (nova) => {
        estado.sugerindo = null;
        pedirFoco({ alvo: "peca-na-cena", peca });
        return textoSinalEnviado(cenario, nova, peca, lugar);
      },
      peca,
    );
  }

  async function escolherLugar(lugar: string, bloqueio: string | null): Promise<void> {
    if (estado.enviando) {
      return;
    }
    if (estado.sugerindo) {
      await sugerir(estado.sugerindo, lugar);
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
      tratarErroDeAcao(erro, acao, peca);
    }
  }

  function tratarErroDeAcao(erro: unknown, acao: Acao, peca: string | null): void {
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
    const recusa = acao === "sinal" ? RECUSA_DO_SINAL : RECUSA_DA_PECA;
    const visaoAntes = estado.visao;
    if (erro.visao) {
      polling.aceitar(null);
      if (erro.status === 409 && peca) {
        recusa.focar(erro.visao, peca);
      }
      aplicarVisao(erro.visao, true);
    } else {
      publicar();
      if (erro.status === 409) {
        polling.consultarAgora();
      }
    }
    aviso.avisar(recusa.texto(erro, estado.visao ?? visaoAntes), "erro");
  }

  const RECUSA_DO_SINAL: ReacaoARecusa = { focar: focarAposRecusaDoSinal, texto: textoRecusaDoSinal };
  const RECUSA_DA_PECA: ReacaoARecusa = { focar: focarAposRecusa, texto: mensagemDeErro };

  /** Com destinos de sobra, a sugestão continua e o foco vai ao primeiro; senão, volta à peça. */
  function focarAposRecusaDoSinal(visao: Visao, peca: string): void {
    pedirFoco(sugestaoContinua(visao, estado.sugerindo) ? { alvo: "primeiro-lugar" } : { alvo: "peca-na-cena", peca });
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
      if (emTroca()) {
        await sairParaTrocar();
      }
      const resposta = conviteEmMaos ? await api.entrar(conviteEmMaos, apelido) : await api.criarSala(apelido);
      if (conviteEmMaos) {
        convitesProprios.add(conviteEmMaos);
      }
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

  /**
   * Primeiro passo da troca: sai da sala atual (a que já acabou também serve) e a solta, ficando na
   * entrada com o convite. Outra recusa devolve o polling e segue como erro, ainda na troca.
   */
  async function sairParaTrocar(): Promise<void> {
    polling.parar();
    try {
      await api.sair();
    } catch (erro) {
      if (!semSala(erro)) {
        polling.iniciar();
        throw erro;
      }
    }
    limparSessao();
    estado.semVisao = { tela: "entrada", comConvite: true, trocaDeSala: false, erroApelido: "" };
    estado.enviando = "entrada";
    publicar();
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
    estado.sugerindo = null;
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
    // Sem parceiro, não houve encontro para encerrar: só a espera acabou.
    const motivo = estado.visao.parceiro === null ? "SEM_ENCONTRO" : "VOCE_SAIU";
    limparSessao();
    aviso.avisar("");
    mostrarEncerrado(textoEncerrado(motivo));
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
    estado.sugerindo = null;
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
    const emTrocaAntes = emTroca();
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
      aviso.avisar(estavaNaSala ? avisoDaSalaPerdida(erro, emTrocaAntes) : "");
      return;
    }
    if (erro.codigo === "SALA_ENCERRADA" || erro.codigo === "SALA_INEXISTENTE") {
      const motivo = erro.motivo ?? "SALA_INEXISTENTE";
      // A aba esteve na sala e nunca viu um parceiro: o terraço acabou na espera (outra aba saiu).
      const semEncontro = motivo === "SAIU" && estavaNaSala && parceiro === null;
      aviso.avisar("");
      mostrarEncerrado(textoEncerrado(semEncontro ? "SEM_ENCONTRO" : motivo, parceiro));
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
    moverPara: (peca, lugar) => moverPeca(peca, lugar, "pecas"),
    retirar: () => moverPeca(estado.selecionada, null, "retirar"),
    confirmar: () =>
      enviar(
        "pronto",
        (visao) => api.marcarPronto(visao.versao, !visao.prontos.voce),
        (visao) => {
          if (visao.fase === "REVELADO") {
            return TEXTO_NOITE_REVELADA;
          }
          return visao.prontos.voce ? null : "Confirmação desfeita. Ajuste à vontade.";
        },
      ),
    pedirAjuste: () =>
      enviar(
        "ajustar",
        () => api.pedirAjuste(),
        // Com o pedido do outro pendente, pedir é aceitar (R4): a resposta já vem na montagem.
        (visao) =>
          visao.fase === "MONTANDO"
            ? "De volta à montagem. As confirmações foram zeradas."
            : `Pedido enviado. ${textoDoPedido(visao)}`,
      ),
    // A mesma rota recusa o pedido do outro ou cancela o próprio; o texto diz qual dos dois foi.
    recusarAjuste() {
      const cancelando = estado.visao !== null && situacaoDoAjuste(estado.visao) === "meu";
      return enviar(
        "recusar-ajuste",
        () => api.recusarAjuste(),
        () => (cancelando ? "Pedido cancelado." : "Tudo bem, a noite fica como está."),
      );
    },
    // O foco fica na lanterna (a cena o devolve no redesenho) ou, se ela sumiu, vai às peças.
    tocarAbertura: () =>
      enviar(
        "abertura",
        () => api.tocarAbertura(),
        () => null,
      ),
    enviarApelido: entrarOuCriar,
    async receberConvite(token) {
      // Sem sala, ou com um convite que a aba já viu, não precisa do resumo.
      if (estado.visao === null || convitesProprios.has(token)) {
        receberConviteResumido(token, null);
        return;
      }
      receberConviteResumido(token, await resumirConvite(token));
    },
    continuarNoTerraco() {
      if (estado.enviando === "entrada") {
        return;
      }
      desfazerTroca();
      publicar();
    },
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
      const sugerida = estado.sugerindo;
      if (sugerida) {
        estado.sugerindo = null;
        pedirFoco({ alvo: "peca-na-cena", peca: sugerida });
        publicar();
        return;
      }
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
