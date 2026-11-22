// Laço único de GET /api/sala. Fica num chunk próprio (app/polling-*.js): a sonda do PollingIT
// reconhece este módulo pela pilha do setTimeout, então o agendamento precisa morar aqui.

import type { Visao } from "../dominio/visao";
import { type Api, ErroApi } from "./cliente";

const ESPERA_NORMAL = 2000;
const ESPERA_OCULTA = 10000;
const ESPERAS_ERRO = [2000, 4000, 8000, 15000];

export interface GanchosDoPolling {
  aoReceber: (visao: Visao) => void;
  /** esperaMs: a próxima tentativa (erro transitório) ou null (erro definitivo; o laço parou). */
  aoFalhar: (erro: unknown, esperaMs: number | null) => void;
  aoRecuperar: () => void;
}

export interface Polling {
  iniciar: () => void;
  parar: () => void;
  consultarAgora: () => void;
  /** Uma mutação trouxe visão nova: descarta consultas em voo e passa a usar o ETag dela. */
  aceitar: (etag: string | null) => void;
}

/**
 * Consultas encadeadas com setTimeout: a próxima só é agendada quando a anterior termina.
 * Erros transitórios recuam 2→4→8→15 s; os definitivos param o laço.
 */
export function criarPolling(api: Api, { aoReceber, aoFalhar, aoRecuperar }: GanchosDoPolling): Polling {
  let ativo = false;
  let emVoo = false;
  let repetirJa = false;
  let temporizador: ReturnType<typeof setTimeout> | undefined;
  let etag: string | null = null;
  let falhas = 0;
  // Muda a cada parada ou mutação aceita: respostas de consultas mais antigas são descartadas.
  let epoca = 0;

  /** Uma resposta só vale se o laço segue ativo e nada a tornou velha desde que a consulta saiu. */
  const vale = (epocaDaConsulta: number): boolean => ativo && epocaDaConsulta === epoca;

  function espera(): number {
    const base =
      falhas > 0 ? (ESPERAS_ERRO[Math.min(falhas, ESPERAS_ERRO.length) - 1] ?? ESPERA_NORMAL) : ESPERA_NORMAL;
    return document.hidden ? Math.max(base, ESPERA_OCULTA) : base;
  }

  function agendar(ms: number): void {
    clearTimeout(temporizador);
    temporizador = setTimeout(() => void consultar(), ms);
  }

  async function consultar(): Promise<void> {
    temporizador = undefined;
    if (!ativo || emVoo) {
      return;
    }
    emVoo = true;
    const minhaEpoca = epoca;
    try {
      const resposta = await api.obterSala(etag);
      if (vale(minhaEpoca)) {
        if (falhas > 0) {
          falhas = 0;
          aoRecuperar();
        }
        if (!resposta.naoMudou && resposta.visao !== null) {
          etag = resposta.etag;
          aoReceber(resposta.visao);
        }
      }
    } catch (erro) {
      if (vale(minhaEpoca)) {
        if (erro instanceof ErroApi && erro.transitorio) {
          falhas += 1;
          aoFalhar(erro, espera());
        } else {
          ativo = false;
          document.removeEventListener("visibilitychange", aoVoltarAAba);
          aoFalhar(erro, null);
        }
      }
    } finally {
      emVoo = false;
      if (ativo) {
        agendar(repetirJa ? 0 : espera());
      }
      repetirJa = false;
    }
  }

  function consultarAgora(): void {
    if (!ativo) {
      return;
    }
    if (emVoo) {
      repetirJa = true;
    } else {
      agendar(0);
    }
  }

  // Ligado só com o laço ativo: parar() o desliga, e a desmontagem não deixa ouvinte para trás.
  function aoVoltarAAba(): void {
    if (!document.hidden) {
      consultarAgora();
    }
  }

  return {
    iniciar() {
      if (ativo) {
        return;
      }
      ativo = true;
      falhas = 0;
      document.addEventListener("visibilitychange", aoVoltarAAba);
      consultarAgora();
    },
    parar() {
      document.removeEventListener("visibilitychange", aoVoltarAAba);
      ativo = false;
      epoca += 1;
      etag = null;
      clearTimeout(temporizador);
      temporizador = undefined;
    },
    consultarAgora,
    aceitar(novoEtag) {
      epoca += 1;
      etag = novoEtag;
    },
  };
}
