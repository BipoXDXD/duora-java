import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Explicacao } from "./Explicacao";

const EMAIL = "encarregado@exemplo.invalid";

function renderizar({ temSuporte = true, tentativas = 3 } = {}) {
  const aoComecar = vi.fn();
  render(
    <Explicacao tentativas={tentativas} emailDoEncarregado={EMAIL} temSuporte={temSuporte} aoComecar={aoComecar} />,
  );
  return { aoComecar, comecar: screen.getByRole("button", { name: "Começar a selfie" }) };
}

describe("Explicacao", () => {
  it("comecarIndisponivelSemConsentimento", async () => {
    const usuario = userEvent.setup();
    const { aoComecar, comecar } = renderizar();

    await usuario.click(comecar);

    expect(comecar).toHaveAttribute("aria-disabled", "true");
    expect(comecar).toHaveAccessibleDescription("Marque a caixa para começar.");
    expect(aoComecar).not.toHaveBeenCalled();
  });

  it("comecarComConsentimentoChamaAoComecar", async () => {
    const usuario = userEvent.setup();
    const { aoComecar, comecar } = renderizar();

    await usuario.click(
      screen.getByRole("checkbox", { name: "Concordo com o uso da minha imagem para confirmar minha idade" }),
    );
    await usuario.click(comecar);

    expect(comecar).not.toHaveAttribute("aria-disabled");
    expect(aoComecar).toHaveBeenCalledOnce();
  });

  it("comecarIndisponivelSemSuporte", async () => {
    const usuario = userEvent.setup();
    const { aoComecar, comecar } = renderizar({ temSuporte: false });

    await usuario.click(screen.getByRole("checkbox"));
    await usuario.click(comecar);

    expect(comecar).toHaveAttribute("aria-disabled", "true");
    expect(comecar).toHaveAccessibleDescription(
      "Este navegador não abre a câmera. Abra o Duora no Chrome, no Safari ou no Firefox atualizado.",
    );
    expect(aoComecar).not.toHaveBeenCalled();
  });

  it("mostraAsTentativasRestantes", () => {
    renderizar({ tentativas: 2 });

    expect(screen.getByText("Tentativas restantes hoje: 2")).toBeInTheDocument();
  });

  it("explicaAwsEUsoDaImagem", () => {
    renderizar();

    expect(
      screen.getByText(
        "O Duora é só para maiores de 18 anos. Para confirmar, pedimos uma selfie em vídeo de poucos segundos.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "A AWS, nos Estados Unidos, confere se há uma pessoa real diante da câmera e estima a idade. Usamos a imagem só para isso e a apagamos logo depois. Guardamos só o resultado, a data e o método.",
      ),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "Procure um lugar claro.",
      "Tire óculos escuros, boné ou máscara.",
      "Fique sem outra pessoa no quadro.",
    ]);
  });

  it("avisoDeLuzOfereceOCaminhoManual", () => {
    renderizar();

    const link = screen.getByRole("link", { name: EMAIL });

    expect(link).toHaveAttribute("href", `mailto:${EMAIL}`);
    expect(link.closest("p")?.textContent).toBe(
      `Durante a selfie, a tela pisca em cores. Se luzes piscando fazem mal a você, não comece: escreva para ${EMAIL} e combinamos outro jeito de confirmar sua idade.`,
    );
  });
});
