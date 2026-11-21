// Transporte com a API do terraço (mesma origem). Não interpreta regras: devolve a visão
// ou lança ErroApi com o código do contrato.

const ESPERA_NORMAL = 2000;
const ESPERA_OCULTA = 10000;
const ESPERAS_ERRO = [2000, 4000, 8000, 15000];

export class ErroApi extends Error {
  constructor({ status, codigo, motivo = null, visao = null, detalhe = "" }) {
    super(detalhe || codigo);
    this.name = "ErroApi";
    this.status = status;
    this.codigo = codigo;
    this.motivo = motivo;
    this.visao = visao;
  }

  /** Falha de rede ou do servidor: vale tentar de novo mais tarde. */
  get transitorio() {
    return this.status === 0 || (this.status >= 500 && this.codigo !== "LIMITE_SALAS");
  }
}

async function lerJson(resposta) {
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

async function requisitar(metodo, caminho, { corpo, etag } = {}) {
  const cabecalhos = { "X-Duora": "1", Accept: "application/json, application/problem+json" };
  if (corpo !== undefined) {
    cabecalhos["Content-Type"] = "application/json";
  }
  if (etag) {
    cabecalhos["If-None-Match"] = etag;
  }

  let resposta;
  try {
    resposta = await fetch(`/api${caminho}`, {
      method: metodo,
      headers: cabecalhos,
      credentials: "same-origin",
      cache: "no-store",
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
  } catch (causa) {
    throw new ErroApi({ status: 0, codigo: "REDE", detalhe: causa.message });
  }

  if (resposta.status === 304) {
    return { naoMudou: true, visao: null, etag };
  }
  if (resposta.status === 204) {
    return { naoMudou: false, visao: null, etag: null };
  }
  const dados = await lerJson(resposta);
  if (!resposta.ok) {
    throw new ErroApi({
      status: resposta.status,
      codigo: dados?.codigo ?? (resposta.status >= 500 ? "SERVIDOR" : "DESCONHECIDO"),
      motivo: dados?.motivo ?? null,
      visao: dados?.visao ?? null,
      detalhe: dados?.detail ?? "",
    });
  }
  if (!dados) {
    throw new ErroApi({ status: resposta.status, codigo: "RESPOSTA_INVALIDA" });
  }
  return { naoMudou: false, visao: dados, etag: resposta.headers.get("ETag") };
}

export const api = {
  criarSala: (apelido) => requisitar("POST", "/salas", { corpo: { apelido } }),
  entrar: (convite, apelido) => requisitar("POST", "/salas/entrar", { corpo: { convite, apelido } }),
  obterSala: (etag) => requisitar("GET", "/sala", { etag }),
  moverPeca: (peca, lugar, versaoEsperada) =>
    requisitar("POST", "/sala/pecas", { corpo: { peca, lugar, versaoEsperada } }),
  sinalizar: (peca) => requisitar("POST", "/sala/sinal", { corpo: { peca } }),
  marcarPronto: (versao, pronto) => requisitar("POST", "/sala/pronto", { corpo: { versao, pronto } }),
  ajustar: () => requisitar("POST", "/sala/ajustar"),
  sair: () => requisitar("POST", "/sala/sair"),
};

/**
 * Laço único de GET /api/sala encadeado com setTimeout: a próxima consulta só é agendada
 * quando a anterior termina. Erros transitórios recuam 2→4→8→15 s; os definitivos param o laço.
 */
export function criarPolling({ aoReceber, aoFalhar, aoRecuperar }) {
  let ativo = false;
  let emVoo = false;
  let repetirJa = false;
  let temporizador = null;
  let etag = null;
  let falhas = 0;
  // Muda a cada parada ou mutação aceita: respostas de consultas mais antigas são descartadas.
  let epoca = 0;

  function espera() {
    const base = falhas > 0 ? ESPERAS_ERRO[Math.min(falhas, ESPERAS_ERRO.length) - 1] : ESPERA_NORMAL;
    return document.hidden ? Math.max(base, ESPERA_OCULTA) : base;
  }

  function agendar(ms) {
    clearTimeout(temporizador);
    temporizador = setTimeout(consultar, ms);
  }

  async function consultar() {
    temporizador = null;
    if (!ativo || emVoo) {
      return;
    }
    emVoo = true;
    const minhaEpoca = epoca;
    try {
      const resposta = await api.obterSala(etag);
      if (ativo && minhaEpoca === epoca) {
        if (falhas > 0) {
          falhas = 0;
          aoRecuperar();
        }
        if (!resposta.naoMudou) {
          etag = resposta.etag;
          aoReceber(resposta.visao);
        }
      }
    } catch (erro) {
      if (ativo && minhaEpoca === epoca) {
        if (erro instanceof ErroApi && erro.transitorio) {
          falhas += 1;
          aoFalhar(erro, espera());
        } else {
          ativo = false;
          aoFalhar(erro, null);
        }
      }
    } finally {
      emVoo = false;
      if (ativo) {
        agendar(repetirJa ? 0 : espera());
      }
      repetirJa = false;
    }
  }

  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) {
      consultarAgora();
    }
  });

  function consultarAgora() {
    if (!ativo) {
      return;
    }
    if (emVoo) {
      repetirJa = true;
    } else {
      agendar(0);
    }
  }

  return {
    iniciar() {
      if (ativo) {
        return;
      }
      ativo = true;
      falhas = 0;
      consultarAgora();
    },
    parar() {
      ativo = false;
      epoca += 1;
      etag = null;
      clearTimeout(temporizador);
      temporizador = null;
    },
    consultarAgora,
    /** Uma mutação trouxe visão nova: descarta consultas em voo e passa a usar o ETag dela. */
    aceitar(novoEtag) {
      epoca += 1;
      etag = novoEtag ?? null;
    },
  };
}
