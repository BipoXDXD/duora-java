// Cliente da API do terraço sobre o transporte de http.ts. Não interpreta regras: devolve a
// visão ou lança ErroApi com o código do contrato.

import type { Visao } from "../dominio/visao";
import { ErroHttp, requisitarHttp, type RespostaHttp } from "./http";

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

/** Campos de erro que só a API do terraço manda, além do código. */
interface CorpoDeErroDaSala {
  motivo?: string | null;
  visao?: Visao | null;
}

function erroDaApi(erro: ErroHttp): ErroApi {
  const corpo = (erro.corpo ?? {}) as CorpoDeErroDaSala;
  return new ErroApi({
    status: erro.status,
    codigo: erro.codigo,
    motivo: corpo.motivo ?? null,
    visao: corpo.visao ?? null,
    detalhe: erro.message,
  });
}

async function requisitar(
  metodo: "GET" | "POST",
  caminho: string,
  { corpo, etag }: { corpo?: unknown; etag?: string | null } = {},
): Promise<Resposta> {
  let resposta: RespostaHttp;
  try {
    resposta = await requisitarHttp(metodo, caminho, { corpo, etag });
  } catch (erro) {
    throw erro instanceof ErroHttp ? erroDaApi(erro) : erro;
  }

  if (resposta.status === 304) {
    return { naoMudou: true, visao: null, etag: etag ?? null };
  }
  if (resposta.status === 204) {
    return { naoMudou: false, visao: null, etag: null };
  }
  if (!resposta.dados) {
    throw new ErroApi({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  // O 200 da sala traz a Visao serializada pelo room/Visao.java.
  return { naoMudou: false, visao: resposta.dados as Visao, etag: resposta.cabecalhos.get("ETag") };
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
