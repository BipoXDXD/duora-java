import { createContext, useContext } from "react";
import type { CenarioFront } from "./tipos";

/** null só fora do provedor: o App sempre o monta com o cenário já carregado. */
const ContextoDoCenario = createContext<CenarioFront | null>(null);

export const ProvedorDeCenario = ContextoDoCenario.Provider;

/** O cenário em jogo; fora do ProvedorDeCenario é erro de montagem. */
export function useCenario(): CenarioFront {
  const cenario = useContext(ContextoDoCenario);
  if (cenario === null) {
    throw new Error("useCenario fora do ProvedorDeCenario");
  }
  return cenario;
}
