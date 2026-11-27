// Transporte HTTP genérico com a API (mesma origem). Não conhece rotas nem regras: monta a
// requisição, lê o corpo e traduz erro de rede ou status fora de 2xx/304 em ErroHttp.

interface DadosDoErroHttp {
  status: number;
  codigo: string;
  mensagem?: string | null;
  tentarDeNovoEmSegundos?: number | null;
  corpo?: unknown;
  detalhe?: string;
}

export class ErroHttp extends Error {
  /** 0 quando a requisição nem chegou ao servidor. */
  readonly status: number;
  readonly codigo: string;
  /** Texto para a pessoa, vindo do servidor; null quando ele não mandou. */
  readonly mensagem: string | null;
  readonly tentarDeNovoEmSegundos: number | null;
  /** Corpo de erro já lido, para quem precisa de campos além do código. */
  readonly corpo: unknown;

  constructor({
    status,
    codigo,
    mensagem = null,
    tentarDeNovoEmSegundos = null,
    corpo = null,
    detalhe = "",
  }: DadosDoErroHttp) {
    super(detalhe || codigo);
    this.name = "ErroHttp";
    this.status = status;
    this.codigo = codigo;
    this.mensagem = mensagem;
    this.tentarDeNovoEmSegundos = tentarDeNovoEmSegundos;
    this.corpo = corpo;
  }

  /** Falha de rede ou do servidor: vale tentar de novo mais tarde. */
  get transitorio(): boolean {
    return this.status === 0 || this.status >= 500;
  }
}

export interface OpcoesDaRequisicao {
  /** Vai como JSON. Exclusivo com `formulario`. */
  corpo?: unknown;
  /** Vai como multipart/form-data; o navegador escreve o Content-Type com o boundary. */
  formulario?: FormData;
  cabecalhos?: Record<string, string>;
  etag?: string | null;
}

export interface RespostaHttp {
  status: number;
  cabecalhos: Headers;
  dados: unknown;
}

/** Campos do application/problem+json do servidor; faltam quando o erro não vem dele. */
interface CorpoDeErro {
  codigo?: unknown;
  mensagem?: unknown;
  detail?: unknown;
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

function texto(valor: unknown): string | null {
  return typeof valor === "string" && valor !== "" ? valor : null;
}

/** Só a forma em segundos; a forma de data HTTP não é usada pelo servidor. */
function lerRetryAfter(cabecalhos: Headers): number | null {
  const valor = cabecalhos.get("Retry-After");
  return valor !== null && /^\d+$/.test(valor.trim()) ? Number(valor) : null;
}

function erroDaResposta(resposta: Response, dados: unknown): ErroHttp {
  const corpo = (typeof dados === "object" && dados !== null ? dados : {}) as CorpoDeErro;
  const detalhe = texto(corpo.detail);
  return new ErroHttp({
    status: resposta.status,
    codigo: texto(corpo.codigo) ?? (resposta.status >= 500 ? "SERVIDOR" : "DESCONHECIDO"),
    mensagem: texto(corpo.mensagem) ?? detalhe,
    tentarDeNovoEmSegundos: lerRetryAfter(resposta.headers),
    corpo: dados,
    detalhe: detalhe ?? "",
  });
}

/** `caminho` é relativo a /api. 2xx e 304 resolvem; o resto rejeita com ErroHttp. */
export async function requisitarHttp(
  metodo: "GET" | "POST",
  caminho: string,
  { corpo, formulario, cabecalhos = {}, etag }: OpcoesDaRequisicao = {},
): Promise<RespostaHttp> {
  if (corpo !== undefined && formulario !== undefined) {
    throw new TypeError("corpo e formulario são exclusivos");
  }
  const enviados: Record<string, string> = {
    "X-Duora": "1",
    Accept: "application/json, application/problem+json",
  };
  if (corpo !== undefined) {
    enviados["Content-Type"] = "application/json";
  }
  if (etag) {
    enviados["If-None-Match"] = etag;
  }
  Object.assign(enviados, cabecalhos);

  let resposta: Response;
  try {
    resposta = await fetch(`/api${caminho}`, {
      method: metodo,
      headers: enviados,
      credentials: "same-origin",
      cache: "no-store",
      body: formulario ?? (corpo === undefined ? undefined : JSON.stringify(corpo)),
    });
  } catch (causa) {
    throw new ErroHttp({ status: 0, codigo: "REDE", detalhe: causa instanceof Error ? causa.message : String(causa) });
  }

  const dados = await lerJson(resposta);
  if (!resposta.ok && resposta.status !== 304) {
    throw erroDaResposta(resposta, dados);
  }
  return { status: resposta.status, cabecalhos: resposta.headers, dados };
}

/** Chave para o cabeçalho Idempotency-Key; casa ^[A-Za-z0-9-]{8,64}$. */
export function novaChaveDeIdempotencia(
  cripto: Pick<Crypto, "getRandomValues"> & Partial<Pick<Crypto, "randomUUID">> = globalThis.crypto,
): string {
  if (cripto.randomUUID) {
    return cripto.randomUUID();
  }
  const bytes = cripto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}
