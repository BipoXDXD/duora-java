import { QueryClient } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { exigirConta } from "./guardas";

function responderConta(status: number, corpo?: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>(() =>
      Promise.resolve(
        corpo === undefined
          ? new Response(null, { status })
          : new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": "application/json" } }),
      ),
    ),
  );
}

function contaNoEstado(estado: string) {
  return { estado, cadastroConcluido: estado === "ATIVA", verificada: estado === "ATIVA" };
}

/** O redirect do React Router é uma Response lançada; devolve o destino dela. */
async function destinoDoRedirect(promessa: Promise<unknown>): Promise<string | null> {
  try {
    await promessa;
  } catch (lancado) {
    if (lancado instanceof Response) {
      return lancado.headers.get("Location");
    }
    throw lancado;
  }
  throw new Error("esperava um redirect");
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("exigirConta", () => {
  it("anonimoVaiParaEntrar", async () => {
    responderConta(204);

    expect(await destinoDoRedirect(exigirConta(new QueryClient(), ["PENDENTE"]))).toBe("/entrar");
  });

  it("pendenteForaDoCadastroVaiParaCadastro", async () => {
    responderConta(200, contaNoEstado("PENDENTE"));

    expect(await destinoDoRedirect(exigirConta(new QueryClient(), ["AGUARDANDO_VERIFICACAO"]))).toBe("/cadastro");
  });

  it("aguardandoVaiParaAVerificacao", async () => {
    responderConta(200, contaNoEstado("AGUARDANDO_VERIFICACAO"));

    expect(await destinoDoRedirect(exigirConta(new QueryClient(), ["PENDENTE"]))).toBe("/verificacao");
  });

  it("ativaForaDaContaVaiParaConta", async () => {
    responderConta(200, contaNoEstado("ATIVA"));

    expect(await destinoDoRedirect(exigirConta(new QueryClient(), ["PENDENTE"]))).toBe("/conta");
  });

  it("estadoAceitoDevolveAConta", async () => {
    responderConta(200, contaNoEstado("PENDENTE"));

    await expect(exigirConta(new QueryClient(), ["PENDENTE"])).resolves.toEqual(contaNoEstado("PENDENTE"));
  });

  it("contaIndisponivelNaoViraAnonimo", async () => {
    responderConta(403, { codigo: "CONTA_INDISPONIVEL", mensagem: "Esta conta não está disponível." });

    await expect(exigirConta(new QueryClient(), ["PENDENTE"])).rejects.toMatchObject({
      codigo: "CONTA_INDISPONIVEL",
    });
  });
});
