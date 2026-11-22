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

/** Peças próprias trazem lugares permitidos e bloqueio; as do parceiro, não. */
export interface VisaoPeca {
  peca: string;
  assento: Assento;
  lugar: string | null;
  lugaresPermitidos?: string[];
  bloqueio?: string | null;
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
}

export interface VisaoResultado {
  secundariaA: string;
  secundariaB: string;
  ladoTelescopio: string;
}

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
}
