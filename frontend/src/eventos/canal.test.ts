import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { criarCanal, type Canal, type DependenciasDoCanal, type FonteDeEventos } from "./canal";

const CONECTANDO = 0;
const ABERTA = 1;
const FECHADA = 2;

type Ouvinte = (evento: MessageEvent<string>) => void;

/** Um EventSource que o teste abre, derruba e faz emitir à mão. */
class FonteFalsa implements FonteDeEventos {
  readyState = CONECTANDO;
  fechada = false;
  private readonly ouvintes = new Map<string, Set<Ouvinte>>();

  readonly url: string;

  constructor(url: string) {
    this.url = url;
  }

  addEventListener(tipo: string, ouvinte: Ouvinte): void {
    const doTipo = this.ouvintes.get(tipo) ?? new Set<Ouvinte>();
    doTipo.add(ouvinte);
    this.ouvintes.set(tipo, doTipo);
  }

  removeEventListener(tipo: string, ouvinte: Ouvinte): void {
    this.ouvintes.get(tipo)?.delete(ouvinte);
  }

  close(): void {
    this.readyState = FECHADA;
    this.fechada = true;
  }

  ouve(tipo: string): boolean {
    return (this.ouvintes.get(tipo)?.size ?? 0) > 0;
  }

  abrir(): void {
    this.readyState = ABERTA;
    this.disparar("open", new MessageEvent("open"));
  }

  falhar(estado: number): void {
    this.readyState = estado;
    this.disparar("error", new MessageEvent("error"));
  }

  emitir(nome: string, dados: string, id = ""): void {
    this.disparar(nome, new MessageEvent(nome, { data: dados, lastEventId: id }));
  }

  private disparar(tipo: string, evento: MessageEvent<string>): void {
    for (const ouvinte of this.ouvintes.get(tipo) ?? []) {
      ouvinte(evento);
    }
  }
}

let fontes: FonteFalsa[];
let documento: EventTarget & { visibilityState: DocumentVisibilityState };
let janela: EventTarget;

function canalDeTeste(extra: Partial<DependenciasDoCanal> = {}): Canal {
  return criarCanal({
    abrir: (url) => {
      const fonte = new FonteFalsa(url);
      fontes.push(fonte);
      return fonte;
    },
    aleatorio: () => 0.999,
    relogio: { setTimeout, clearTimeout },
    documento,
    janela,
    ...extra,
  });
}

function ultima(): FonteFalsa {
  const fonte = fontes.at(-1);
  if (fonte === undefined) {
    throw new Error("nenhuma fonte aberta");
  }
  return fonte;
}

function ficarVisivel(): void {
  documento.visibilityState = "visible";
  documento.dispatchEvent(new Event("visibilitychange"));
}

