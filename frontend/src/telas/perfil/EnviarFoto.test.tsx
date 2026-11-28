import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FotoDoPerfil, MeuPerfil as Perfil } from "../../api/perfil";
import { EnviarFoto } from "./EnviarFoto";

const SEM_FOTO: FotoDoPerfil = { aprovada: null, pendente: null, recusadaEm: null };
const URLS = { nitida: "/api/fotos/p/nitida", borrada: "/api/fotos/p/borrada" };
const PENDENTE = { estado: "PENDENTE", url: "/api/fotos/p/nitida" };
const APROVADA = { estado: "APROVADA", url: "/api/fotos/p/nitida" };

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
  foto: SEM_FOTO,
};

interface Envio {
  chave: string | null;
  arquivo: FormDataEntryValue | null;
}

/**
 * O POST da foto responde `respostas` (uma por chamada, a última se repete); o DELETE responde
 * `remocao` e conta em `remocoes`.
 */
function prepararFetch(respostas: { status: number; corpo?: unknown }[], remocao = 204) {
  const envios: Envio[] = [];
  const remocoes: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((entrada, opcoes) => {
      if (entrada === "/api/perfil/foto" && opcoes?.method === "POST") {
        const cabecalhos = new Headers(opcoes.headers);
        const corpo = opcoes.body instanceof FormData ? opcoes.body : null;
        envios.push({ chave: cabecalhos.get("Idempotency-Key"), arquivo: corpo?.get("arquivo") ?? null });
        const { status, corpo: dados } = respostas[Math.min(envios.length - 1, respostas.length - 1)] ?? {
          status: 500,
        };
        const tipo = status >= 400 ? "application/problem+json" : "application/json";
        return Promise.resolve(
          new Response(JSON.stringify(dados ?? null), { status, headers: { "Content-Type": tipo } }),
        );
      }
      if (entrada === "/api/perfil/foto" && opcoes?.method === "DELETE") {
        remocoes.push("DELETE");
        return Promise.resolve(
          remocao === 204
            ? new Response(null, { status: 204 })
            : new Response(JSON.stringify({ codigo: "INTERNO" }), {
                status: remocao,
                headers: { "Content-Type": "application/problem+json" },
              }),
        );
      }
      if (entrada === "/api/perfil") {
        return Promise.resolve(Response.json(PERFIL));
      }
      return Promise.reject(new Error("pedido inesperado"));
    }),
  );
  return Object.assign(envios, { remocoes });
}

function renderizar(foto: FotoDoPerfil = SEM_FOTO) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  cliente.setQueryData(["perfil"], { ...PERFIL, foto });
  render(
    <QueryClientProvider client={cliente}>
      <EnviarFoto apelido="Lua" foto={foto} />
    </QueryClientProvider>,
  );
}

function jpeg(tamanho = 16): File {
  return new File([new Uint8Array(tamanho)], "foto.jpg", { type: "image/jpeg" });
}

