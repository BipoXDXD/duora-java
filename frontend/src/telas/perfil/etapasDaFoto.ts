// As etapas do envio da foto, sem React: a conferência no aparelho, a tradução das recusas do
// servidor e a máquina de estados que a tela segue.

import { ErroHttp, novaChaveDeIdempotencia } from "../../api/http";

/** 5 MB, o mesmo teto do servidor. */
export const TAMANHO_MAXIMO = 5_242_880;

export const TIPOS_ACEITOS = ["image/jpeg", "image/png", "image/webp"] as const;

/** Espera do 429 quando o servidor não manda o Retry-After. */
const ESPERA_PADRAO_EM_SEGUNDOS = 60;

export type AvisoDaFoto =
  | { tipo: "grande" | "formato" | "invalida" | "indisponivel" }
  | { tipo: "limite"; segundos: number }
  | { tipo: "servidor"; mensagem: string };

/** A foto escolhida: o arquivo, a prévia em data URL e a chave de idempotência do envio. */
interface Escolha {
  arquivo: File;
  previa: string;
  chave: string;
}

export type EtapaDaFoto =
  | { tipo: "parada"; aviso?: AvisoDaFoto }
  | ({ tipo: "previa" } & Escolha)
  | ({ tipo: "enviando" } & Escolha)
  | ({ tipo: "falhou"; aviso: AvisoDaFoto } & Escolha)
  | { tipo: "enviada"; aprovada: boolean };

export type EventoDaFoto =
  | ({ tipo: "escolheu" } & Escolha)
  | { tipo: "recusadaNoCliente"; aviso: AvisoDaFoto }
  | { tipo: "previaFalhou" }
  | { tipo: "enviar" }
  | { tipo: "enviado"; aprovada: boolean }
  | { tipo: "envioRecusado"; erro: unknown }
  | { tipo: "descartar" };

export const ETAPA_INICIAL: EtapaDaFoto = { tipo: "parada" };

/**
 * O que dá para recusar antes de enviar: o tamanho e um tipo declarado fora da lista. Sem tipo (o
 * aparelho não soube dizer), quem decide são os bytes, no servidor.
 */
export function conferirNoCliente(f: File): AvisoDaFoto | null {
  if (f.size > TAMANHO_MAXIMO) {
    return { tipo: "grande" };
  }
  if (f.type !== "" && !(TIPOS_ACEITOS as readonly string[]).includes(f.type)) {
    return { tipo: "formato" };
  }
  return null;
}

function erroHttp(e: unknown, status: number, codigo?: string): boolean {
  return e instanceof ErroHttp && e.status === status && (codigo === undefined || e.codigo === codigo);
}

/** A mesma chave foi usada com outro arquivo: a próxima tentativa precisa de uma chave nova. */
function chaveReusada(e: unknown): boolean {
  return erroHttp(e, 409, "CHAVE_DE_IDEMPOTENCIA_REUSADA");
}

export function avisoDoErro(e: unknown): AvisoDaFoto {
  if (!(e instanceof ErroHttp)) {
    return { tipo: "indisponivel" };
  }
  if (e.status === 413) {
    return { tipo: "grande" };
  }
  if (e.status === 415) {
    return { tipo: "formato" };
  }
  if (erroHttp(e, 422, "IMAGEM_INVALIDA")) {
    return { tipo: "invalida" };
  }
  if (e.status === 429) {
    return { tipo: "limite", segundos: e.tentarDeNovoEmSegundos ?? ESPERA_PADRAO_EM_SEGUNDOS };
  }
  if (
    e.status === 0 ||
    e.status === 503 ||
    erroHttp(e, 409, "REQUISICAO_EM_ANDAMENTO") ||
    chaveReusada(e) ||
    e.mensagem === null
  ) {
    return { tipo: "indisponivel" };
  }
  return { tipo: "servidor", mensagem: e.mensagem };
}

function escolha({ arquivo, previa, chave }: Escolha): Escolha {
  return { arquivo, previa, chave };
}

/**
 * A próxima etapa. Durante o envio, só a resposta conta: escolher outra foto ou descartar espera.
 * Tentar de novo depois de uma falha reusa a chave (o servidor devolve o mesmo resultado se o
 * primeiro envio chegou), salvo quando ele disse que a chave já serviu a outro arquivo.
 */
export function transicao(
  e: EtapaDaFoto,
  ev: EventoDaFoto,
  gerarChave: () => string = novaChaveDeIdempotencia,
): EtapaDaFoto {
  if (e.tipo === "enviando") {
    if (ev.tipo === "enviado") {
      return { tipo: "enviada", aprovada: ev.aprovada };
    }
    if (ev.tipo === "envioRecusado") {
      return {
        tipo: "falhou",
        ...escolha(e),
        chave: chaveReusada(ev.erro) ? gerarChave() : e.chave,
        aviso: avisoDoErro(ev.erro),
      };
    }
    return e;
  }
  switch (ev.tipo) {
    case "escolheu":
      return { tipo: "previa", ...escolha(ev) };
    case "recusadaNoCliente":
      return { tipo: "parada", aviso: ev.aviso };
    case "previaFalhou":
      return { tipo: "parada", aviso: { tipo: "invalida" } };
    case "enviar":
      return e.tipo === "previa" || e.tipo === "falhou" ? { tipo: "enviando", ...escolha(e) } : e;
    case "descartar":
      return { tipo: "parada" };
    case "enviado":
    case "envioRecusado":
      return e;
  }
}
