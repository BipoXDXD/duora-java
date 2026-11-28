import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DadosDeNamoro, MeuPerfil } from "../../api/perfil";
import { criarRotas } from "../../rotas/rotas";
import { stubarFetch } from "../../teste/fetchFalso";
import { Namoro } from "./Namoro";

const COM_NAMORO: MeuPerfil = {
  versao: 4,
  apelido: "Lua",
  pronomes: null,
  descricaoPronomes: null,
  intencoes: ["AMIZADE", "NAMORO"],
  bio: "",
  pausada: false,
  prontoParaFila: true,
  pendencias: [],
  foto: { aprovada: null, pendente: null, recusadaEm: null },
};

const SO_AMIZADE: MeuPerfil = { ...COM_NAMORO, versao: 5, intencoes: ["AMIZADE"] };

const DADOS: DadosDeNamoro = {
  genero: "MULHER",
  descricaoGenero: null,
  interesses: ["HOMEM", "PESSOA_NAO_BINARIA"],
  consentidoEm: "2026-11-28T02:30:00Z",
};

const SEM_DADOS = { genero: null, descricaoGenero: null, interesses: [], consentidoEm: null };

const AVISO =
  "Gênero e interesse são dados sensíveis. Usamos só para encontrar pessoas compatíveis e nunca mostramos a ninguém. Você pode apagar quando quiser.";

interface RespostaFalsa {
  status: number;
  corpo?: unknown;
}

function json({ status, corpo }: RespostaFalsa): Response {
  if (status === 204) {
    return new Response(null, { status });
  }
  const tipo = status >= 400 ? "application/problem+json" : "application/json";
  return new Response(JSON.stringify(corpo ?? null), { status, headers: { "Content-Type": tipo } });
}

/** Responde cada pedido por "MÉTODO /caminho"; guarda os pedidos e os corpos dos PUT, na ordem. */
function prepararFetch(respostas: Record<string, RespostaFalsa>) {
  const corposDoPut: unknown[] = [];
  const pedidos: string[] = [];
  stubarFetch((entrada, opcoes) => {
    const chave = `${opcoes?.method ?? "GET"} ${typeof entrada === "string" ? entrada : "?"}`;
    pedidos.push(chave);
    if (opcoes?.method === "PUT") {
      corposDoPut.push(JSON.parse(typeof opcoes.body === "string" ? opcoes.body : "null"));
    }
    const resposta = respostas[chave];
    return resposta === undefined
      ? Promise.reject(new Error(`pedido inesperado: ${chave}`))
      : Promise.resolve(json(resposta));
  });
  return { corposDoPut, pedidos };
}

/** Abre a tela com o que o loader da rota deixaria no cache. */
function abrir(perfil: MeuPerfil, dados: DadosDeNamoro | null) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  cliente.setQueryData(["perfil"], perfil);
  cliente.setQueryData(["perfil", "namoro"], dados);
  const roteador = createMemoryRouter([{ path: "/perfil/namoro", Component: Namoro }], {
    initialEntries: ["/perfil/namoro"],
  });
  render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
  return { usuario: userEvent.setup() };
}

function botaoSalvar(): HTMLElement {
  return screen.getByRole("button", { name: /Salvar dados de namoro|Salvando…/ });
}

