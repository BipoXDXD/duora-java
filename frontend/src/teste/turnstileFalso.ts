import { act } from "@testing-library/react";
import { vi } from "vitest";
import type { ApiTurnstile, OpcoesDoWidget } from "../componentes/Turnstile";

/**
 * Turnstile falso para as telas que pedem a verificação: `carregar` entrega a API (ou falha, com
 * `carregou` falso), e o teste resolve ou expira o desafio do widget desenhado ("widget-1").
 */
export function turnstileFalso(carregou = true) {
  let opcoes: OpcoesDoWidget | null = null;
  const desenhar = vi.fn((_elemento: HTMLElement, recebidas: OpcoesDoWidget) => {
    opcoes = recebidas;
    return "widget-1";
  });
  const reiniciar = vi.fn<(id: string) => void>();
  const api: ApiTurnstile = { render: desenhar, reset: reiniciar, remove: vi.fn() };
  const carregar = vi.fn(() => (carregou ? Promise.resolve(api) : Promise.reject(new Error("bloqueado"))));
  function atuais(): OpcoesDoWidget {
    if (!opcoes) {
      throw new Error("o widget não foi desenhado");
    }
    return opcoes;
  }
  return {
    carregar,
    desenhar,
    reiniciar,
    resolverDesafio(token: string) {
      act(() => {
        atuais().callback(token);
      });
    },
    expirar() {
      act(() => {
        atuais()["expired-callback"]();
      });
    },
  };
}
