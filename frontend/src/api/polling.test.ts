import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Visao } from "../dominio/visao";
import { type Api, ErroApi, type Resposta } from "./cliente";
import { criarPolling } from "./polling";

const VISAO_VELHA = { versao: 1 } as Visao;

function apiFalsa(obterSala: Api["obterSala"]): Api {
  const naoUsado = () => Promise.reject(new Error("não usado neste teste"));
  return {
    criarSala: naoUsado,
    entrar: naoUsado,
    obterSala: vi.fn(obterSala),
    moverPeca: naoUsado,
    sinalizar: naoUsado,
    marcarPronto: naoUsado,
    ajustar: naoUsado,
    sair: naoUsado,
  };
}

function ganchos() {
  return { aoReceber: vi.fn(), aoFalhar: vi.fn(), aoRecuperar: vi.fn() };
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("criarPolling", () => {
  it("umaConsultaPorVez: com a resposta segurada, só uma chamada em 10 s", async () => {
    const api = apiFalsa(() => new Promise<Resposta>(() => undefined));
    const polling = criarPolling(api, ganchos());

    polling.iniciar();
    await vi.advanceTimersByTimeAsync(10_000);

    expect(api.obterSala).toHaveBeenCalledTimes(1);
  });

  it("recuaEmErroTransitorio: três 500 seguidos esperam 2000, 4000 e 8000 ms", async () => {
    const api = apiFalsa(() => Promise.reject(new ErroApi({ status: 500, codigo: "SERVIDOR" })));
    const g = ganchos();
    const polling = criarPolling(api, g);

    polling.iniciar();
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(2000);
    await vi.advanceTimersByTimeAsync(4000);

    expect(api.obterSala).toHaveBeenCalledTimes(3);
    expect(g.aoFalhar.mock.calls.map(([, espera]: unknown[]) => espera)).toEqual([2000, 4000, 8000]);
  });

  it("paraEmErroDefinitivo: 401 chama aoFalhar(erro, null) e não consulta de novo", async () => {
    const erro = new ErroApi({ status: 401, codigo: "SEM_ASSENTO" });
    const api = apiFalsa(() => Promise.reject(erro));
    const g = ganchos();
    const polling = criarPolling(api, g);

    polling.iniciar();
    await vi.advanceTimersByTimeAsync(30_000);

    expect(g.aoFalhar).toHaveBeenCalledExactlyOnceWith(erro, null);
    expect(api.obterSala).toHaveBeenCalledTimes(1);
  });

  it("aceitarDescartaRespostaEmVoo: a resposta da consulta anterior ao aceitar é ignorada", async () => {
    let responder: (resposta: Resposta) => void = () => undefined;
    const api = apiFalsa(
      () =>
        new Promise<Resposta>((resolver) => {
          responder = resolver;
        }),
    );
    const g = ganchos();
    const polling = criarPolling(api, g);

    polling.iniciar();
    await vi.advanceTimersByTimeAsync(0);
    polling.aceitar("x");
    responder({ naoMudou: false, visao: VISAO_VELHA, etag: '"v1"' });
    await vi.advanceTimersByTimeAsync(0);

    expect(g.aoReceber).not.toHaveBeenCalled();
  });

  it("depois de uma falha transitória, a próxima resposta avisa a recuperação", async () => {
    const respostas = [
      () => Promise.reject(new ErroApi({ status: 0, codigo: "REDE" })),
      () => Promise.resolve<Resposta>({ naoMudou: true, visao: null, etag: '"v1"' }),
    ];
    const api = apiFalsa(() => respostas.shift()?.() ?? new Promise<Resposta>(() => undefined));
    const g = ganchos();
    const polling = criarPolling(api, g);

    polling.iniciar();
    await vi.advanceTimersByTimeAsync(2000);

    expect(g.aoRecuperar).toHaveBeenCalledOnce();
    expect(g.aoReceber).not.toHaveBeenCalled();
  });

  it("usa o ETag aceito na consulta seguinte", async () => {
    const api = apiFalsa(() => Promise.resolve<Resposta>({ naoMudou: true, visao: null, etag: null }));
    const polling = criarPolling(api, ganchos());

    polling.aceitar('"v9"');
    polling.iniciar();
    await vi.advanceTimersByTimeAsync(0);

    expect(api.obterSala).toHaveBeenCalledWith('"v9"');
  });
});
