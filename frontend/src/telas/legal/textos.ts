// Textos fixos das telas públicas de /denunciar e /encarregado. A mensagem de sucesso da denúncia vem
// do servidor.

import type { MotivoDaDenuncia } from "../../api/denuncia";

export const DENUNCIAR = {
  titulo: "Denunciar um problema",
  tituloDaAba: "Denunciar · Duora",
  introducao:
    "Use este canal se você não tem conta ou não consegue denunciar pelo app. Em caso de risco imediato, ligue 190.",
  rotuloDoMotivo: "O que aconteceu",
  /** Na ordem da tela. */
  motivos: [
    ["PARECE_MENOR", "Parece menor de idade"],
    ["ABUSO_INFANTIL", "Abuso ou exploração de criança"],
    ["IMAGEM_INTIMA", "Imagem íntima sem consentimento"],
    ["ASSEDIO", "Assédio ou ameaça"],
    ["GOLPE", "Golpe"],
    ["OUTRO", "Outro"],
  ] as const satisfies readonly (readonly [MotivoDaDenuncia, string])[],
  rotuloDaDescricao: "Conte o que aconteceu",
  dicaDaDescricao: "De 10 a 2000 caracteres.",
  rotuloDaReferencia: "Apelido ou link, se souber",
  dicaDaReferencia: "Até 200 caracteres.",
  rotuloDoEmail: "Seu e-mail para resposta (opcional)",
  enviar: "Enviar denúncia",
  enviando: "Enviando…",
  /** As recusas por campo, iguais às do servidor (DENUNCIA_PUBLICA_INVALIDA). */
  semMotivo: "Escolha o que aconteceu.",
  descricaoForaDoTamanho: "Conte o que aconteceu em 10 a 2000 caracteres.",
  referenciaLonga: "Use até 200 caracteres no apelido ou link.",
  emailInvalido: "Confira o e-mail para resposta.",
  /** Falha sem resposta conclusiva (rede, servidor fora do ar). */
  falha: "Não conseguimos confirmar o envio. Tente de novo em instantes.",
} as const;

/** "12 de 2000": o contador da descrição. */
export function textoDoContador(caracteres: number, maximo: number): string {
  return `${String(caracteres)} de ${String(maximo)}`;
}

export const ENCARREGADO = {
  titulo: "Encarregado de dados",
  tituloDaAba: "Encarregado de dados · Duora",
  uso: "Use este contato para pedidos sobre seus dados pessoais (acesso, correção, exclusão).",
  nome: "Nome",
  email: "E-mail",
  carregando: "Carregando o contato…",
  falha: "Não conseguimos carregar o contato. Tente de novo em instantes.",
  tentarDeNovo: "Tentar de novo",
} as const;
