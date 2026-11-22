import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import { arquivosDoSom, criarSom } from "./som";

describe("arquivosDoSom", () => {
  it("volumes e URLs vêm do cenário", () => {
    expect(arquivosDoSom(cenarioDoTerraco())).toEqual({
      ambiente: { url: "/assets/cenarios/terraco/audio/ambiente.mp3", volume: 0.35 },
      meteoro: { url: "/assets/cenarios/terraco/audio/meteoro.mp3", volume: 0.5 },
      revelacao: { url: "/assets/cenarios/terraco/audio/revelacao.mp3", volume: 0.6 },
    });
  });
});

/** Contexto falso do Web Audio: conta os start(), resume() e suspend() e guarda o fim de cada fonte. */
class ContextoFalso {
  static ultimo: ContextoFalso | null = null;
  readonly currentTime = 0;
  readonly destination = {};
  starts = 0;
  resumes = 0;
  suspends = 0;
  /** Um por fonte iniciada: chamar faz a fonte terminar de tocar. */
  readonly terminar: (() => void)[] = [];

  constructor() {
    ContextoFalso.ultimo = this;
  }

  resume(): Promise<void> {
    this.resumes += 1;
    return Promise.resolve();
  }

  suspend(): Promise<void> {
    this.suspends += 1;
    return Promise.resolve();
  }

  close(): Promise<void> {
    return Promise.resolve();
  }

  decodeAudioData(): Promise<object> {
    return Promise.resolve({});
  }

  createGain() {
    return {
      gain: { value: 1, setValueAtTime: vi.fn(), linearRampToValueAtTime: vi.fn() },
      connect: vi.fn(),
      disconnect: vi.fn(),
    };
  }

  createBufferSource() {
    return {
      buffer: null,
      loop: false,
      connect: vi.fn(),
      disconnect: vi.fn(),
      addEventListener: (tipo: string, ouvinte: () => void) => {
        if (tipo === "ended") {
          this.terminar.push(ouvinte);
        }
      },
      start: () => {
        this.starts += 1;
      },
      stop: vi.fn(),
    };
  }
}

const NOTAS = "/assets/cenarios/terraco/audio/radio-notas.mp3";

describe("criarSom: notas e desbloqueio", () => {
  const fetchFalso = vi.fn<typeof fetch>();

  beforeEach(() => {
    ContextoFalso.ultimo = null;
    fetchFalso.mockReset();
    vi.stubGlobal("AudioContext", ContextoFalso);
    vi.stubGlobal("fetch", fetchFalso);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("as notas tocam com o som desligado", async () => {
    fetchFalso.mockResolvedValue(new Response(new ArrayBuffer(8)));
    const som = criarSom(cenarioDoTerraco());

    som.notas(NOTAS, 0.5);

    await vi.waitFor(() => {
      expect(ContextoFalso.ultimo?.starts).toBe(1);
    });
    expect(fetchFalso).toHaveBeenCalledWith(NOTAS);
  });

  it("as notas falham em silêncio", async () => {
    fetchFalso.mockRejectedValue(new TypeError("sem rede"));
    const som = criarSom(cenarioDoTerraco());

    expect(() => {
      som.notas(NOTAS, 0.5);
    }).not.toThrow();
    // A suspensão do contexto marca o fim da tentativa: só então dá para dizer que nada tocou.
    await vi.waitFor(() => {
      expect(ContextoFalso.ultimo?.suspends).toBe(1);
    });
    expect(ContextoFalso.ultimo?.starts).toBe(0);
  });

  it("com o som desligado, o contexto suspende quando as notas terminam", async () => {
    fetchFalso.mockResolvedValue(new Response(new ArrayBuffer(8)));
    const som = criarSom(cenarioDoTerraco());

    som.notas(NOTAS, 0.5);
    await vi.waitFor(() => {
      expect(ContextoFalso.ultimo?.starts).toBe(1);
    });
    expect(ContextoFalso.ultimo?.suspends).toBe(0);
    ContextoFalso.ultimo?.terminar.forEach((terminar) => {
      terminar();
    });

    expect(ContextoFalso.ultimo?.suspends).toBe(1);
  });

  it("com o som ligado, o fim das notas não suspende o contexto", async () => {
    fetchFalso.mockImplementation(() => Promise.resolve(new Response(new ArrayBuffer(8))));
    const som = criarSom(cenarioDoTerraco());
    som.definir(true);

    som.notas(NOTAS, 0.5);
    await vi.waitFor(() => {
      expect(fetchFalso).toHaveBeenCalledWith(NOTAS);
      expect(ContextoFalso.ultimo?.terminar.length).toBeGreaterThan(0);
    });
    ContextoFalso.ultimo?.terminar.forEach((terminar) => {
      terminar();
    });

    expect(ContextoFalso.ultimo?.suspends).toBe(0);
  });

  it("desbloquear não baixa nada", () => {
    const som = criarSom(cenarioDoTerraco());

    som.desbloquear();

    expect(ContextoFalso.ultimo?.resumes).toBe(1);
    expect(fetchFalso).not.toHaveBeenCalled();
  });
});
