// A posição do aparelho, uma vez, já arredondada. Nenhum caminho daqui loga a coordenada nem o
// erro do navegador: quem chama recebe só o resultado e o manda adiante na mesma função.

import type { Coordenada } from "../api/perfil";

export type FalhaDePosicao = "negada" | "semResposta" | "indisponivel";

export type ResultadoDePosicao = { ok: true; coordenada: Coordenada } | { ok: false; falha: FalhaDePosicao };

/**
 * Quanto esperar por qualquer resposta do navegador. O `timeout` das opções só começa a contar
 * depois da permissão: com o pedido parado na barra do navegador, nenhum callback chega.
 */
export const VIGIA_DA_PERMISSAO_MS = 20_000;

/** Posição aproximada basta (o servidor guarda uma área de cerca de 5 km), e uma recente serve. */
const OPCOES: PositionOptions = { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 };

/** O código de GeolocationPositionError.PERMISSION_DENIED. */
const PERMISSAO_NEGADA = 1;

export interface Relogio {
  setTimeout: typeof setTimeout;
  clearTimeout: typeof clearTimeout;
}

/** Duas casas decimais (cerca de 1 km): menos precisão sai do aparelho do que o servidor usa. */
export function arredondar(c: Coordenada): Coordenada {
  return { latitude: Math.round(c.latitude * 100) / 100, longitude: Math.round(c.longitude * 100) / 100 };
}

/**
 * Os timers globais chamados sem trocar o `this`: no navegador, `relogio.setTimeout(...)` com o
 * próprio setTimeout lança "Illegal invocation".
 */
const RELOGIO_GLOBAL: Relogio = {
  setTimeout: ((acao: () => void, ms: number) => setTimeout(acao, ms)) as typeof setTimeout,
  clearTimeout: (id) => {
    clearTimeout(id);
  },
};

function geolocalizacaoDoNavegador(): Geolocation | undefined {
  return typeof navigator !== "undefined" && "geolocation" in navigator ? navigator.geolocation : undefined;
}

/**
 * Pede a posição uma vez. Sem Geolocation, TIMEOUT e POSITION_UNAVAILABLE viram `indisponivel`;
 * PERMISSION_DENIED vira `negada`; nenhum callback em VIGIA_DA_PERMISSAO_MS vira `semResposta`.
 * O que chegar depois do primeiro resultado é ignorado.
 */
export function obterPosicao(
  geo: Geolocation | undefined = geolocalizacaoDoNavegador(),
  relogio: Relogio = RELOGIO_GLOBAL,
): Promise<ResultadoDePosicao> {
  return new Promise((resolver) => {
    if (geo === undefined) {
      resolver({ ok: false, falha: "indisponivel" });
      return;
    }
    let terminou = false;
    const terminar = (resultado: ResultadoDePosicao) => {
      if (terminou) {
        return;
      }
      terminou = true;
      relogio.clearTimeout(vigia);
      resolver(resultado);
    };
    const vigia = relogio.setTimeout(() => {
      terminar({ ok: false, falha: "semResposta" });
    }, VIGIA_DA_PERMISSAO_MS);
    try {
      geo.getCurrentPosition(
        (posicao) => {
          const { latitude, longitude } = posicao.coords;
          terminar({ ok: true, coordenada: arredondar({ latitude, longitude }) });
        },
        (erro) => {
          terminar({ ok: false, falha: erro.code === PERMISSAO_NEGADA ? "negada" : "indisponivel" });
        },
        OPCOES,
      );
    } catch {
      // Navegadores antigos lançam em contexto inseguro em vez de chamar o callback de erro.
      terminar({ ok: false, falha: "indisponivel" });
    }
  });
}
