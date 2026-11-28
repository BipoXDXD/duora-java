// O canal SSE único da aba (/api/eventos): um EventSource para todos os assinantes, aberto com o
// primeiro e fechado com o último. Entrega o JSON do evento sem validar: o formato é de quem
// assina, dentro da casca (o valibot não entra no JS inicial). Nenhum caminho daqui loga os dados.

import type { Relogio } from "../geo/obterPosicao";

export type EstadoDaConexao = "conectando" | "conectado" | "reconectando" | "substituido";

/** O pedaço do EventSource que o canal usa. */
export interface FonteDeEventos {
  readonly readyState: number;
  addEventListener(tipo: string, ouvinte: (evento: MessageEvent<string>) => void): void;
  removeEventListener(tipo: string, ouvinte: (evento: MessageEvent<string>) => void): void;
  close(): void;
}

type Documento = Pick<Document, "visibilityState" | "addEventListener" | "removeEventListener">;
type Janela = Pick<Window, "addEventListener" | "removeEventListener">;

export interface DependenciasDoCanal {
  abrir?: (url: string) => FonteDeEventos;
  aleatorio?: () => number;
  relogio?: Relogio;
  documento?: Documento;
  janela?: Janela;
}

export interface Canal {
  /**
   * Entrega os dados de cada evento `nome`; devolve o cancelamento. O primeiro assinante abre o
   * stream. O T é o formato que quem assina espera: o canal não valida (o valibot fica na casca).
   */
  // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-parameters -- T é a promessa de quem assina (acima)
  assinar<T>(nome: string, fn: (dados: T) => void): () => void;
  /** Chamado em toda abertura depois da primeira: o que chegou enquanto caído precisa ser relido. */
  aoReconectar(fn: () => void): () => void;
  aoMudarEstado(fn: () => void): () => void;
  estado(): EstadoDaConexao;
  /** Reabre já se o stream estiver fechado (ex.: depois de substituído por outra aba). */
  retomar(): void;
}

const URL_DOS_EVENTOS = "/api/eventos";
/** EventSource.CLOSED (o jsdom não tem EventSource). */
const FECHADA = 2;
const TETO_DA_ESPERA_MS = 30_000;
const ESPERA_BASE_MS = 1_000;
/** Aberto por esse tempo, o stream é estável e o backoff volta ao começo. */
const ESTAVEL_APOS_MS = 10_000;
/** O servidor manda stream.batimento a cada 25 s: sem nada em 60 s, o stream está morto. */
const VIGIA_MS = 60_000;
const BATIMENTO = "stream.batimento";
const SUBSTITUIDO = "stream.substituido";

/** Os timers globais sem trocar o `this` (no navegador, chamá-los como método lança "Illegal invocation"). */
const RELOGIO_GLOBAL: Relogio = {
  setTimeout: ((acao: () => void, ms: number) => setTimeout(acao, ms)) as typeof setTimeout,
  clearTimeout: (id) => {
    clearTimeout(id);
  },
};

type Assinante = { fn: (dados: unknown) => void };
type Temporizador = ReturnType<typeof setTimeout>;

