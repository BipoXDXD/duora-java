// Os hooks do canal da aba. O callback mais recente é sempre o chamado, sem reassinar a cada render.

import { useCallback, useEffect, useEffectEvent, useSyncExternalStore } from "react";
import { canal, type Canal, type EstadoDaConexao } from "./canal";

/**
 * Assina o evento `nome` enquanto o componente estiver montado. O T é o formato que quem assina
 * espera: o canal não valida (o valibot fica na casca, com quem assina).
 */
// eslint-disable-next-line @typescript-eslint/no-unnecessary-type-parameters -- T é a promessa de quem assina (acima)
export function useEvento<T>(nome: string, fn: (dados: T) => void, alvo: Canal = canal): void {
  const aoEvento = useEffectEvent(fn);
  useEffect(
    () =>
      alvo.assinar<T>(nome, (dados) => {
        aoEvento(dados);
      }),
    [nome, alvo],
  );
}

export function useEstadoDaConexao(alvo: Canal = canal): EstadoDaConexao {
  const inscrever = useCallback((avisar: () => void) => alvo.aoMudarEstado(avisar), [alvo]);
  return useSyncExternalStore(inscrever, () => alvo.estado());
}

/** Chamado a cada reconexão do canal (não na primeira abertura): hora de reler o que pode ter mudado. */
export function useAoReconectar(fn: () => void, alvo: Canal = canal): void {
  const aoReconectar = useEffectEvent(fn);
  useEffect(
    () =>
      alvo.aoReconectar(() => {
        aoReconectar();
      }),
    [alvo],
  );
}
