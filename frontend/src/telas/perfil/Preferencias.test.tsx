import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PreferenciasDeEncontro, SituacaoDaLocalizacao } from "../../api/perfil";
import { Preferencias } from "./Preferencias";

const SEM_LIMITE: PreferenciasDeEncontro = {
  versao: 2,
  idadeMinima: 25,
  idadeMaxima: 40,
  raio: "SEM_LIMITE",
  raioObrigatorio: false,
};

const COM_RAIO: PreferenciasDeEncontro = { ...SEM_LIMITE, raio: "ATE_20_KM", raioObrigatorio: true };

const INATIVA: SituacaoDaLocalizacao = { consentida: false, atualizadaEm: null };
const ATIVA: SituacaoDaLocalizacao = { consentida: true, atualizadaEm: "2026-11-28T12:00:00Z" };

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

/** Responde cada pedido por "MÉTODO /caminho"; os corpos dos PUT ficam guardados, na ordem. */
function prepararFetch(respostas: Record<string, RespostaFalsa | (() => RespostaFalsa)>) {
  const corposDoPut: Record<string, unknown>[] = [];
  const pedidos: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      const chave = `${opcoes?.method ?? "GET"} ${typeof entrada === "string" ? entrada : "?"}`;
      pedidos.push(chave);
      if (opcoes?.method === "PUT") {
        corposDoPut.push(JSON.parse(typeof opcoes.body === "string" ? opcoes.body : "null") as Record<string, unknown>);
      }
      const resposta = respostas[chave];
      if (resposta === undefined) {
        return Promise.reject(new Error(`pedido inesperado: ${chave}`));
      }
      return Promise.resolve(json(typeof resposta === "function" ? resposta() : resposta));
    }),
  );
  return { corposDoPut, pedidos };
}

function renderizar(preferencias: PreferenciasDeEncontro, localizacao: SituacaoDaLocalizacao) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  cliente.setQueryData(["perfil", "preferencias"], preferencias);
  cliente.setQueryData(["perfil", "localizacao"], localizacao);
  render(
    <QueryClientProvider client={cliente}>
      <Preferencias />
    </QueryClientProvider>,
  );
  return { cliente };
}

function opcao(nome: string): HTMLInputElement {
  return screen.getByRole("radio", { name: nome });
}

function salvar(): HTMLElement {
  return screen.getByRole("button", { name: /Salvar preferências|Salvando…/ });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Preferencias", () => {
  it("raioDesabilitadoSemLocalizacao", async () => {
    prepararFetch({});
    renderizar(SEM_LIMITE, INATIVA);
    const usuario = userEvent.setup();

    for (const nome of ["Até 5 km", "Até 20 km", "Até 50 km"]) {
      expect(opcao(nome)).toHaveAttribute("aria-disabled", "true");
      expect(opcao(nome)).toHaveAccessibleDescription("Ative a localização para escolher uma distância.");
    }
    expect(opcao("Sem limite")).not.toHaveAttribute("aria-disabled");
    expect(opcao("Sem limite")).not.toBeDisabled();

    await usuario.click(opcao("Até 20 km"));

    expect(opcao("Até 20 km")).not.toBeChecked();
    expect(opcao("Sem limite")).toBeChecked();
  });

  it("comoUsarSoComRaio", async () => {
    prepararFetch({});
    renderizar(SEM_LIMITE, ATIVA);
    const usuario = userEvent.setup();
    expect(screen.queryByRole("group", { name: "Como usar a distância" })).not.toBeInTheDocument();

    await usuario.click(opcao("Até 5 km"));

    expect(screen.getByRole("group", { name: "Como usar a distância" })).toBeInTheDocument();
    expect(opcao("Só pessoas dentro do raio")).toBeInTheDocument();
    expect(opcao("Prefiro quem está mais perto, mas aceito mais longe")).toBeInTheDocument();
  });

  it("semLimiteMandaObrigatorioFalso", async () => {
    const { corposDoPut } = prepararFetch({
      "PUT /api/perfil/preferencias": { status: 200, corpo: { ...SEM_LIMITE, versao: 3 } },
    });
    renderizar(COM_RAIO, ATIVA);
    const usuario = userEvent.setup();
    expect(opcao("Só pessoas dentro do raio")).toBeChecked();

    await usuario.click(opcao("Sem limite"));
    await usuario.click(salvar());

    expect(await screen.findByText("Preferências salvas.")).toBeInTheDocument();
    expect(corposDoPut).toEqual([
      { versao: 2, idadeMinima: 25, idadeMaxima: 40, raio: "SEM_LIMITE", raioObrigatorio: false },
    ]);
  });

  it("versaoObsoletaRecarregaAsPreferencias", async () => {
    const { pedidos } = prepararFetch({
      "PUT /api/perfil/preferencias": {
        status: 409,
        corpo: { status: 409, codigo: "VERSAO_OBSOLETA", mensagem: "Seu perfil mudou enquanto você editava." },
      },
      "GET /api/perfil/preferencias": { status: 200, corpo: { ...SEM_LIMITE, versao: 5, idadeMinima: 30 } },
    });
    renderizar(SEM_LIMITE, INATIVA);
    const usuario = userEvent.setup();

    await usuario.click(salvar());

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Suas preferências mudaram em outro aparelho. Confira e salve de novo.",
    );
    expect(pedidos).toContain("GET /api/perfil/preferencias");
    expect(screen.getByRole("spinbutton", { name: "De" })).toHaveValue(30);
  });

  it("idadeDeMaiorQueAteRecusada", async () => {
    const { corposDoPut } = prepararFetch({});
    renderizar(SEM_LIMITE, INATIVA);
    const usuario = userEvent.setup();
    const de = screen.getByRole("spinbutton", { name: "De" });

    await usuario.clear(de);
    await usuario.type(de, "50");
    await usuario.click(salvar());

    expect(screen.getByText('Use idades de 18 a 99, com "De" menor ou igual a "Até".')).toBeInTheDocument();
    expect(de).toHaveAttribute("aria-invalid", "true");
    expect(de).toHaveFocus();
    expect(corposDoPut).toHaveLength(0);
  });

  it("localizacaoNecessariaVoltaParaSemLimiteComAMensagem", async () => {
    prepararFetch({
      "PUT /api/perfil/preferencias": {
        status: 422,
        corpo: { status: 422, codigo: "LOCALIZACAO_NECESSARIA", mensagem: "Ative a localização para usar um raio." },
      },
    });
    renderizar(COM_RAIO, ATIVA);
    const usuario = userEvent.setup();

    await usuario.click(salvar());

    expect(await screen.findByText("Ative a localização para usar um raio.")).toBeInTheDocument();
    expect(opcao("Sem limite")).toBeChecked();
    expect(opcao("Até 20 km")).toHaveAttribute("aria-disabled", "true");
  });
});