function seletor(): HTMLInputElement {
  return screen.getByLabelText(/Escolher foto|Escolher outra/);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("EnviarFoto", () => {
  it("recusaAcimaDe5MBSemEnviar", async () => {
    const envios = prepararFetch([{ status: 202, corpo: PENDENTE }]);
    renderizar();

    await userEvent.setup().upload(seletor(), jpeg(5_242_881));

    expect(await screen.findByRole("alert")).toHaveTextContent("A foto pode ter até 5 MB. Escolha uma menor.");
    expect(screen.queryByRole("button", { name: "Enviar foto" })).not.toBeInTheDocument();
    expect(envios).toHaveLength(0);
  });

  it("previaAntesDeEnviar", async () => {
    const envios = prepararFetch([{ status: 202, corpo: PENDENTE }]);
    renderizar();

    await userEvent.setup().upload(seletor(), jpeg());

    const previa = await screen.findByRole("img", { name: "Prévia da foto escolhida" });
    expect(previa.getAttribute("src")).toMatch(/^data:image\/jpeg;base64,/);
    expect(screen.getByRole("button", { name: "Enviar foto" })).toBeInTheDocument();
    expect(screen.getByLabelText("Escolher outra")).toBeInTheDocument();
    expect(envios).toHaveLength(0);
  });

  it("enviaComIdempotencyKey", async () => {
    const envios = prepararFetch([{ status: 202, corpo: PENDENTE }]);
    renderizar();
    const usuario = userEvent.setup();

    await usuario.upload(seletor(), jpeg());
    await usuario.click(await screen.findByRole("button", { name: "Enviar foto" }));

    const enviada = await screen.findByText("Foto enviada. Ela aparece depois da aprovação.");
    expect(envios).toHaveLength(1);
    expect(envios[0]?.chave).toMatch(/^[A-Za-z0-9-]{8,64}$/);
    expect(envios[0]?.arquivo).toBeInstanceOf(Blob);
    expect(enviada).toHaveFocus();
  });

  it("aprovadaPelaModeracaoAvisaQueJaAparece", async () => {
    prepararFetch([{ status: 202, corpo: APROVADA }]);
    renderizar();
    const usuario = userEvent.setup();

    await usuario.upload(seletor(), jpeg());
    await usuario.click(await screen.findByRole("button", { name: "Enviar foto" }));

    expect(await screen.findByText("Foto aprovada. Ela já aparece no seu perfil.")).toHaveFocus();
  });

  it("semFotoNaoOfereceRemover", () => {
    prepararFetch([]);
    renderizar();

    expect(screen.queryByRole("button", { name: "Remover foto" })).not.toBeInTheDocument();
  });

  it("removerPedeConfirmacaoAntes", async () => {
    const envios = prepararFetch([]);
    renderizar({ aprovada: URLS, pendente: null, recusadaEm: null });
    const usuario = userEvent.setup();

    await usuario.click(screen.getByRole("button", { name: "Remover foto" }));

    expect(screen.getByRole("dialog", { name: "Remover a foto?" })).toBeInTheDocument();
    await usuario.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(envios.remocoes).toHaveLength(0);
  });

  it("removerConfirmadoApagaEAvisa", async () => {
    const envios = prepararFetch([]);
    renderizar({ aprovada: URLS, pendente: URLS, recusadaEm: null });
    const usuario = userEvent.setup();

    await usuario.click(screen.getByRole("button", { name: "Remover foto" }));
    await usuario.click(screen.getByRole("button", { name: "Remover" }));

    expect(await screen.findByText("Foto removida. Outras pessoas voltam a ver uma silhueta.")).toHaveFocus();
    expect(envios.remocoes).toHaveLength(1);
  });

  it("falhaAoRemoverAvisa", async () => {
    prepararFetch([], 503);
    renderizar({ aprovada: URLS, pendente: null, recusadaEm: null });
    const usuario = userEvent.setup();

    await usuario.click(screen.getByRole("button", { name: "Remover foto" }));
    await usuario.click(screen.getByRole("button", { name: "Remover" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Não conseguimos remover a foto agora. Tente de novo em instantes.",
    );
  });

  it("pendenteSemAprovadaMostraLegenda", () => {
    prepararFetch([]);
    renderizar({ aprovada: null, pendente: URLS, recusadaEm: null });

    expect(screen.getByText("Aguardando aprovação. Até lá, os outros veem uma silhueta.")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Foto de Lua" })).toHaveAttribute("src", URLS.nitida);
  });

  it("recusadaMostraAviso", () => {
    prepararFetch([]);
    renderizar({ aprovada: null, pendente: null, recusadaEm: "2026-11-27T12:00:00Z" });

    expect(screen.getByText("Sua última foto não foi aprovada.")).toBeInTheDocument();
  });

  it("politicaComTextoExato", () => {
    prepararFetch([]);
    renderizar();

    expect(
      screen.getByText(
        "Sem nudez, sexo, violência explícita ou conteúdo chocante. Toda foto passa por aprovação antes de aparecer.",
      ),
    ).toBeInTheDocument();
  });

  it("acceptListaSoJpegPngWebp", () => {
    prepararFetch([]);
    renderizar();

    expect(seletor()).toHaveAttribute("accept", "image/jpeg,image/png,image/webp");
    expect(seletor()).toHaveAttribute("type", "file");
  });

  it("indisponivelPermiteTentarDeNovoComAMesmaChave", async () => {
    const envios = prepararFetch([{ status: 503, corpo: { codigo: "PROCESSAMENTO_INDISPONIVEL" } }, { status: 202, corpo: PENDENTE }]);
    renderizar();
    const usuario = userEvent.setup();
    await usuario.upload(seletor(), jpeg());
    await usuario.click(await screen.findByRole("button", { name: "Enviar foto" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Não conseguimos enviar a foto agora. Tente de novo em instantes.",
    );

    await usuario.click(screen.getByRole("button", { name: "Tentar de novo" }));

    await waitFor(() => {
      expect(envios).toHaveLength(2);
    });
    expect(envios[1]?.chave).toBe(envios[0]?.chave);
  });
});
