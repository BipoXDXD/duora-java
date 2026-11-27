import { render } from "@testing-library/react";
import { createRef } from "react";
import { describe, expect, it, vi } from "vitest";
import {
  criarCarregadorDoTurnstile,
  Turnstile,
  type ApiTurnstile,
  type ControleDoTurnstile,
  type OpcoesDoWidget,
} from "./Turnstile";

const ID_DO_WIDGET = "widget-1";

function apiFalsa() {
  const opcoesRecebidas: OpcoesDoWidget[] = [];
  const desenhar = vi.fn((_elemento: HTMLElement, opcoes: OpcoesDoWidget) => {
    opcoesRecebidas.push(opcoes);
    return ID_DO_WIDGET;
  });
  const reiniciar = vi.fn<(id: string) => void>();
  const remover = vi.fn<(id: string) => void>();
  const api: ApiTurnstile = { render: desenhar, reset: reiniciar, remove: remover };
  return {
    api,
    desenhar,
    reiniciar,
    remover,
    /** As opções do único render; falha se o widget não foi desenhado. */
    opcoes: (): OpcoesDoWidget => {
      const [opcoes] = opcoesRecebidas;
      if (!opcoes) {
        throw new Error("o widget não foi desenhado");
      }
      return opcoes;
    },
  };
}

async function renderizar() {
  const falsa = apiFalsa();
  const ref = createRef<ControleDoTurnstile>();
  const avisos = {
    aoObterToken: vi.fn<(t: string) => void>(),
    aoExpirar: vi.fn<() => void>(),
    aoFalhar: vi.fn<() => void>(),
  };
  const resultado = render(
    <Turnstile
      chaveDoSite="1x00000000000000000000AA"
      {...avisos}
      carregar={() => Promise.resolve(falsa.api)}
      ref={ref}
    />,
  );
  await vi.waitFor(() => {
    expect(falsa.desenhar).toHaveBeenCalled();
  });
  return { ...falsa, ...resultado, ...avisos, ref };
}

describe("Turnstile", () => {
  it("renderizaComChaveIdiomaETema", async () => {
    const { desenhar, opcoes } = await renderizar();

    expect(desenhar).toHaveBeenCalledOnce();
    expect(desenhar.mock.calls[0]?.[0]).toBeInstanceOf(HTMLElement);
    expect(opcoes()).toMatchObject({
      sitekey: "1x00000000000000000000AA",
      language: "pt-br",
      theme: "dark",
    });
  });

  it("tokenChegaPeloCallback", async () => {
    const { opcoes, aoObterToken } = await renderizar();

    opcoes().callback("token-do-desafio");

    expect(aoObterToken).toHaveBeenCalledExactlyOnceWith("token-do-desafio");
  });

  it("expiradoAvisa", async () => {
    const { opcoes, aoExpirar } = await renderizar();

    opcoes()["expired-callback"]();

    expect(aoExpirar).toHaveBeenCalledOnce();
  });

  it("erroAvisa", async () => {
    const { opcoes, aoFalhar } = await renderizar();

    opcoes()["error-callback"]();

    expect(aoFalhar).toHaveBeenCalledOnce();
  });

  it("reiniciarChamaReset", async () => {
    const { reiniciar, ref } = await renderizar();

    ref.current?.reiniciar();

    expect(reiniciar).toHaveBeenCalledExactlyOnceWith(ID_DO_WIDGET);
  });

  it("desmontarChamaRemove", async () => {
    const { remover, unmount } = await renderizar();

    unmount();

    expect(remover).toHaveBeenCalledExactlyOnceWith(ID_DO_WIDGET);
  });

  it("scriptCarregaUmaVez", () => {
    const documento = document.implementation.createHTMLDocument();
    const carregar = criarCarregadorDoTurnstile(documento);

    void carregar();
    void carregar();

    const scripts = documento.querySelectorAll("script");
    expect(scripts).toHaveLength(1);
    expect(scripts[0]?.src).toBe("https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit");
    expect(scripts[0]?.async).toBe(true);
  });
});
