// Guardas dos loaders das rotas que exigem conta: decidem, antes do render, se a tela abre ou para
// onde a pessoa segue.

import type { QueryClient } from "@tanstack/react-query";
import { redirect } from "react-router";
import type { Conta, EstadoDaConta } from "../api/estadoDaConta";
import { CAMINHO_DA_VERIFICACAO } from "./caminhosDaCasca";
import { consultaDaConta } from "./consultas";

/** A tela de cada estado; o estado fora das telas aceitas por uma rota segue para a sua. */
const TELA_DO_ESTADO = {
  PENDENTE: "/cadastro",
  AGUARDANDO_VERIFICACAO: CAMINHO_DA_VERIFICACAO,
  ATIVA: "/conta",
} as const satisfies Record<EstadoDaConta, string>;

/**
 * Lê a conta da sessão (sempre do servidor: a consulta não tem staleTime) e devolve a conta se o
 * estado dela está em `aceitos`. O anônimo vai para /entrar; os outros estados, para a tela deles.
 * Erro na leitura (403 CONTA_INDISPONIVEL, rede) segue para o ErrorBoundary da casca, nunca para o
 * anônimo. A leitura não pausa quando o navegador se diz fora do ar (networkMode "always" do
 * cliente): a falha de rede chega ao ErrorBoundary.
 */
export async function exigirConta(cliente: QueryClient, aceitos: readonly EstadoDaConta[]): Promise<Conta> {
  const conta = await cliente.query(consultaDaConta);
  if (conta === null) {
    throw redirect("/entrar");
  }
  if (!aceitos.includes(conta.estado)) {
    throw redirect(TELA_DO_ESTADO[conta.estado]);
  }
  return conta;
}

/**
 * Guarda das telas de perfil: só a conta ATIVA passa, e só então `carregar` roda (quem aguarda a
 * verificação nunca dispara os pedidos da tela).
 */
export async function comContaAtiva<T>(cliente: QueryClient, carregar: () => Promise<T>): Promise<T> {
  await exigirConta(cliente, ["ATIVA"]);
  return carregar();
}
