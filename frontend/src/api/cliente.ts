// Transporte com a API do terraço (mesma origem). Não interpreta regras: devolve a visão
// ou lança ErroApi com o código do contrato.

import type { Visao } from "../dominio/visao";

interface DadosDoErro {
  status: number;
  codigo: string;
  motivo?: string | null;
  visao?: Visao | null;
  detalhe?: string;
}

export class ErroApi extends Error {
  readonly status: number;
  readonly codigo: string;
  readonly motivo: string | null;
  readonly visao: Visao | null;

  constructor({ status, codigo, motivo = null, visao = null, detalhe = "" }: DadosDoErro) {
    super(detalhe || codigo);
    this.name = "ErroApi";
    this.status = status;
    this.codigo = codigo;
    this.motivo = motivo;
    this.visao = visao;
  }

  /** Falha de rede ou do servidor: vale tentar de novo mais tarde. */
  get transitorio(): boolean {
    return this.status === 0 || (this.status >= 500 && this.codigo !== "LIMITE_SALAS");
  }
}

export interface Resposta {
  naoMudou: boolean;
  visao: Visao | null;
  etag: string | null;
}

export interface Api {
  criarSala: (apelido: string) => Promise<Resposta>;
  entrar: (convite: string, apelido: string) => Promise<Resposta>;
  obterSala: (etag: string | null) => Promise<Resposta>;
  moverPeca: (peca: string, lugar: string | null, versaoEsperada: number) => Promise<Resposta>;
  sinalizar: (peca: string, lugar: string) => Promise<Resposta>;
  marcarPronto: (versao: number, pronto: boolean) => Promise<Resposta>;
  /** Pede para voltar à montagem; com o pedido do outro pendente, é o aceite. */
  pedirAjuste: () => Promise<Resposta>;
  /** Recusa o pedido do outro ou cancela o próprio. */
  recusarAjuste: () => Promise<Resposta>;
  /** Toca a própria lanterna da abertura; o servidor acende as duas se o outro tocou na janela. */
  tocarAbertura: () => Promise<Resposta>;
  sair: () => Promise<Resposta>;
}

/** Corpo application/problem+json do servidor; os campos faltam quando o erro não vem dele. */
interface CorpoDeErro {
  codigo?: string;
  motivo?: string | null;
  visao?: Visao | null;
  detail?: string;
}

async function lerJson(resposta: Response): Promise<unknown> {
  const tipo = resposta.headers.get("Content-Type") ?? "";
  if (!tipo.includes("json")) {
    return null;
  }
  try {
    return await resposta.json();
  } catch {
    return null;
  }
}

async function requisitar(
  metodo: "GET" | "POST",
  caminho: string,
  { corpo, etag }: { corpo?: unknown; etag?: string | null } = {},
): Promise<Resposta> {
  const cabecalhos: Record<string, string> = { "X-Duora": "1", Accept: "application/json, application/problem+json" };
  if (corpo !== undefined) {
    cabecalhos["Content-Type"] = "application/json";
  }
  if (etag) {
    cabecalhos["If-None-Match"] = etag;
  }

  let resposta: Response;
  try {
    resposta = await fetch(`/api${caminho}`, {
      method: metodo,
      headers: cabecalhos,
      credentials: "same-origin",
      cache: "no-store",
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
  } catch (causa) {
    throw new ErroApi({ status: 0, codigo: "REDE", detalhe: causa instanceof Error ? causa.message : String(causa) });
  }

  if (resposta.status === 304) {
    return { naoMudou: true, visao: null, etag: etag ?? null };
  }
  if (resposta.status === 204) {
    return { naoMudou: false, visao: null, etag: null };
  }
  const dados = await lerJson(resposta);
  if (!resposta.ok) {
    // O servidor é o contrato: o corpo de erro tem a forma do ProblemDetail dele.
    const erro = dados as CorpoDeErro | null;
    throw new ErroApi({
      status: resposta.status,
      codigo: erro?.codigo ?? (resposta.status >= 500 ? "SERVIDOR" : "DESCONHECIDO"),
      motivo: erro?.motivo ?? null,
      visao: erro?.visao ?? null,
      detalhe: erro?.detail ?? "",
    });
  }
  if (!dados) {
    throw new ErroApi({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  // Idem: o 200 da sala traz a Visao serializada pelo room/Visao.java.
  return { naoMudou: false, visao: dados as Visao, etag: resposta.headers.get("ETag") };
}

export const api: Api = {
  criarSala: (apelido) => requisitar("POST", "/salas", { corpo: { apelido } }),
  entrar: (convite, apelido) => requisitar("POST", "/salas/entrar", { corpo: { convite, apelido } }),
  obterSala: async (etag) => {
    const resposta = await requisitar("GET", "/sala", { etag });
    // R1: o 204 da sala quer dizer "sem cookie", o mesmo caminho do antigo 401.
    if (!resposta.naoMudou && resposta.visao === null) {
      throw new ErroApi({ status: 401, codigo: "SEM_ASSENTO" });
    }
    return resposta;
  },
  moverPeca: (peca, lugar, versaoEsperada) =>
    requisitar("POST", "/sala/pecas", { corpo: { peca, lugar, versaoEsperada } }),
  sinalizar: (peca, lugar) => requisitar("POST", "/sala/sinal", { corpo: { peca, lugar } }),
  marcarPronto: (versao, pronto) => requisitar("POST", "/sala/pronto", { corpo: { versao, pronto } }),
  pedirAjuste: () => requisitar("POST", "/sala/ajustar"),
  recusarAjuste: () => requisitar("POST", "/sala/ajustar/recusar"),
  tocarAbertura: () => requisitar("POST", "/sala/abertura"),
  sair: () => requisitar("POST", "/sala/sair"),
};
