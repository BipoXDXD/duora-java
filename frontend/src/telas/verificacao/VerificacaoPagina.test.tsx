import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect, useState, type ReactElement } from "react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { SituacaoDaVerificacao } from "../../api/verificacao";
import { consultaDaConta } from "../../rotas/consultas";
import { criarRotas } from "../../rotas/rotas";
import { stubarFetch, VERSOES_LEGAIS } from "../../teste/fetchFalso";
import { textoDeEspera } from "../conta/textos";
import type { ResultadoDaCamera } from "./camera";
import { VERIFICACAO_DE_IDADE } from "./textos";
import { VerificacaoPagina, type DadosDaVerificacao } from "./VerificacaoPagina";

/** O Core do Amplify abriria a câmera: o teste guarda as props e chama os callbacks à mão. */
const coreDoAmplify = vi.hoisted(() => ({
  onError: undefined as ((erro: { state: string; error: Error }) => void) | undefined,
  /** Faz o Core quebrar ao renderizar: o limite do CapturaDeVida vira a falha "carregamento". */
  quebrar: false,
}));

vi.mock("@aws-amplify/ui-react-liveness", () => ({
  FaceLivenessDetectorCore: (props: { onError: (erro: { state: string; error: Error }) => void }) => {
    if (coreDoAmplify.quebrar) {
      throw new Error("chunk quebrado");
    }
    coreDoAmplify.onError = props.onError;
    return <p>detector amplify</p>;
  },
}));

const CONTA = { estado: "AGUARDANDO_VERIFICACAO", cadastroConcluido: false, verificada: false } as const;
const SITUACAO: SituacaoDaVerificacao = {
  estado: "AGUARDANDO",
  tentativasRestantesHoje: 3,
  enviosDeDocumentoRestantesHoje: 2,
  modo: "falso",
};
const ROTA_DA_SESSAO = "/api/conta/verificacao/sessao";
const ROTA_DO_RESULTADO = "/api/conta/verificacao/sessao/abc/resultado";

function desfecho(campos: Record<string, unknown> = {}) {
  return json(200, {
    resultado: "APROVADA",
    pedeDocumento: false,
    tentativasRestantesHoje: 2,
    enviosDeDocumentoRestantesHoje: 2,
    ...campos,
  });
}

/** Valores que nunca podem sair da memória: nem storage, nem DOM, nem cache de mutação. */
const CREDENCIAL = {
  accessKeyId: "ASIACHAVEDEACESSO",
  secretAccessKey: "segredo-da-credencial-curta",
  sessionToken: "token-da-sessao-curta",
  expiraEm: "2026-11-27T12:15:00Z",
};
const SESSAO_CRIADA = { sessaoId: "abc", regiao: "us-east-1", modo: "falso", credencial: CREDENCIAL };

function json(status: number, corpo: unknown, cabecalhos: Record<string, string> = {}): Response {
  const tipo = status >= 400 ? "application/problem+json" : "application/json";
  return new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": tipo, ...cabecalhos } });
}

function problema(status: number, codigo: string, cabecalhos: Record<string, string> = {}): Response {
  return json(status, { codigo, mensagem: "Texto do servidor." }, cabecalhos);
}

/** Uma promessa que o teste resolve na hora que quiser. */
function adiada<T>() {
  let resolver: (valor: T) => void = () => undefined;
  const promessa = new Promise<T>((r) => {
    resolver = r;
  });
  return { promessa, resolver };
}

/** Responde a conta e a situação da verificação; as versões legais vêm do stubarFetch. */
function prepararFetch(situacao: Response) {
  stubarFetch((entrada) => {
    if (entrada === "/api/conta") {
      return Promise.resolve(json(200, CONTA));
    }
    if (entrada === "/api/conta/verificacao") {
      return Promise.resolve(situacao);
    }
    return Promise.reject(new Error("pedido inesperado"));
  });
}

