import { QueryClient, QueryClientProvider, useSuspenseQuery } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useRef, type ReactElement } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { MeuPerfil as Perfil } from "../../api/perfil";
import { consultaDoMeuPerfil } from "../../rotas/consultas";
import { FaixaDaPausa, PausaDaConta } from "./PausaDaConta";

const PERFIL: Perfil = {
  versao: 2,
  apelido: "Lua",
  pronomes: null,
  descricaoPronomes: null,
  intencoes: ["AMIZADE"],
  bio: "",
  pausada: false,
  prontoParaFila: true,
  pendencias: [],
  foto: { aprovada: null, pendente: null, recusadaEm: null },
};

/** Registra os pedidos à pausa e responde 204; o GET do perfil responde `depois`. */
function prepararFetch(depois: Perfil) {
  const pedidos: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      if (entrada === "/api/perfil/pausa") {
        pedidos.push(opcoes?.method ?? "GET");
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (entrada === "/api/perfil") {
        return Promise.resolve(Response.json(depois));
      }
      return Promise.reject(new Error("pedido inesperado"));
    }),
  );
  return pedidos;
}

/** A faixa fica no topo de Meu perfil; aqui, o mínimo que junta as duas partes, lendo o cache. */
function Tela(): ReactElement {
  const { data } = useSuspenseQuery({ ...consultaDoMeuPerfil, refetchOnMount: false });
  const { pausada } = data;
  const faixa = useRef<HTMLParagraphElement>(null);
  return (
    <>
      <FaixaDaPausa ref={faixa} pausada={pausada} />
      <PausaDaConta pausada={pausada} faixa={faixa} />
    </>
  );
}

function renderizar(pausada: boolean) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  cliente.setQueryData(["perfil"], { ...PERFIL, pausada });
  render(
    <QueryClientProvider client={cliente}>
      <Tela />
    </QueryClientProvider>,
  );
  return { cliente };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("PausaDaConta", () => {
  it("pausarMostraOTextoExato", () => {
    prepararFetch(PERFIL);
    renderizar(false);

    expect(screen.getByRole("heading", { name: "Pausar a conta" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Enquanto a conta estiver pausada, você não entra na fila nem recebe avisos de pessoas compatíveis. Suas conexões e conversas continuam.",
      ),
    ).toBeInTheDocument();
  });

  it("pausarChamaPost", async () => {
    const pedidos = prepararFetch({ ...PERFIL, pausada: true });
    const { cliente } = renderizar(false);

    await userEvent.setup().click(screen.getByRole("button", { name: "Pausar" }));

    await waitFor(() => {
      expect(pedidos).toEqual(["POST"]);
    });
    await waitFor(() => {
      expect(cliente.getQueryData<Perfil>(["perfil"])?.pausada).toBe(true);
    });
  });

  it("pausadaMostraFaixaEVoltarAAparecer", () => {
    prepararFetch(PERFIL);
    renderizar(true);

    expect(screen.getByRole("status")).toHaveTextContent("Sua conta está pausada.");
    expect(screen.getByRole("button", { name: "Voltar a aparecer" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Pausar" })).not.toBeInTheDocument();
  });

  it("voltarChamaDelete", async () => {
    const pedidos = prepararFetch(PERFIL);
    renderizar(true);

    await userEvent.setup().click(screen.getByRole("button", { name: "Voltar a aparecer" }));

    await waitFor(() => {
      expect(pedidos).toEqual(["DELETE"]);
    });
  });
});
