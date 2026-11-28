import { useRef } from "react";

export interface TravaDeEnvio {
  fechada: () => boolean;
  fechar: () => void;
  abrir: () => void;
}

/**
 * Trava síncrona do envio: o isPending da mutação só chega no render seguinte, e um duplo toque
 * nessa janela mandaria o pedido duas vezes. Quem envia fecha; o onSettled da mutação abre.
 */
export function useTravaDeEnvio(): TravaDeEnvio {
  const fechada = useRef(false);
  return {
    fechada: () => fechada.current,
    fechar: () => {
      fechada.current = true;
    },
    abrir: () => {
      fechada.current = false;
    },
  };
}
