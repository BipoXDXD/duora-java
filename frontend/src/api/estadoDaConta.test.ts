import { afterEach, describe, expect, it, vi } from "vitest";
import { obterConta } from "./estadoDaConta";
import { ErroHttp } from "./http";

function responder(status: number, corpo?: unknown) {
  const fetchFalso = vi.fn<typeof fetch>(() => {
    const json = corpo === undefined ? null : JSON.stringify(corpo);
    const cabecalhos: Record<string, string> = json === null ? {} : { "Content-Type": "application/json" };
    return Promise.resolve(new Response(json, { status, headers: cabecalhos }));
  });
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("obterConta", () => {
  it("contaAtivaDevolveOsCampos", async () => {
    const fetchFalso = responder(200, { estado: "ATIVA", cadastroConcluido: true, verificada: false });

    const conta = await obterConta();

    expect(conta).toEqual({ estado: "ATIVA", cadastroConcluido: true, verificada: false });
    expect(fetchFalso.mock.calls[0]?.[0]).toBe("/api/conta");
    expect(fetchFalso.mock.calls[0]?.[1]?.method).toBe("GET");
  });

  it("semConteudo204EhAnonimo", async () => {
    responder(204);
    // window.location.assign não aceita spy (propriedade não configurável); o global, sim.
    const assign = vi.fn();
    vi.stubGlobal("location", { href: window.location.href, assign });

    const conta = await obterConta();

    expect(conta).toBeNull();
    expect(assign).not.toHaveBeenCalled();
  });

  it("erro401Lanca", async () => {
    responder(401, { codigo: "NAO_AUTENTICADO" });

    await expect(obterConta()).rejects.toMatchObject({ name: "ErroHttp", status: 401 });
  });

  it("contaIndisponivel403ChegaComCodigoEMensagem", async () => {
    responder(403, { codigo: "CONTA_INDISPONIVEL", mensagem: "Esta conta não está disponível." });

    await expect(obterConta()).rejects.toMatchObject({
      name: "ErroHttp",
      status: 403,
      codigo: "CONTA_INDISPONIVEL",
      mensagem: "Esta conta não está disponível.",
    });
  });

  it("erro500Lanca", async () => {
    responder(500);

    await expect(obterConta()).rejects.toBeInstanceOf(ErroHttp);
  });

  it("corpoForaDoContratoLanca", async () => {
    responder(200, { estado: "SUSPENSA", cadastroConcluido: true, verificada: false });

    await expect(obterConta()).rejects.toMatchObject({ codigo: "RESPOSTA_INVALIDA" });
  });
});
