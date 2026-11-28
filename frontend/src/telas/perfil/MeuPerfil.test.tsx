import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { MeuPerfil as Perfil } from "../../api/perfil";
import { MeuPerfil } from "./MeuPerfil";

const SALVO: Perfil = {
  versao: 3,
  apelido: "Lua",
  pronomes: "ELU_DELU",
  descricaoPronomes: null,
  intencoes: ["AMIZADE"],
  bio: "",
  pausada: false,
  prontoParaFila: true,
  pendencias: [],
  foto: { aprovada: null, pendente: null, recusadaEm: null },
};

const NAO_SALVO: Perfil = {
  ...SALVO,
  versao: null,
  apelido: null,
  pronomes: null,
  intencoes: [],
  prontoParaFila: false,
  pendencias: ["PERFIL"],
};

interface RespostaFalsa {
  status: number;
  corpo?: unknown;
}

function json({ status, corpo }: RespostaFalsa): Response {
  const tipo = status >= 400 ? "application/problem+json" : "application/json";
  return new Response(JSON.stringify(corpo ?? null), { status, headers: { "Content-Type": tipo } });
}

/** O PUT responde `put` (um por chamada, o último se repete); o GET responde `get`. */
function prepararFetch(put: RespostaFalsa[], get: Perfil = SALVO) {
  const corposDoPut: Record<string, unknown>[] = [];
  let leituras = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      if (entrada === "/api/perfil" && opcoes?.method === "PUT") {
        corposDoPut.push(JSON.parse(typeof opcoes.body === "string" ? opcoes.body : "null") as Record<string, unknown>);
        const resposta = put[Math.min(corposDoPut.length - 1, put.length - 1)];
        return Promise.resolve(json(resposta ?? { status: 500 }));
      }
      if (entrada === "/api/perfil" && opcoes?.method === "GET") {
        leituras += 1;
        return Promise.resolve(json({ status: 200, corpo: get }));
      }
      return Promise.reject(new Error(`pedido inesperado: ${typeof entrada === "string" ? entrada : "?"}`));
    }),
  );
  return { corposDoPut, leituras: () => leituras };
}

function renderizar(perfil: Perfil = SALVO, verificada = false) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  cliente.setQueryData(["conta"], { estado: "ATIVA", cadastroConcluido: true, verificada });
  cliente.setQueryData(["perfil"], perfil);
  cliente.setQueryData(["perfil", "namoro"], null);
  render(
    <QueryClientProvider client={cliente}>
      <MeuPerfil />
    </QueryClientProvider>,
  );
  return { cliente };
}

function botaoSalvar(): HTMLElement {
  return screen.getByRole("button", { name: /Salvar perfil|Salvando…/ });
}

