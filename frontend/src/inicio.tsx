import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { carregarCenario, CENARIO_PADRAO } from "./cenario/carregar";
import type { CenarioFront } from "./cenario/tipos";
import { ErroDeCenario } from "./componentes/ErroDeCenario";

/**
 * Espera o cenário e só então monta o app; até lá o esqueleto estático do index.html (título e céu
 * da primeira pintura) fica na tela, e o createRoot o troca pelo app ou pela tela de erro.
 * Qualquer falha (cenário inválido ou rede) vira a mensagem em pt-BR; o detalhe vai ao console.
 */
export async function iniciarApp(
  raiz: HTMLElement,
  convite: string | null,
  carregar: (id: string) => Promise<CenarioFront> = carregarCenario,
): Promise<void> {
  let cenario: CenarioFront;
  try {
    cenario = await carregar(CENARIO_PADRAO);
  } catch (erro) {
    console.error("Não foi possível carregar o cenário:", erro);
    createRoot(raiz).render(
      <StrictMode>
        <ErroDeCenario />
      </StrictMode>,
    );
    return;
  }
  createRoot(raiz).render(
    <StrictMode>
      <App convite={convite} cenario={cenario} />
    </StrictMode>,
  );
}
