import { afterEach, describe, expect, it, vi } from "vitest";
import { concluirCadastro, confirmarEntrada, obterConfiguracao, pedirLink } from "./conta";
import { ErroHttp } from "./http";

function responder(corpo: unknown, status = 200) {
  const fetchFalso = vi.fn<typeof fetch>(() =>
    Promise.resolve(new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": "application/json" } })),
  );
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("obterConfiguracao", () => {
  it("configuracaoDevolveAChave", async () => {
    const fetchFalso = responder({ chaveDoSiteTurnstile: "1x00000000000000000000AA" });

    const configuracao = await obterConfiguracao();

    expect(configuracao).toEqual({ chaveDoSiteTurnstile: "1x00000000000000000000AA" });
    expect(fetchFalso).toHaveBeenCalledWith("/api/conta/configuracao", expect.objectContaining({ method: "GET" }));
  });

  it("configuracaoSemChaveViraRespostaInvalida", async () => {
    responder({});

    await expect(obterConfiguracao()).rejects.toThrow(ErroHttp);
    await expect(obterConfiguracao()).rejects.toMatchObject({ codigo: "RESPOSTA_INVALIDA" });
  });
});

describe("pedirLink", () => {
  it("enviaEmailTokenEChave", async () => {
    const fetchFalso = responder({ mensagem: "Enviamos o link." }, 202);

    await pedirLink("ana@exemplo.com", "token-do-desafio", "chave-0001");

    const [url, opcoes] = fetchFalso.mock.calls[0] ?? [];
    expect(url).toBe("/api/conta/entrar");
    expect(opcoes?.method).toBe("POST");
    expect(opcoes?.body).toBe(JSON.stringify({ email: "ana@exemplo.com", tokenVerificacao: "token-do-desafio" }));
    expect(new Headers(opcoes?.headers).get("Idempotency-Key")).toBe("chave-0001");
  });

  it("devolveAMensagemDoServidor", async () => {
    responder({ mensagem: "Enviamos o link." }, 202);

    await expect(pedirLink("ana@exemplo.com", "t", "chave-0001")).resolves.toEqual({ mensagem: "Enviamos o link." });
  });

  it("respostaSemMensagemViraRespostaInvalida", async () => {
    responder({}, 202);

    await expect(pedirLink("ana@exemplo.com", "t", "chave-0001")).rejects.toMatchObject({
      codigo: "RESPOSTA_INVALIDA",
    });
  });
});

describe("confirmarEntrada", () => {
  it("enviaOTokenEDevolveSeOCadastroEstaConcluido", async () => {
    const fetchFalso = responder({ cadastroConcluido: false });

    await expect(confirmarEntrada("token-do-link")).resolves.toEqual({ cadastroConcluido: false });

    const [url, opcoes] = fetchFalso.mock.calls[0] ?? [];
    expect(url).toBe("/api/conta/confirmar");
    expect(opcoes?.method).toBe("POST");
    expect(opcoes?.body).toBe(JSON.stringify({ token: "token-do-link" }));
    expect(new Headers(opcoes?.headers).get("X-Duora")).toBe("1");
  });

  it("respostaSemCadastroConcluidoViraRespostaInvalida", async () => {
    responder({});

    await expect(confirmarEntrada("token-do-link")).rejects.toMatchObject({ codigo: "RESPOSTA_INVALIDA" });
  });
});

describe("concluirCadastro", () => {
  const DADOS = {
    dataDeNascimento: "2000-01-31",
    versaoTermos: "2026-11-23",
    versaoPrivacidade: "2026-11-20",
    aceito: true,
  } as const;

  it("enviaOsDadosEDevolveOProximoPasso", async () => {
    const fetchFalso = responder({ cadastroConcluido: false, proximoPasso: "VERIFICACAO" });

    await expect(concluirCadastro(DADOS)).resolves.toEqual({ cadastroConcluido: false, proximoPasso: "VERIFICACAO" });

    const [url, opcoes] = fetchFalso.mock.calls[0] ?? [];
    expect(url).toBe("/api/conta/cadastro");
    expect(opcoes?.method).toBe("POST");
    expect(opcoes?.body).toBe(JSON.stringify(DADOS));
    expect(new Headers(opcoes?.headers).get("X-Duora")).toBe("1");
  });

  it("respostaComOutroPassoViraRespostaInvalida", async () => {
    responder({ cadastroConcluido: true, proximoPasso: "TERRACO" });

    await expect(concluirCadastro(DADOS)).rejects.toMatchObject({ codigo: "RESPOSTA_INVALIDA" });
  });
});
