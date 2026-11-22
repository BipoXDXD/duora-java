import { render, type RenderResult } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";
import { ProvedorDeCenario } from "../cenario/contexto";
import type { CenarioFront } from "../cenario/tipos";
import { cenarioDoTerraco } from "./cenarioDoTerraco";

/** Renderiza dentro do ProvedorDeCenario, como o App faz; por padrão com o terraço. */
export function renderComCenario(ui: ReactElement, c: CenarioFront = cenarioDoTerraco()): RenderResult {
  function Provedor({ children }: { children: ReactNode }) {
    return <ProvedorDeCenario value={c}>{children}</ProvedorDeCenario>;
  }
  return render(ui, { wrapper: Provedor });
}
