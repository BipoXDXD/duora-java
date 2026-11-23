// O terraço do ponto de vista de quem pede, campo a campo como o room/Visao.java serializa.
// Campos anuláveis do contrato chegam como null (o Jackson não omite nulos).

export type Fase = "AGUARDANDO_PAR" | "MONTANDO" | "REVELADO";

export type Assento = "A" | "B";

export interface Voce {
  assento: Assento;
  nome: string;
}

export interface Parceiro {
  assento: Assento;
  nome: string;
  conectado: boolean;
}

/** Peças próprias trazem lugares permitidos e bloqueio; as do parceiro, os destinos sugeríveis. */
export interface VisaoPeca {
  peca: string;
  assento: Assento;
  lugar: string | null;
  lugaresPermitidos?: string[];
  bloqueio?: string | null;
  destinosSugeriveis?: string[];
}

export interface Prontos {
  voce: boolean;
  parceiro: boolean;
}

export interface Confirmacao {
  podeConfirmar: boolean;
  faltam: number;
}

export interface VisaoSinal {
  peca: string;
  deAssento: Assento;
  lugar: string;
}

/** Pedido de ajuste pendente na revelação: quem pediu espera o aceite do outro. */
export interface VisaoAjuste {
  deAssento: Assento;
}

/**
 * A abertura "acender junto" da montagem. ativa: ainda esperando os dois; voceTocou/outroTocou: um
 * toque dentro da janela; acesa: os dois tocaram a tempo (concluída pelo tempo, fica apagada).
 */
export interface VisaoAbertura {
  ativa: boolean;
  voceTocou: boolean;
  outroTocou: boolean;
  acesa: boolean;
}

/** Desfecho do cenário: chave → valor, na ordem que o servidor manda (terraço: secundariaA, secundariaB, ladoTelescopio). */
export type VisaoResultado = Readonly<Record<string, string>>;

export interface Visao {
  fase: Fase;
  versao: number;
  voce: Voce;
  parceiro: Parceiro | null;
  convite: string | null;
  pecas: VisaoPeca[];
  prontos: Prontos;
  confirmacao: Confirmacao;
  sinal: VisaoSinal | null;
  resultado: VisaoResultado | null;
  ajuste: VisaoAjuste | null;
  /** Só na montagem de um cenário com abertura; continua (concluída) até a revelação. */
  abertura: VisaoAbertura | null;
}

/** A abertura ainda espera os dois: sem instrução, sem peças no painel, só as lanternas na cena. */
export function aberturaAtiva(visao: Visao): boolean {
  return visao.abertura?.ativa === true;
}

/** A abertura acabou de acender entre duas visões (concluída pelo tempo, ela fica apagada). */
export function lanternasAcenderam(anterior: Visao, visao: Visao): boolean {
  return aberturaAtiva(anterior) && visao.abertura?.ativa === false && visao.abertura.acesa;
}

/** O sinal só vale na montagem: na revelação, a sugestão não é acionável e não aparece. */
export function sinalDaMontagem(visao: Visao): VisaoSinal | null {
  return visao.fase === "MONTANDO" ? visao.sinal : null;
}
