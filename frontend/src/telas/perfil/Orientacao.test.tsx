import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DadosDeOrientacao } from "../../api/perfil";
import { stubarFetch } from "../../teste/fetchFalso";
import { Orientacao } from "./Orientacao";

const DADOS: DadosDeOrientacao = {
  orientacao: "BISSEXUAL",
  descricaoOrientacao: null,
  mostrarOrientacao: true,
  consentidaEm: "2026-11-28T02:30:00Z",
};

const SEM_DADOS = { orientacao: null, descricaoOrientacao: null, mostrarOrientacao: false, consentidaEm: null };

const AVISO =
  "Orientação é um dado sensível. É opcional, nunca entra no pareamento e só aparece para suas conexões se você quiser.";

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
function abrir(dados: DadosDeOrientacao | null) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  cliente.setQueryData(["perfil", "orientacao"], dados);
  const roteador = createMemoryRouter([{ path: "/perfil/orientacao", Component: Orientacao }], {
    initialEntries: ["/perfil/orientacao"],
  });
  render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
  return { usuario: userEvent.setup() };
}

function botaoSalvar(): HTMLElement {
  return screen.getByRole("button", { name: /Salvar orientação|Salvando…/ });
}

function caixaDeConsentimento(): HTMLElement {
  return screen.getByRole("checkbox", { name: "Concordo em guardar minha orientação no Duora" });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Orientacao", () => {
  it("semDadosComecaVazia", async () => {
    abrir(null);

    expect(await screen.findByRole("heading", { level: 1, name: "Orientação" })).toBeInTheDocument();
    expect(screen.getByText(AVISO)).toBeInTheDocument();
    const grupo = screen.getByRole("group", { name: "Sua orientação" });
    expect(
      within(grupo)
        .getAllByRole("radio")
        .map((opcao) => opcao.closest("label")?.textContent),
    ).toEqual([
      "Heterossexual",
      "Gay",
      "Lésbica",
      "Bissexual",
      "Pansexual",
      "Assexual",
      "Demissexual",
      "Queer",
      "Questionando",
      "Prefiro descrever",
    ]);
    expect(screen.getAllByRole("radio").filter((opcao) => (opcao as HTMLInputElement).checked)).toEqual([]);
    expect(screen.getByRole("checkbox", { name: "Mostrar para minhas conexões" })).not.toBeChecked();
    expect(caixaDeConsentimento()).not.toBeChecked();
    expect(screen.queryByText(/Você concordou em/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Apagar orientação" })).not.toBeInTheDocument();
  });

  it("comDadosVemMarcadaComAData", async () => {
    abrir(DADOS);

    await screen.findByRole("heading", { level: 1, name: "Orientação" });
    expect(screen.getByRole("radio", { name: "Bissexual" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Mostrar para minhas conexões" })).toBeChecked();
    expect(caixaDeConsentimento()).toBeChecked();
    // 02h30 UTC do dia 28 ainda é dia 27 em São Paulo.
    expect(caixaDeConsentimento()).toHaveAccessibleDescription("Você concordou em 27 de novembro de 2026.");
  });

  it("orientacaoPedeConsentimento", async () => {
    const { pedidos } = prepararFetch({});
    const { usuario } = abrir(null);

    await usuario.click(await screen.findByRole("radio", { name: "Gay" }));

    expect(botaoSalvar()).toHaveAttribute("aria-disabled", "true");
    expect(botaoSalvar()).toHaveAccessibleDescription("Marque a caixa de consentimento para salvar.");
    await usuario.click(botaoSalvar());
    expect(pedidos).toEqual([]);

    await usuario.click(caixaDeConsentimento());
    expect(botaoSalvar()).not.toHaveAttribute("aria-disabled");
    expect(screen.queryByText("Marque a caixa de consentimento para salvar.")).not.toBeInTheDocument();
  });

  it("prefiroDescreverPedeTextoDeAte40", async () => {
    const { pedidos } = prepararFetch({});
    const { usuario } = abrir(null);
    expect(screen.queryByRole("textbox", { name: "Como você descreve sua orientação" })).not.toBeInTheDocument();

    await usuario.click(await screen.findByRole("radio", { name: "Prefiro descrever" }));
    const campo = screen.getByRole("textbox", { name: "Como você descreve sua orientação" });
    expect(campo).toHaveAccessibleDescription("Até 40 caracteres.");
    await usuario.type(campo, "a".repeat(41));
    await usuario.click(caixaDeConsentimento());
    await usuario.click(botaoSalvar());

    expect(campo).toHaveFocus();
    expect(campo).toHaveAttribute("aria-invalid", "true");
    expect(campo).toHaveAccessibleDescription(
      "Até 40 caracteres. Escreva como você descreve sua orientação, com até 40 caracteres.",
    );
    expect(pedidos).toEqual([]);
  });

  it("mostrarVaiNoCorpo", async () => {
    const salvos: DadosDeOrientacao = { ...DADOS, orientacao: "GAY" };
    const { corposDoPut } = prepararFetch({ "PUT /api/perfil/orientacao": { status: 200, corpo: salvos } });
    const { usuario } = abrir(null);

    await usuario.click(await screen.findByRole("radio", { name: "Gay" }));
    await usuario.click(screen.getByRole("checkbox", { name: "Mostrar para minhas conexões" }));
    await usuario.click(caixaDeConsentimento());
    await usuario.click(botaoSalvar());

    expect(await screen.findByText("Orientação salva.")).toBeInTheDocument();
    expect(corposDoPut).toEqual([
      { orientacao: "GAY", descricaoOrientacao: null, mostrarOrientacao: true, consentimento: true },
    ]);
  });

  it("apagarChamaDeleteEMostraStatus", async () => {
    const { pedidos } = prepararFetch({
      "DELETE /api/perfil/orientacao": { status: 204 },
      "GET /api/perfil/orientacao": { status: 200, corpo: SEM_DADOS },
    });
    const { usuario } = abrir(DADOS);

    await usuario.click(await screen.findByRole("button", { name: "Apagar orientação" }));

    const status = await screen.findByText("Orientação apagada.");
    expect(status).toHaveAttribute("role", "status");
    expect(status).toHaveFocus();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(pedidos).toContain("DELETE /api/perfil/orientacao");
    // A releitura confirma o apagamento no servidor.
    await waitFor(() => {
      expect(pedidos).toContain("GET /api/perfil/orientacao");
    });
    expect(screen.getByRole("radio", { name: "Bissexual" })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Mostrar para minhas conexões" })).not.toBeChecked();
    expect(caixaDeConsentimento()).not.toBeChecked();
    expect(screen.queryByText(/Você concordou em/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Apagar orientação" })).not.toBeInTheDocument();
  });
});