function campoDoApelido(): HTMLInputElement {
  return screen.getByRole("textbox", { name: "Apelido" });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("MeuPerfil", () => {
  it("enviaVersaoNoPut", async () => {
    const { corposDoPut } = prepararFetch([{ status: 200, corpo: { ...SALVO, versao: 4, apelido: "Sol" } }]);
    renderizar();
    const usuario = userEvent.setup();

    await usuario.clear(campoDoApelido());
    await usuario.type(campoDoApelido(), "Sol");
    await usuario.click(botaoSalvar());

    await waitFor(() => {
      expect(corposDoPut).toHaveLength(1);
    });
    expect(corposDoPut[0]).toEqual({
      versao: 3,
      apelido: "Sol",
      pronomes: "ELU_DELU",
      descricaoPronomes: null,
      intencoes: ["AMIZADE"],
      bio: "",
    });
  });

  it("primeiraVezEnviaVersaoNula", async () => {
    const { corposDoPut } = prepararFetch([{ status: 200, corpo: { ...SALVO, versao: 1 } }]);
    renderizar(NAO_SALVO);
    const usuario = userEvent.setup();
    expect(screen.getByText("Seu perfil é o que as pessoas veem quando encontram você.")).toBeInTheDocument();

    await usuario.type(campoDoApelido(), "Lua");
    await usuario.click(screen.getByRole("checkbox", { name: "Amizade" }));
    await usuario.click(botaoSalvar());

    await waitFor(() => {
      expect(corposDoPut).toHaveLength(1);
    });
    expect(corposDoPut[0]).toMatchObject({ versao: null, apelido: "Lua", pronomes: null, intencoes: ["AMIZADE"] });
  });

  it("versaoObsoletaRecarrega", async () => {
    const { corposDoPut, leituras } = prepararFetch(
      [
        { status: 409, corpo: { codigo: "VERSAO_OBSOLETA", mensagem: "Seu perfil mudou enquanto você editava." } },
        { status: 200, corpo: { ...SALVO, versao: 6 } },
      ],
      { ...SALVO, versao: 5, apelido: "Sol" },
    );
    renderizar();
    const usuario = userEvent.setup();

    await usuario.click(botaoSalvar());

    const alerta = await screen.findByText("Seu perfil mudou em outro aparelho. Confira e salve de novo.");
    expect(alerta).toHaveAttribute("role", "alert");
    expect(alerta).toHaveFocus();
    expect(leituras()).toBe(1);
    expect(campoDoApelido()).toHaveValue("Sol");

    await usuario.click(botaoSalvar());
    await waitFor(() => {
      expect(corposDoPut).toHaveLength(2);
    });
    expect(corposDoPut[1]).toMatchObject({ versao: 5, apelido: "Sol" });
  });

  it("avisoAoTirarNamoro", async () => {
    prepararFetch([]);
    renderizar({ ...SALVO, intencoes: ["AMIZADE", "NAMORO"] });
    const usuario = userEvent.setup();
    const aviso = "Ao salvar sem Namoro, apagamos seu gênero e quem você quer conhecer.";
    expect(screen.queryByText(aviso)).not.toBeInTheDocument();

    await usuario.click(screen.getByRole("checkbox", { name: "Namoro" }));

    expect(screen.getByText(aviso)).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Como você quer usar o Duora" })).toHaveAccessibleDescription(aviso);
  });

  it("erroDoServidorVaiParaOCampo", async () => {
    const mensagem = "Em Prefiro descrever, use de 1 a 40 caracteres na orientação e de 1 a 20 nos pronomes.";
    prepararFetch([{ status: 422, corpo: { codigo: "IDENTIDADE_INVALIDA", mensagem } }]);
    renderizar();
    const usuario = userEvent.setup();

    await usuario.click(screen.getByRole("radio", { name: "Prefiro descrever" }));
    await usuario.type(screen.getByRole("textbox", { name: "Seus pronomes" }), "ilu/dilu");
    await usuario.click(botaoSalvar());

    const campo = screen.getByRole("textbox", { name: "Seus pronomes" });
    await waitFor(() => {
      expect(campo).toHaveAttribute("aria-invalid", "true");
    });
    expect(campo).toHaveAccessibleDescription(`Até 20 caracteres. ${mensagem}`);
    expect(campo).toHaveFocus();
    expect(screen.getByRole("alert")).toHaveTextContent("");
  });

  it("outroErroMostraFalhaGeral", async () => {
    prepararFetch([{ status: 503, corpo: { codigo: "INDISPONIVEL", mensagem: "Fora." } }]);
    renderizar();

    await userEvent.setup().click(botaoSalvar());

    expect(await screen.findByText("Não conseguimos salvar seu perfil. Tente de novo em instantes.")).toHaveFocus();
  });

  it("validacaoFocaOPrimeiroInvalidoSemEnviar", async () => {
    const { corposDoPut } = prepararFetch([]);
    renderizar();
    const usuario = userEvent.setup();

    await usuario.clear(campoDoApelido());
    await usuario.click(screen.getByRole("checkbox", { name: "Amizade" }));
    await usuario.click(botaoSalvar());

    expect(campoDoApelido()).toHaveFocus();
    expect(campoDoApelido()).toHaveAttribute("aria-invalid", "true");
    expect(campoDoApelido()).toHaveAccessibleDescription("Até 20 caracteres. Escreva um apelido.");
    expect(screen.getByRole("group", { name: "Como você quer usar o Duora" })).toHaveAccessibleDescription(
      "Marque Amizade, Namoro ou as duas.",
    );
    expect(corposDoPut).toHaveLength(0);
  });

  it("contadorDaBio", async () => {
    prepararFetch([]);
    renderizar();

    await userEvent.setup().type(screen.getByRole("textbox", { name: "Sobre você" }), "abc");

    expect(screen.getByText("3/280")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Sobre você" })).toHaveAccessibleDescription("Opcional. 3/280");
  });

  it("salvoMostraStatus", async () => {
    const novo: Perfil = { ...SALVO, versao: 4, bio: "Oi" };
    prepararFetch([{ status: 200, corpo: novo }]);
    const { cliente } = renderizar();
    const usuario = userEvent.setup();

    await usuario.type(screen.getByRole("textbox", { name: "Sobre você" }), "Oi");
    await usuario.click(botaoSalvar());

    expect(await screen.findByText("Perfil salvo.")).toHaveAttribute("role", "status");
    expect(cliente.getQueryData(["perfil"])).toEqual(novo);
    expect(cliente.getQueryState(["perfil", "namoro"])?.isInvalidated).toBe(true);
    expect(botaoSalvar()).toHaveTextContent("Salvar perfil");
  });

  it("editarDepoisDeSalvarTiraOStatus", async () => {
    prepararFetch([{ status: 200, corpo: SALVO }]);
    renderizar();
    const usuario = userEvent.setup();
    await usuario.click(botaoSalvar());
    await screen.findByText("Perfil salvo.");

    await usuario.type(campoDoApelido(), "a");

    expect(screen.queryByText("Perfil salvo.")).not.toBeInTheDocument();
  });

  it("prefiroDescreverAbreCampo", async () => {
    prepararFetch([]);
    renderizar();
    expect(screen.queryByRole("textbox", { name: "Seus pronomes" })).not.toBeInTheDocument();

    await userEvent.setup().click(screen.getByRole("radio", { name: "Prefiro descrever" }));

    expect(screen.getByRole("textbox", { name: "Seus pronomes" })).toHaveAccessibleDescription("Até 20 caracteres.");
  });

  it("descricaoDosPronomesSoVaiComPrefiroDescrever", async () => {
    const { corposDoPut } = prepararFetch([{ status: 200, corpo: SALVO }]);
    renderizar();
    const usuario = userEvent.setup();

    await usuario.click(screen.getByRole("radio", { name: "Prefiro descrever" }));
    await usuario.type(screen.getByRole("textbox", { name: "Seus pronomes" }), "ilu");
    await usuario.click(screen.getByRole("radio", { name: "ela/dela" }));
    await usuario.click(botaoSalvar());

    await waitFor(() => {
      expect(corposDoPut).toHaveLength(1);
    });
    expect(corposDoPut[0]).toMatchObject({ pronomes: "ELA_DELA", descricaoPronomes: null });
  });

  it("cartaoMostraABorradaDaAprovada", () => {
    prepararFetch([]);
    renderizar(
      {
        ...SALVO,
        foto: {
          aprovada: { nitida: "/api/fotos/a/nitida", borrada: "/api/fotos/a/borrada" },
          pendente: { nitida: "/api/fotos/b/nitida", borrada: "/api/fotos/b/borrada" },
          recusadaEm: null,
        },
      },
      true,
    );

    const cartao = screen.getByRole("region", { name: "Como os outros veem você" });
    const foto = cartao.querySelector("img");
    expect(foto).toHaveAttribute("src", "/api/fotos/a/borrada");
    expect(foto).toHaveAttribute("loading", "lazy");
    expect(cartao).toHaveTextContent("elu/delu");
    expect(cartao).toHaveTextContent("Verificado");
  });

  it("cartaoSemAprovadaMostraSilhueta", () => {
    prepararFetch([]);
    renderizar({ ...SALVO, foto: { aprovada: null, pendente: { nitida: "/n", borrada: "/b" }, recusadaEm: null } });

    const cartao = screen.getByRole("region", { name: "Como os outros veem você" });
    expect(cartao.querySelector("img")).toBeNull();
    expect(screen.getByRole("img", { name: "Sem foto" })).toBeInTheDocument();
  });

  it("cartaoMostraADescricaoDosPronomes", () => {
    prepararFetch([]);
    renderizar({ ...SALVO, pronomes: "OUTROS", descricaoPronomes: "ilu/dilu" });

    expect(screen.getByRole("region", { name: "Como os outros veem você" })).toHaveTextContent("ilu/dilu");
  });
});