/** A casca inteira, aberta como documento próprio da verificação. */
function abrirAVerificacao() {
  const clienteDeConsulta = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rotas = criarRotas({ clienteDeConsulta, tomarTokenDoLink: () => null, caminhoDaCarga: "/verificacao" });
  const roteador = createMemoryRouter(rotas, { initialEntries: ["/verificacao"] });
  render(
    <QueryClientProvider client={clienteDeConsulta}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
}

interface OpcoesDaPagina {
  situacao?: Partial<SituacaoDaVerificacao>;
  /** As respostas do POST da sessão, na ordem; sem mais respostas, o pedido fica sem resposta. */
  sessoes?: Response[];
  /** As respostas do POST do resultado da selfie, na ordem; sem mais respostas, fica sem resposta. */
  resultados?: Response[];
  checarCamera?: () => Promise<ResultadoDaCamera>;
  temSuporte?: boolean;
}

interface PropsDaPaginaRecarregavel {
  checarCamera: () => Promise<ResultadoDaCamera>;
  navegarParaPagina: (url: string) => void;
  temSuporte: boolean;
  expor: (recarregar: () => void) => void;
}

/**
 * A página sob um pai que o teste pode renderizar de novo: depois da recarga, cada render entrega uma
 * checagem de câmera nova, e o efeito da etapa roda outra vez na mesma etapa.
 */
function PaginaRecarregavel({
  checarCamera,
  navegarParaPagina,
  temSuporte,
  expor,
}: PropsDaPaginaRecarregavel): ReactElement {
  const [recargas, setRecargas] = useState(0);
  useEffect(() => {
    expor(() => {
      setRecargas((n) => n + 1);
    });
  }, [expor]);
  return (
    <VerificacaoPagina
      navegarParaPagina={navegarParaPagina}
      checarCamera={recargas === 0 ? checarCamera : () => checarCamera()}
      temSuporte={() => temSuporte}
    />
  );
}

/** Só a página, com o loader já resolvido e a câmera, o suporte e a navegação injetados. */
function abrirAPagina({
  situacao = {},
  sessoes = [],
  resultados = [],
  checarCamera,
  temSuporte = true,
}: OpcoesDaPagina = {}) {
  const fila = [...sessoes];
  const filaDeResultados = [...resultados];
  const fetchFalso = stubarFetch((entrada) => {
    if (entrada === ROTA_DA_SESSAO) {
      const resposta = fila.shift();
      return resposta === undefined ? new Promise<Response>(() => undefined) : Promise.resolve(resposta);
    }
    if (entrada === ROTA_DO_RESULTADO) {
      const resposta = filaDeResultados.shift();
      return resposta === undefined ? new Promise<Response>(() => undefined) : Promise.resolve(resposta);
    }
    return Promise.reject(new Error("pedido inesperado"));
  });
  const camera = vi.fn(checarCamera ?? (() => Promise.resolve<ResultadoDaCamera>("ok")));
  const navegarParaPagina = vi.fn<(url: string) => void>();
  const clienteDeConsulta = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  let recarregarPai: () => void = () => undefined;
  const dados: DadosDaVerificacao = { conta: CONTA, situacao: { ...SITUACAO, ...situacao }, legal: VERSOES_LEGAIS };
  const roteador = createMemoryRouter(
    [
      {
        path: "/verificacao",
        loader: () => dados,
        HydrateFallback: () => null,
        element: (
          <PaginaRecarregavel
            navegarParaPagina={navegarParaPagina}
            checarCamera={camera}
            temSuporte={temSuporte}
            expor={(fn) => {
              recarregarPai = fn;
            }}
          />
        ),
      },
    ],
    { initialEntries: ["/verificacao"] },
  );
  render(
    <QueryClientProvider client={clienteDeConsulta}>
      <RouterProvider router={roteador} />
    </QueryClientProvider>,
  );
  const pedidosDeSessao = () => fetchFalso.mock.calls.filter(([entrada]) => entrada === ROTA_DA_SESSAO).length;
  const pedidosDeResultado = () => fetchFalso.mock.calls.filter(([entrada]) => entrada === ROTA_DO_RESULTADO).length;
  /** Renderiza o pai de novo e espera uma macrotarefa: o POST da mutação sai depois de algumas microtarefas. */
  const recarregar = async () => {
    await act(async () => {
      recarregarPai();
      await new Promise((resolver) => setTimeout(resolver, 0));
    });
  };
  return { camera, navegarParaPagina, clienteDeConsulta, pedidosDeSessao, pedidosDeResultado, recarregar };
}

async function consentirEComecar(usuario: ReturnType<typeof userEvent.setup>) {
  await usuario.click(
    await screen.findByRole("checkbox", { name: "Concordo com o uso da minha imagem para confirmar minha idade" }),
  );
  await usuario.click(screen.getByRole("button", { name: "Começar a selfie" }));
}

/** Do consentimento até o fim da selfie falsa: a página vai para a conferência. */
async function simularASelfie(usuario: ReturnType<typeof userEvent.setup>) {
  await consentirEComecar(usuario);
  await usuario.click(await screen.findByRole("button", { name: "Simular captura" }));
}

/** Chaves e valores do storage, numa linha. */
function conteudoDo(storage: Storage): string {
  const itens: (string | null)[] = [];
  for (let i = 0; i < storage.length; i++) {
    const chave = storage.key(i);
    itens.push(chave, chave === null ? null : storage.getItem(chave));
  }
  return JSON.stringify(itens);
}

function titulo() {
  return screen.getByRole("heading", { level: 1, name: "Confirme sua idade" });
}

afterEach(() => {
  coreDoAmplify.onError = undefined;
  coreDoAmplify.quebrar = false;
  vi.unstubAllGlobals();
  localStorage.clear();
  sessionStorage.clear();
});

describe("VerificacaoPagina", () => {
  it("mostraOTituloEAAba", async () => {
    prepararFetch(json(200, SITUACAO));

    abrirAVerificacao();

    expect(await screen.findByRole("heading", { level: 1, name: "Confirme sua idade" })).toBeInTheDocument();
    expect(document.title).toBe("Confirmar idade · Duora");
  });

  it("falhaNaLeituraVaiParaOErroDaCasca", async () => {
    prepararFetch(json(503, { codigo: "INDISPONIVEL", mensagem: "Fora do ar." }));

    abrirAVerificacao();

    expect(
      await screen.findByRole("heading", { level: 1, name: "Não conseguimos abrir esta página" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Confirme sua idade" })).not.toBeInTheDocument();
  });

  it("modoAwsChecaACameraAntesDeCriarASessao", async () => {
    const usuario = userEvent.setup();
    const camera = adiada<ResultadoDaCamera>();
    const { pedidosDeSessao } = abrirAPagina({ situacao: { modo: "aws" }, checarCamera: () => camera.promessa });

    await consentirEComecar(usuario);

    expect(screen.getByRole("status")).toHaveTextContent("Abrindo a câmera…");
    expect(pedidosDeSessao()).toBe(0);

    camera.resolver("ok");

    await waitFor(() => {
      expect(pedidosDeSessao()).toBe(1);
    });
    expect(screen.getByRole("status")).toHaveTextContent("Preparando a selfie…");
  });

  it("modoFalsoNaoChecaCamera", async () => {
    const usuario = userEvent.setup();
    const { camera, pedidosDeSessao } = abrirAPagina({ situacao: { modo: "falso" } });

    await consentirEComecar(usuario);

    await waitFor(() => {
      expect(pedidosDeSessao()).toBe(1);
    });
    expect(camera).not.toHaveBeenCalled();
  });

  it("cameraNegadaMostraComoReativarETentaDeNovo", async () => {
    const usuario = userEvent.setup();
    const respostas: ResultadoDaCamera[] = ["negada", "ok"];
    const { camera, pedidosDeSessao } = abrirAPagina({
      situacao: { modo: "aws" },
      checarCamera: () => Promise.resolve(respostas.shift() ?? "ok"),
    });

    await consentirEComecar(usuario);

    expect(
      await screen.findByText(
        "Não conseguimos usar a câmera. Libere o acesso nas configurações do navegador e toque em Tentar de novo.",
      ),
    ).toBeInTheDocument();
    expect(pedidosDeSessao()).toBe(0);

    await usuario.click(screen.getByRole("button", { name: "Tentar de novo" }));

    await waitFor(() => {
      expect(pedidosDeSessao()).toBe(1);
    });
    expect(camera).toHaveBeenCalledTimes(2);
  });

  it("cliqueDuploIniciaUmaSessaoSo", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeSessao } = abrirAPagina();
    await usuario.click(
      await screen.findByRole("checkbox", { name: "Concordo com o uso da minha imagem para confirmar minha idade" }),
    );

    await usuario.dblClick(screen.getByRole("button", { name: "Começar a selfie" }));

    await screen.findByText("Preparando a selfie…");
    expect(pedidosDeSessao()).toBe(1);
  });

  it("efeitoRepetidoEmIniciandoFazUmPostSo", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeSessao, recarregar } = abrirAPagina();
    await consentirEComecar(usuario);
    await screen.findByText("Preparando a selfie…");

    await recarregar();
    await recarregar();

    expect(screen.getByText("Preparando a selfie…")).toBeInTheDocument();
    expect(pedidosDeSessao()).toBe(1);
  });

  it("cameraQueFalhaViraCameraNegada", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeSessao } = abrirAPagina({
      situacao: { modo: "aws" },
      checarCamera: () => Promise.reject(new Error("falha inesperada")),
    });

    await consentirEComecar(usuario);

    expect(
      await screen.findByText(
        "Não conseguimos usar a câmera. Libere o acesso nas configurações do navegador e toque em Tentar de novo.",
      ),
    ).toBeInTheDocument();
    expect(pedidosDeSessao()).toBe(0);
  });

  it("esperaFalaForaDoTrechoOcupado", async () => {
    const usuario = userEvent.setup();
    abrirAPagina();

    await consentirEComecar(usuario);

    const espera = await screen.findByRole("status");
    expect(espera).toHaveTextContent("Preparando a selfie…");
    expect(document.querySelector("[aria-busy='true']")).not.toBeNull();
    expect(espera.closest("[aria-busy='true']")).toBeNull();
  });

  it("servicoForaMostraTentarDeNovoQueRepete", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeSessao } = abrirAPagina({ sessoes: [problema(503, "PROVA_DE_VIDA_INDISPONIVEL")] });

    await consentirEComecar(usuario);

    expect(
      await screen.findByText("O serviço de verificação não respondeu. Tente de novo em instantes."),
    ).toBeInTheDocument();

    await usuario.click(screen.getByRole("button", { name: "Tentar de novo" }));

    await waitFor(() => {
      expect(pedidosDeSessao()).toBe(2);
    });
  });

  it("limiteMostraTextoDeEspera", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({ sessoes: [problema(429, "LIMITE_DE_TAXA", { "Retry-After": "120" })] });

    await consentirEComecar(usuario);

    expect(await screen.findByText("Muitas tentativas. Tente de novo em 2 minutos.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Começar a selfie" })).not.toBeInTheDocument();
  });

  it("limiteDeTaxaSemRetryAfterNaoFalaEmAmanha", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({ sessoes: [problema(429, "LIMITE_DE_TAXA")] });

    await consentirEComecar(usuario);

    expect(await screen.findByText("Muitas tentativas. Tente de novo em 1 minuto.")).toBeInTheDocument();
    expect(screen.queryByText(/amanhã/)).not.toBeInTheDocument();
  });

  it("limiteDeVerificacaoMostraOLimiteDoDia", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({ sessoes: [problema(429, "LIMITE_DE_VERIFICACAO", { "Retry-After": "120" })] });

    await consentirEComecar(usuario);

    expect(
      await screen.findByText("Você chegou ao limite de tentativas de hoje. Tente de novo amanhã."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/minutos/)).not.toBeInTheDocument();
  });

  it("zeroTentativasAbreNoLimite", async () => {
    abrirAPagina({ situacao: { tentativasRestantesHoje: 0 } });

    expect(
      await screen.findByText("Você chegou ao limite de tentativas de hoje. Tente de novo amanhã."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Começar a selfie" })).not.toBeInTheDocument();
  });

  it("foraDeOrdemRecarregaPelaConta", async () => {
    const usuario = userEvent.setup();
    const { navegarParaPagina } = abrirAPagina({ sessoes: [problema(409, "VERIFICACAO_FORA_DE_ORDEM")] });

    await consentirEComecar(usuario);

    await waitFor(() => {
      expect(navegarParaPagina).toHaveBeenCalledExactlyOnceWith("/conta");
    });
  });

  it("aprovadaVoltaParaAConta", async () => {
    const { navegarParaPagina } = abrirAPagina({ situacao: { estado: "APROVADA" } });

    await waitFor(() => {
      expect(navegarParaPagina).toHaveBeenCalledExactlyOnceWith("/conta");
    });
    expect(screen.queryByRole("button", { name: "Começar a selfie" })).not.toBeInTheDocument();
  });

  it("focoVaiParaOTituloDeCadaEtapa", async () => {
    const usuario = userEvent.setup();
    const respostas: ResultadoDaCamera[] = ["negada", "ausente"];
    abrirAPagina({ situacao: { modo: "aws" }, checarCamera: () => Promise.resolve(respostas.shift() ?? "ok") });

    await consentirEComecar(usuario);
    await screen.findByText(/Libere o acesso/);

    expect(titulo()).toHaveFocus();

    await usuario.click(screen.getByRole("button", { name: "Tentar de novo" }));
    await screen.findByText("Não encontramos uma câmera neste aparelho. Use um celular ou computador com câmera.");

    expect(titulo()).toHaveFocus();
  });

  it("sessaoCriadaMostraODetectorDoModo", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({ sessoes: [json(201, SESSAO_CRIADA)] });

    await consentirEComecar(usuario);

    expect(await screen.findByRole("region", { name: "Selfie em vídeo" })).toBeInTheDocument();
    expect(await screen.findByText("Modo de teste: nenhuma câmera é usada.")).toBeInTheDocument();
  });

  it("simularCapturaVaiParaConferindo", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeSessao } = abrirAPagina({ sessoes: [json(201, SESSAO_CRIADA)] });

    await consentirEComecar(usuario);
    await usuario.click(await screen.findByRole("button", { name: "Simular captura" }));

    expect(screen.queryByRole("region", { name: "Selfie em vídeo" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Começar a selfie" })).not.toBeInTheDocument();
    expect(titulo()).toHaveFocus();
    expect(pedidosDeSessao()).toBe(1);
  });

  it("falhaDoDetectorSaiDaCaptura", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({ situacao: { modo: "aws" }, sessoes: [json(201, { ...SESSAO_CRIADA, modo: "aws" })] });

    await consentirEComecar(usuario);
    await screen.findByText("detector amplify");
    act(() => {
      coreDoAmplify.onError?.({ state: "MULTIPLE_FACES_ERROR", error: new Error("dois rostos") });
    });

    expect(screen.queryByRole("region", { name: "Selfie em vídeo" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Começar a selfie" })).not.toBeInTheDocument();
    expect(titulo()).toHaveFocus();
  });

  it("cancelarVoltaParaExplicacao", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeSessao } = abrirAPagina({ sessoes: [json(201, SESSAO_CRIADA)] });

    await consentirEComecar(usuario);
    await usuario.click(await screen.findByRole("button", { name: "Cancelar" }));

    expect(screen.getByRole("button", { name: "Começar a selfie" })).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Selfie em vídeo" })).not.toBeInTheDocument();
    expect(pedidosDeSessao()).toBe(1);
  });

  it("conferindoAnunciaEspera", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeResultado } = abrirAPagina({ sessoes: [json(201, SESSAO_CRIADA)] });

    await simularASelfie(usuario);

    expect(await screen.findByText("Conferindo a selfie…")).toHaveAttribute("role", "status");
    expect(pedidosDeResultado()).toBe(1);
  });

  it("aprovadaMostraContinuarParaOTerraco", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({ sessoes: [json(201, SESSAO_CRIADA)], resultados: [desfecho()] });

    await simularASelfie(usuario);

    expect(await screen.findByRole("heading", { level: 2, name: "Idade confirmada" })).toHaveFocus();
    expect(screen.getByText("Confirmamos que você tem 18 anos ou mais. Sua conta está ativa.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Continuar" })).toHaveAttribute("href", "/");
  });

  it("desfechoAtualizaASituacaoDaVerificacao", async () => {
    const usuario = userEvent.setup();
    const { clienteDeConsulta } = abrirAPagina({ sessoes: [json(201, SESSAO_CRIADA)], resultados: [desfecho()] });
    const invalidar = vi.spyOn(clienteDeConsulta, "invalidateQueries");

    await simularASelfie(usuario);
    await screen.findByRole("heading", { level: 2, name: "Idade confirmada" });

    expect(invalidar).toHaveBeenCalledWith({ queryKey: ["conta", "verificacao"] });
  });

  it("repetirMostraTentativasDoDesfecho", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeSessao } = abrirAPagina({
      sessoes: [json(201, SESSAO_CRIADA)],
      resultados: [desfecho({ resultado: "REPETIR", tentativasRestantesHoje: 1 })],
    });

    await simularASelfie(usuario);

    expect(await screen.findByRole("heading", { level: 2, name: "Não deu para confirmar" })).toBeInTheDocument();
    expect(screen.getByText("Procure um lugar claro, tire óculos escuros e tente de novo.")).toBeInTheDocument();
    expect(screen.getByText("Tentativas restantes hoje: 1")).toBeInTheDocument();

    await usuario.click(screen.getByRole("button", { name: "Tentar de novo" }));

    await waitFor(() => {
      expect(pedidosDeSessao()).toBe(2);
    });
  });

  it("repetirSemTentativasMostraOLimiteDoDia", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({
      sessoes: [json(201, SESSAO_CRIADA)],
      resultados: [desfecho({ resultado: "REPETIR", tentativasRestantesHoje: 0 })],
    });

    await simularASelfie(usuario);

    expect(await screen.findByText(VERIFICACAO_DE_IDADE.limiteDoDia)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tentar de novo" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar ao terraço" })).toHaveAttribute("href", "/");
  });

  it("menorMostraTextoFinalEEsqueceAConta", async () => {
    const usuario = userEvent.setup();
    const { clienteDeConsulta } = abrirAPagina({
      sessoes: [json(201, SESSAO_CRIADA)],
      resultados: [problema(422, "MENOR_DE_IDADE")],
    });
    clienteDeConsulta.setQueryData(consultaDaConta.queryKey, CONTA);

    await simularASelfie(usuario);

    expect(await screen.findByRole("heading", { level: 2, name: "Cadastro não concluído" })).toBeInTheDocument();
    expect(screen.getByText("O Duora é só para maiores de 18 anos. Encerramos o cadastro.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar ao terraço" })).toHaveAttribute("href", "/");
    expect(clienteDeConsulta.getQueryData(consultaDaConta.queryKey)).toBeUndefined();
  });

  it("analiseMostraOEmailDoEncarregado", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({
      sessoes: [json(201, SESSAO_CRIADA)],
      resultados: [desfecho({ resultado: "DUVIDA", pedeDocumento: false, enviosDeDocumentoRestantesHoje: 0 })],
    });

    await simularASelfie(usuario);

    expect(await screen.findByRole("heading", { level: 2, name: "Conta em análise" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: VERSOES_LEGAIS.encarregado.email })).toHaveAttribute(
      "href",
      `mailto:${VERSOES_LEGAIS.encarregado.email}`,
    );
    expect(screen.getByRole("link", { name: "Voltar ao terraço" })).toHaveAttribute("href", "/");
  });

  it("aberturaEmAnaliseNaoTomaOFoco", async () => {
    abrirAPagina({ situacao: { estado: "DUVIDA", enviosDeDocumentoRestantesHoje: 0 } });

    expect(await screen.findByRole("heading", { level: 2, name: "Conta em análise" })).not.toHaveFocus();
  });

  it("duvidaComDocumentoVaiParaOEnvio", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({
      sessoes: [json(201, SESSAO_CRIADA)],
      resultados: [desfecho({ resultado: "DUVIDA", pedeDocumento: true })],
    });

    await simularASelfie(usuario);

    expect(await screen.findByRole("heading", { level: 2, name: "Falta pouco" })).toBeInTheDocument();
  });

  it("sessaoInvalidaVoltaParaExplicacao", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({
      sessoes: [json(201, SESSAO_CRIADA)],
      resultados: [problema(404, "SESSAO_DE_VERIFICACAO_INVALIDA")],
    });

    await simularASelfie(usuario);
    expect(await screen.findByText("Esta selfie não vale mais. Comece de novo.")).toBeInTheDocument();
    await usuario.click(screen.getByRole("button", { name: "Tentar de novo" }));

    expect(screen.getByRole("button", { name: "Começar a selfie" })).toBeInTheDocument();
  });

  it("servicoForaRepeteAConferenciaSemNovaSessao", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeSessao, pedidosDeResultado } = abrirAPagina({
      sessoes: [json(201, SESSAO_CRIADA)],
      resultados: [problema(503, "PROVA_DE_VIDA_INDISPONIVEL"), desfecho()],
    });

    await simularASelfie(usuario);
    expect(await screen.findByText(VERIFICACAO_DE_IDADE.servicoFora)).toBeInTheDocument();
    await usuario.click(screen.getByRole("button", { name: "Tentar de novo" }));

    expect(await screen.findByRole("heading", { level: 2, name: "Idade confirmada" })).toBeInTheDocument();
    expect(pedidosDeResultado()).toBe(2);
    expect(pedidosDeSessao()).toBe(1);
  });

  it("limiteDeTaxaNaConferenciaMostraAEspera", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({
      sessoes: [json(201, SESSAO_CRIADA)],
      resultados: [problema(429, "LIMITE_DE_TAXA", { "Retry-After": "120" })],
    });

    await simularASelfie(usuario);

    expect(await screen.findByText(textoDeEspera(120))).toBeInTheDocument();
  });

  it("foraDeOrdemNaConferenciaRecarregaPelaConta", async () => {
    const usuario = userEvent.setup();
    const { navegarParaPagina } = abrirAPagina({
      sessoes: [json(201, SESSAO_CRIADA)],
      resultados: [problema(409, "VERIFICACAO_FORA_DE_ORDEM")],
    });

    await simularASelfie(usuario);

    await waitFor(() => {
      expect(navegarParaPagina).toHaveBeenCalledWith("/conta");
    });
  });

  it.each([
    ["CAMERA_ACCESS_ERROR", VERIFICACAO_DE_IDADE.cameraNegada],
    ["TIMEOUT", "O tempo da selfie acabou. Comece de novo quando puder."],
    ["FACE_DISTANCE_ERROR", "Não conseguimos ver seu rosto direito. Centralize o rosto, com boa luz, e tente de novo."],
    ["MULTIPLE_FACES_ERROR", "Só você pode aparecer na selfie. Peça para a outra pessoa sair do quadro."],
    ["CONNECTION_TIMEOUT", "A conexão caiu durante a selfie. Confira a internet e tente de novo."],
    ["SERVER_ERROR", VERIFICACAO_DE_IDADE.servicoFora],
    ["RUNTIME_ERROR", "Esta câmera ou este navegador não deu conta da selfie. Tente em outro aparelho."],
    ["MOBILE_LANDSCAPE_ERROR", "Deixe o celular em pé e tente de novo."],
    ["carregamento", "Não conseguimos carregar a selfie. Confira a conexão e tente de novo."],
  ])("falhaDoDetectorMostraTexto %s", async (estado, texto) => {
    const usuario = userEvent.setup();
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    coreDoAmplify.quebrar = estado === "carregamento";
    abrirAPagina({ situacao: { modo: "aws" }, sessoes: [json(201, { ...SESSAO_CRIADA, modo: "aws" })] });

    await consentirEComecar(usuario);
    if (estado !== "carregamento") {
      await screen.findByText("detector amplify");
      act(() => {
        coreDoAmplify.onError?.({ state: estado, error: new Error(estado) });
      });
    }

    expect(await screen.findByText(texto)).toHaveAttribute("role", "alert");
    expect(screen.getByText("Tentativas restantes hoje: 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });

  it("falhaSemTentativasMostraOLimiteDoDia", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({
      situacao: { modo: "aws", tentativasRestantesHoje: 1 },
      sessoes: [json(201, { ...SESSAO_CRIADA, modo: "aws" })],
    });

    await consentirEComecar(usuario);
    await screen.findByText("detector amplify");
    act(() => {
      coreDoAmplify.onError?.({ state: "TIMEOUT", error: new Error("tempo") });
    });

    expect(await screen.findByText(VERIFICACAO_DE_IDADE.limiteDoDia)).toHaveAttribute("role", "status");
    expect(screen.queryByRole("button", { name: "Tentar de novo" })).not.toBeInTheDocument();
  });

  it("tentarDeNovoDepoisDaFalhaAbreOutraSessao", async () => {
    const usuario = userEvent.setup();
    const { pedidosDeSessao, camera } = abrirAPagina({
      situacao: { modo: "aws" },
      sessoes: [json(201, { ...SESSAO_CRIADA, modo: "aws" })],
    });

    await consentirEComecar(usuario);
    await screen.findByText("detector amplify");
    act(() => {
      coreDoAmplify.onError?.({ state: "TIMEOUT", error: new Error("tempo") });
    });
    await usuario.click(await screen.findByRole("button", { name: "Tentar de novo" }));

    await waitFor(() => {
      expect(pedidosDeSessao()).toBe(2);
    });
    expect(camera).toHaveBeenCalledTimes(2);
  });

  it("cancelarMostraAsTentativasQueSobraram", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({ sessoes: [json(201, SESSAO_CRIADA)] });

    await consentirEComecar(usuario);
    await usuario.click(await screen.findByRole("button", { name: "Cancelar" }));

    expect(screen.getByText("Tentativas restantes hoje: 2")).toBeInTheDocument();
  });

  it("credencialNuncaVaiParaOStorage", async () => {
    const usuario = userEvent.setup();
    const { clienteDeConsulta } = abrirAPagina({ sessoes: [json(201, SESSAO_CRIADA)] });

    await consentirEComecar(usuario);
    await screen.findByRole("button", { name: "Simular captura" });

    const guardado = [
      conteudoDo(localStorage),
      conteudoDo(sessionStorage),
      JSON.stringify(
        clienteDeConsulta
          .getMutationCache()
          .getAll()
          .map((mutacao) => mutacao.state),
      ),
      JSON.stringify(
        clienteDeConsulta
          .getQueryCache()
          .getAll()
          .map((consulta) => consulta.state),
      ),
    ].join("\n");
    for (const valor of [CREDENCIAL.accessKeyId, CREDENCIAL.secretAccessKey, CREDENCIAL.sessionToken]) {
      expect(guardado).not.toContain(valor);
    }
  });

  it("credencialNaoApareceNoDom", async () => {
    const usuario = userEvent.setup();
    abrirAPagina({ sessoes: [json(201, SESSAO_CRIADA)] });

    await consentirEComecar(usuario);
    await screen.findByRole("button", { name: "Simular captura" });

    const html = document.documentElement.outerHTML;
    for (const valor of [CREDENCIAL.accessKeyId, CREDENCIAL.secretAccessKey, CREDENCIAL.sessionToken]) {
      expect(html).not.toContain(valor);
    }
  });
});
