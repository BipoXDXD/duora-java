import { afterEach, describe, expect, it, vi } from "vitest";
import { enviarDenuncia } from "./denuncia";

const MENSAGEM = "Recebemos sua denúncia. Se você deixou um e-mail, respondemos por lá.";

function responder(status: number, corpo: unknown, cabecalhos: Record<string, string> = {}) {
  const tipo = status >= 400 ? "application/problem+json" : "application/json";
  const fetchFalso = vi.fn<typeof fetch>(() =>
    Promise.resolve(new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": tipo, ...cabecalhos } })),
  );
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("enviarDenuncia", () => {
  it("enviaADenunciaEDevolveAMensagem", async () => {
    const fetchFalso = responder(202, { mensagem: MENSAGEM });
    const denuncia = {
      motivo: "GOLPE",
      descricao: "Pediu dinheiro adiantado.",
      referencia: "@golpista",
      emailContato: "ana@exemplo.com",
      tokenVerificacao: "token-1",
    } as const;

    await expect(enviarDenuncia(denuncia)).resolves.toEqual({ mensagem: MENSAGEM });

    expect(fetchFalso).toHaveBeenCalledWith(
      "/api/denuncias-publicas",
      expect.objectContaining({ method: "POST", body: JSON.stringify(denuncia) }),
    );
  });

  it("recusaTrazAMensagemDoCampo", async () => {
    responder(422, { codigo: "DENUNCIA_PUBLICA_INVALIDA", mensagem: "Escolha o que aconteceu." });

    await expect(
      enviarDenuncia({ motivo: "OUTRO", descricao: "Algo estranho.", tokenVerificacao: "token-1" }),
    ).rejects.toMatchObject({ status: 422, codigo: "DENUNCIA_PUBLICA_INVALIDA", mensagem: "Escolha o que aconteceu." });
  });

  it("limiteTrazOsSegundosDeEspera", async () => {
    responder(429, { codigo: "LIMITE_DE_TAXA" }, { "Retry-After": "120" });

    await expect(
      enviarDenuncia({ motivo: "OUTRO", descricao: "Algo estranho.", tokenVerificacao: "token-1" }),
    ).rejects.toMatchObject({ status: 429, tentarDeNovoEmSegundos: 120 });
  });

  it("semMensagemViraRespostaInvalida", async () => {
    responder(202, {});

    await expect(
      enviarDenuncia({ motivo: "OUTRO", descricao: "Algo estranho.", tokenVerificacao: "token-1" }),
    ).rejects.toMatchObject({ codigo: "RESPOSTA_INVALIDA" });
  });
});