function caixaDeConsentimento(): HTMLElement {
  return screen.getByRole("checkbox", { name: "Concordo com o uso desses dados para o pareamento" });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Namoro", () => {
  it("namoroSoComIntencaoDeNamoro", async () => {
    const { pedidos } = prepararFetch({
      "GET /api/conta": { status: 200, corpo: { estado: "ATIVA", cadastroConcluido: true, verificada: true } },
      "GET /api/perfil": { status: 200, corpo: SO_AMIZADE },
    });
    const clienteDeConsulta = new QueryClient();
    const roteador = createMemoryRouter(
      criarRotas({ clienteDeConsulta, tomarTokenDoLink: () => null, caminhoDaCarga: "/perfil/namoro" }),
      { initialEntries: ["/perfil/namoro"] },
    );
    render(
      <QueryClientProvider client={clienteDeConsulta}>
        <RouterProvider router={roteador} />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Para namoro" })).toBeInTheDocument();
    expect(screen.getByText("O namoro está desligado. Para usar, marque Namoro em Meu perfil.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ir para Meu perfil" })).toHaveAttribute("href", "/perfil");
    expect(screen.queryByRole("group", { name: "Seu gênero" })).not.toBeInTheDocument();
    expect(screen.queryByText(AVISO)).not.toBeInTheDocument();
    expect(pedidos).not.toContain("GET /api/perfil/namoro");
  });

  it("avisoComTextoExato", async () => {
    abrir(COM_NAMORO, null);

    expect(await screen.findByText(AVISO)).toBeInTheDocument();
  });

  it("quatroOpcoesDeGenero", async () => {
    abrir(COM_NAMORO, null);

    const grupo = await screen.findByRole("group", { name: "Seu gênero" });
    expect(grupo).toHaveAccessibleDescription("Escolha a opção com que você se identifica.");
    expect(
      within(grupo)
        .getAllByRole("radio")
        .map((opcao) => opcao.closest("label")?.textContent),
    ).toEqual(["Mulher", "Homem", "Pessoa não binária", "Prefiro descrever"]);
    const interesses = screen.getByRole("group", { name: "Quem você quer conhecer" });
    expect(
      within(interesses)
        .getAllByRole("checkbox")
        .map((opcao) => opcao.closest("label")?.textContent),
    ).toEqual(["Mulheres", "Homens", "Pessoas não binárias", "Pessoas que se descrevem de outra forma"]);
  });

  it("prefiroDescreverPedeTexto", async () => {
    prepararFetch({});
    const { usuario } = abrir(COM_NAMORO, null);
    expect(screen.queryByRole("textbox", { name: "Como você se descreve" })).not.toBeInTheDocument();

    await usuario.click(await screen.findByRole("radio", { name: "Prefiro descrever" }));
    const campo = screen.getByRole("textbox", { name: "Como você se descreve" });
    expect(campo).toHaveAccessibleDescription("Até 40 caracteres.");
    await usuario.click(screen.getByRole("checkbox", { name: "Homens" }));
    await usuario.click(caixaDeConsentimento());
    await usuario.click(botaoSalvar());

    expect(campo).toHaveFocus();
    expect(campo).toHaveAttribute("aria-invalid", "true");
    expect(campo).toHaveAccessibleDescription(
      "Até 40 caracteres. Escreva como você se descreve, com até 40 caracteres.",
    );
  });

  it("consentimentoNovoNuncaVemMarcado", async () => {
    abrir(COM_NAMORO, null);

    await screen.findByRole("heading", { level: 1, name: "Para namoro" });
    expect(caixaDeConsentimento()).not.toBeChecked();
    expect(screen.queryByText(/Você concordou em/)).not.toBeInTheDocument();
  });

  it("consentimentoGravadoVemMarcadoComAData", async () => {
    abrir(COM_NAMORO, DADOS);

    await screen.findByRole("heading", { level: 1, name: "Para namoro" });
    expect(caixaDeConsentimento()).toBeChecked();
    // 02h30 UTC do dia 28 ainda é dia 27 em São Paulo.
    expect(screen.getByText("Você concordou em 27 de novembro de 2026.")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Mulher" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Homens" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Pessoas não binárias" })).toBeChecked();
  });

  it("salvarNamoroExigeConsentimento", async () => {
    const { pedidos } = prepararFetch({});
    const { usuario } = abrir(COM_NAMORO, null);

    await usuario.click(await screen.findByRole("radio", { name: "Mulher" }));
    await usuario.click(screen.getByRole("checkbox", { name: "Homens" }));

    expect(botaoSalvar()).toHaveAttribute("aria-disabled", "true");
    expect(botaoSalvar()).toHaveAccessibleDescription("Marque a caixa de consentimento para salvar.");
    await usuario.click(botaoSalvar());
    expect(pedidos.filter((pedido) => pedido.startsWith("PUT"))).toEqual([]);

    await usuario.click(caixaDeConsentimento());
    expect(botaoSalvar()).not.toHaveAttribute("aria-disabled");
    expect(screen.queryByText("Marque a caixa de consentimento para salvar.")).not.toBeInTheDocument();
  });

  it("salvoMandaConsentimentoTrue", async () => {
    const salvos: DadosDeNamoro = { ...DADOS, interesses: ["HOMEM"] };
    const { corposDoPut } = prepararFetch({
      "PUT /api/perfil/namoro": { status: 200, corpo: salvos },
      "GET /api/perfil": { status: 200, corpo: COM_NAMORO },
    });
    const { usuario } = abrir(COM_NAMORO, null);

    await usuario.click(await screen.findByRole("radio", { name: "Mulher" }));
    await usuario.click(screen.getByRole("checkbox", { name: "Homens" }));
    await usuario.click(caixaDeConsentimento());
    await usuario.click(botaoSalvar());

    expect(await screen.findByText("Dados de namoro salvos.")).toBeInTheDocument();
    expect(corposDoPut).toEqual([
      { genero: "MULHER", descricaoGenero: null, interesses: ["HOMEM"], consentimento: true },
    ]);
  });

  it("validacaoAntesDoEnvio", async () => {
    const { pedidos } = prepararFetch({});
    const { usuario } = abrir(COM_NAMORO, null);

    await usuario.click(
      await screen.findByRole("checkbox", { name: "Concordo com o uso desses dados para o pareamento" }),
    );
    await usuario.click(botaoSalvar());

    expect(screen.getByText("Escolha seu gênero.")).toBeInTheDocument();
    expect(screen.getByText("Marque ao menos uma opção.")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Mulher" })).toHaveFocus();
    expect(pedidos).toEqual([]);
  });

  it("apagarPedeConfirmacaoEChamaDelete", async () => {
    const { pedidos } = prepararFetch({
      "DELETE /api/perfil/namoro": { status: 204 },
      "GET /api/perfil": { status: 200, corpo: SO_AMIZADE },
      "GET /api/perfil/namoro": { status: 200, corpo: SEM_DADOS },
    });
    const { usuario } = abrir(COM_NAMORO, DADOS);

    await usuario.click(await screen.findByRole("button", { name: "Apagar dados de namoro" }));
    const dialogo = screen.getByRole("dialog", { name: "Apagar dados de namoro?" });
    expect(dialogo).toHaveAccessibleDescription(
      "Apagamos seu gênero e quem você quer conhecer, e Namoro sai das suas intenções. Para voltar, preencha e concorde de novo.",
    );
    expect(pedidos).toEqual([]);

    await usuario.click(within(dialogo).getByRole("button", { name: "Apagar dados" }));

    expect(await screen.findByText("Dados de namoro apagados.")).toBeInTheDocument();
    expect(pedidos).toContain("DELETE /api/perfil/namoro");
    // A invalidação por prefixo relê o perfil: sem Namoro, a tela mostra o desligado.
    await waitFor(() => {
      expect(pedidos).toContain("GET /api/perfil");
    });
    expect(
      await screen.findByText("O namoro está desligado. Para usar, marque Namoro em Meu perfil."),
    ).toBeInTheDocument();
  });
});