beforeEach(() => {
  vi.useFakeTimers();
  fontes = [];
  documento = Object.assign<EventTarget, { visibilityState: DocumentVisibilityState }>(new EventTarget(), {
    visibilityState: "visible",
  });
  janela = new EventTarget();
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("canal", () => {
  it("doisAssinantesDoMesmoNomeRecebem", () => {
    const canal = canalDeTeste();
    const a = vi.fn();
    const b = vi.fn();
    canal.assinar("fila.estado", a);
    canal.assinar("fila.estado", b);

    ultima().emitir("fila.estado", '{"situacao":"AGUARDANDO_COMPATIVEL"}');

    expect(fontes).toHaveLength(1);
    expect(fontes[0]?.url).toBe("/api/eventos");
    expect(a).toHaveBeenCalledWith({ situacao: "AGUARDANDO_COMPATIVEL" });
    expect(b).toHaveBeenCalledWith({ situacao: "AGUARDANDO_COMPATIVEL" });
  });

  it("cancelarOUltimoFecha", () => {
    const canal = canalDeTeste();
    const cancelarA = canal.assinar("fila.estado", vi.fn());
    const cancelarB = canal.assinar("fila.pareado", vi.fn());

    cancelarA();
    expect(ultima().fechada).toBe(false);
    cancelarB();

    expect(ultima().fechada).toBe(true);
  });

  it("nomeNovoNaoReabre", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    ultima().abrir();
    const pareado = vi.fn();

    canal.assinar("fila.pareado", pareado);
    ultima().emitir("fila.pareado", '{"encontroId":"e1","cenario":"terraco"}');

    expect(fontes).toHaveLength(1);
    expect(pareado).toHaveBeenCalledWith({ encontroId: "e1", cenario: "terraco" });
  });

  it("primeiraEsperaFicaEntre0e1000", () => {
    const sorteios: number[] = [];
    const canal = canalDeTeste({
      aleatorio: () => {
        sorteios.push(0.5);
        return 0.5;
      },
    });
    canal.assinar("fila.estado", vi.fn());

    ultima().falhar(FECHADA);

    vi.advanceTimersByTime(499);
    expect(fontes).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(fontes).toHaveLength(2);
    expect(sorteios).toHaveLength(1);
  });

  it("esperaNuncaPassaDe30000", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    // Tentativas 0 a 9: esperas de 999, 1998, ..., 29_970 (a 5 já passa do teto).
    for (let tentativa = 0; tentativa < 10; tentativa++) {
      ultima().falhar(FECHADA);
      vi.advanceTimersByTime(30_000);
    }
    const antes = fontes.length;

    ultima().falhar(FECHADA);

    vi.advanceTimersByTime(29_969);
    expect(fontes).toHaveLength(antes);
    vi.advanceTimersByTime(1);
    expect(fontes).toHaveLength(antes + 1);
  });

  it("reaberturaManualMandaUltimoId", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    ultima().abrir();
    ultima().emitir("fila.estado", "{}", "42");

    ultima().falhar(FECHADA);
    vi.advanceTimersByTime(999);

    expect(ultima().url).toBe("/api/eventos?ultimoId=42");
  });

  it("erroConectandoNaoReabreAMao", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    ultima().abrir();

    ultima().falhar(CONECTANDO);
    vi.advanceTimersByTime(30_000);

    expect(fontes).toHaveLength(1);
    expect(ultima().fechada).toBe(false);
    expect(canal.estado()).toBe("reconectando");
  });

  it("tentativasZeramSoDepoisDe10s", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    ultima().falhar(FECHADA);
    vi.advanceTimersByTime(999);
    // Aberta por 9_999 ms: a próxima falha ainda é a tentativa 1 (1998 ms).
    ultima().abrir();
    vi.advanceTimersByTime(9_999);
    ultima().falhar(FECHADA);
    vi.advanceTimersByTime(1_997);
    expect(fontes).toHaveLength(2);
    vi.advanceTimersByTime(1);
    expect(fontes).toHaveLength(3);
    // Aberta por 10_000 ms: volta à tentativa 0 (999 ms).
    ultima().abrir();
    vi.advanceTimersByTime(10_000);

    ultima().falhar(FECHADA);

    vi.advanceTimersByTime(998);
    expect(fontes).toHaveLength(3);
    vi.advanceTimersByTime(1);
    expect(fontes).toHaveLength(4);
  });

  it("vigiaDe60sReabre", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    ultima().abrir();

    vi.advanceTimersByTime(59_999);
    expect(fontes).toHaveLength(1);
    vi.advanceTimersByTime(1);

    expect(fontes).toHaveLength(2);
    expect(fontes[0]?.fechada).toBe(true);
  });

  it("batimentoAdiaAVigia", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    ultima().abrir();
    vi.advanceTimersByTime(50_000);

    ultima().emitir("stream.batimento", "{}");
    vi.advanceTimersByTime(59_999);
    expect(fontes).toHaveLength(1);
    vi.advanceTimersByTime(1);

    expect(fontes).toHaveLength(2);
  });

  it("substituidoNaoReabreAteVoltarAVisibilidade", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    ultima().abrir();
    documento.visibilityState = "hidden";

    ultima().emitir("stream.substituido", "{}");
    vi.advanceTimersByTime(120_000);
    expect(canal.estado()).toBe("substituido");
    expect(ultima().fechada).toBe(true);
    expect(fontes).toHaveLength(1);

    ficarVisivel();

    expect(fontes).toHaveLength(2);
  });

  it("retomarReabre", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    ultima().abrir();
    ultima().emitir("fila.estado", "{}", "7");
    ultima().emitir("stream.substituido", "{}");

    canal.retomar();

    expect(fontes).toHaveLength(2);
    expect(ultima().url).toBe("/api/eventos?ultimoId=7");
  });

  it("onlineReabreNaHora", () => {
    const canal = canalDeTeste();
    canal.assinar("fila.estado", vi.fn());
    ultima().falhar(FECHADA);

    janela.dispatchEvent(new Event("online"));

    expect(fontes).toHaveLength(2);
    vi.advanceTimersByTime(30_000);
    expect(fontes).toHaveLength(2);
  });

  it("aoReconectarNaoDisparaNaPrimeiraAbertura", () => {
    const canal = canalDeTeste();
    const aoReconectar = vi.fn();
    canal.aoReconectar(aoReconectar);
    canal.assinar("fila.estado", vi.fn());

    ultima().abrir();

    expect(aoReconectar).not.toHaveBeenCalled();
    expect(canal.estado()).toBe("conectado");
  });

  it("aoReconectarDisparaNaReabertura", () => {
    const canal = canalDeTeste();
    const aoReconectar = vi.fn();
    canal.aoReconectar(aoReconectar);
    canal.assinar("fila.estado", vi.fn());
    ultima().abrir();
    // A reconexão do próprio navegador também conta: o open chega na mesma fonte.
    ultima().falhar(CONECTANDO);

    ultima().abrir();

    expect(aoReconectar).toHaveBeenCalledTimes(1);
  });

  it("jsonInvalidoNaoChamaOAssinante", () => {
    const aviso = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const canal = canalDeTeste();
    const assinante = vi.fn();
    canal.assinar("fila.estado", assinante);

    ultima().emitir("fila.estado", "{não é json");

    expect(assinante).not.toHaveBeenCalled();
    expect(aviso).toHaveBeenCalledTimes(1);
    expect(ultima().ouve("fila.estado")).toBe(true);
  });
});