export function criarCanal(deps: DependenciasDoCanal = {}): Canal {
  const abrirFonte = deps.abrir ?? ((url: string) => new EventSource(url));
  const aleatorio = deps.aleatorio ?? Math.random;
  const relogio = deps.relogio ?? RELOGIO_GLOBAL;
  const documento = deps.documento ?? (typeof document === "undefined" ? undefined : document);
  const janela = deps.janela ?? (typeof window === "undefined" ? undefined : window);

  const assinantes = new Map<string, Set<Assinante>>();
  const ouvintes = new Map<string, (evento: MessageEvent<string>) => void>();
  const aoReconectar = new Set<() => void>();
  const aoMudarEstado = new Set<() => void>();

  let atual: EstadoDaConexao = "conectando";
  let fonte: FonteDeEventos | null = null;
  let ultimoId: string | null = null;
  let tentativa = 0;
  let aberturas = 0;
  let reabertura: Temporizador | null = null;
  let estabilidade: Temporizador | null = null;
  let vigia: Temporizador | null = null;

  function mudar(estado: EstadoDaConexao): void {
    if (estado === atual) {
      return;
    }
    atual = estado;
    for (const fn of [...aoMudarEstado]) {
      fn();
    }
  }

  function parar(temporizador: Temporizador | null): null {
    if (temporizador !== null) {
      relogio.clearTimeout(temporizador);
    }
    return null;
  }

  function armarVigia(): void {
    vigia = parar(vigia);
    vigia = relogio.setTimeout(() => {
      vigia = null;
      mudar("reconectando");
      reabrir();
    }, VIGIA_MS);
  }

  function ouvinteDe(nome: string): (evento: MessageEvent<string>) => void {
    return (evento) => {
      armarVigia();
      if (evento.lastEventId !== "") {
        ultimoId = evento.lastEventId;
      }
      let dados: unknown;
      try {
        dados = JSON.parse(evento.data);
      } catch {
        console.warn(`Evento ${nome} com JSON inválido; ignorado.`);
        return;
      }
      for (const assinante of [...(assinantes.get(nome) ?? [])]) {
        assinante.fn(dados);
      }
    };
  }

  function aoAbrir(): void {
    aberturas++;
    mudar("conectado");
    armarVigia();
    estabilidade = parar(estabilidade);
    estabilidade = relogio.setTimeout(() => {
      estabilidade = null;
      tentativa = 0;
    }, ESTAVEL_APOS_MS);
    if (aberturas > 1) {
      for (const fn of [...aoReconectar]) {
        fn();
      }
    }
  }

  function aoFalhar(): void {
    estabilidade = parar(estabilidade);
    mudar("reconectando");
    // CONNECTING: o próprio navegador tenta de novo (com Last-Event-ID); só o CLOSED é nosso.
    if (fonte === null || fonte.readyState !== FECHADA) {
      return;
    }
    fecharFonte();
    const espera = Math.floor(aleatorio() * Math.min(TETO_DA_ESPERA_MS, ESPERA_BASE_MS * 2 ** tentativa));
    tentativa++;
    reabertura = relogio.setTimeout(() => {
      reabertura = null;
      abrir();
    }, espera);
  }

  function aoSerSubstituido(): void {
    fecharFonte();
    tentativa = 0;
    mudar("substituido");
  }

  function abrir(): void {
    const url = ultimoId === null ? URL_DOS_EVENTOS : `${URL_DOS_EVENTOS}?ultimoId=${encodeURIComponent(ultimoId)}`;
    const nova = abrirFonte(url);
    fonte = nova;
    nova.addEventListener("open", aoAbrir);
    nova.addEventListener("error", aoFalhar);
    nova.addEventListener(BATIMENTO, armarVigia);
    nova.addEventListener(SUBSTITUIDO, aoSerSubstituido);
    for (const [nome, ouvinte] of ouvintes) {
      nova.addEventListener(nome, ouvinte);
    }
    armarVigia();
  }

  function fecharFonte(): void {
    reabertura = parar(reabertura);
    estabilidade = parar(estabilidade);
    vigia = parar(vigia);
    if (fonte === null) {
      return;
    }
    fonte.removeEventListener("open", aoAbrir);
    fonte.removeEventListener("error", aoFalhar);
    fonte.removeEventListener(BATIMENTO, armarVigia);
    fonte.removeEventListener(SUBSTITUIDO, aoSerSubstituido);
    for (const [nome, ouvinte] of ouvintes) {
      fonte.removeEventListener(nome, ouvinte);
    }
    fonte.close();
    fonte = null;
  }

  function reabrir(): void {
    fecharFonte();
    abrir();
  }

  function fechado(): boolean {
    return fonte === null || fonte.readyState === FECHADA;
  }

  /** online, aba visível, foco e retomar(): com assinantes e o stream fechado, reabre sem esperar. */
  function reabrirSeFechado(): void {
    if (ouvintes.size === 0 || !fechado()) {
      return;
    }
    if (atual === "substituido") {
      mudar("reconectando");
    }
    reabrir();
  }

  function aoMudarVisibilidade(): void {
    if (documento?.visibilityState === "visible") {
      reabrirSeFechado();
    }
  }

  function comecar(): void {
    documento?.addEventListener("visibilitychange", aoMudarVisibilidade);
    janela?.addEventListener("online", reabrirSeFechado);
    janela?.addEventListener("focus", reabrirSeFechado);
    abrir();
  }

  function terminar(): void {
    documento?.removeEventListener("visibilitychange", aoMudarVisibilidade);
    janela?.removeEventListener("online", reabrirSeFechado);
    janela?.removeEventListener("focus", reabrirSeFechado);
    fecharFonte();
    tentativa = 0;
    mudar("conectando");
  }

  // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-parameters -- T é a promessa de quem assina (Canal.assinar)
  function assinar<T>(nome: string, fn: (dados: T) => void): () => void {
    // O formato é de quem assina: o canal entrega o JSON como chegou.
    const assinante: Assinante = { fn: fn as (dados: unknown) => void };
    const primeiroDoCanal = ouvintes.size === 0;
    let doNome = assinantes.get(nome);
    if (doNome === undefined) {
      doNome = new Set();
      assinantes.set(nome, doNome);
      const ouvinte = ouvinteDe(nome);
      ouvintes.set(nome, ouvinte);
      fonte?.addEventListener(nome, ouvinte);
    }
    doNome.add(assinante);
    if (primeiroDoCanal) {
      comecar();
    }
    let cancelado = false;
    return () => {
      if (cancelado) {
        return;
      }
      cancelado = true;
      doNome.delete(assinante);
      if (doNome.size > 0) {
        return;
      }
      assinantes.delete(nome);
      const ouvinte = ouvintes.get(nome);
      ouvintes.delete(nome);
      if (ouvinte !== undefined) {
        fonte?.removeEventListener(nome, ouvinte);
      }
      if (ouvintes.size === 0) {
        terminar();
      }
    };
  }

  function inscrever(conjunto: Set<() => void>, fn: () => void): () => void {
    const entrada = () => {
      fn();
    };
    conjunto.add(entrada);
    return () => {
      conjunto.delete(entrada);
    };
  }

  return {
    assinar,
    aoReconectar: (fn) => inscrever(aoReconectar, fn),
    aoMudarEstado: (fn) => inscrever(aoMudarEstado, fn),
    estado: () => atual,
    retomar: reabrirSeFechado,
  };
}

/** O canal da aba. */
export const canal: Canal = criarCanal();
