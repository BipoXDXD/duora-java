import { render } from "@testing-library/react";
import { createRef } from "react";
import { describe, expect, it, vi } from "vitest";
import type { EstadoDaSessao } from "../sessao/tipos";
import { Painel } from "./Painel";

const ENCERRADO: EstadoDaSessao = {
  tela: "encerrado",
  texto: "Você saiu e o terraço foi encerrado.",
  enviando: null,
  confirmandoSaida: false,
  somLigado: false,
  momento: "crepusculo",
  ladoTelescopio: null,
};

describe("Painel", () => {
  it("fora da montagem o botão de som continua na página, oculto e com o estado do som", () => {
    const { container } = render(
      <Painel
        estado={ENCERRADO}
        refAviso={createRef()}
        aoEnviarApelido={vi.fn()}
        aoCorrigirApelido={vi.fn()}
        aoCopiarConvite={vi.fn()}
        aoCriarOutro={vi.fn()}
        aoEscolherPeca={vi.fn()}
        aoRetirar={vi.fn()}
        aoConfirmar={vi.fn()}
        aoAjustar={vi.fn()}
        aoAlternarSom={vi.fn()}
      />,
    );

    const som = container.querySelector("#som");
    expect(som).not.toBeVisible();
    expect(som).toHaveAttribute("aria-pressed", "false");
  });
});
