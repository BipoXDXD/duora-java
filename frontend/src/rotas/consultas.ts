// Consultas do TanStack Query que mais de uma tela da casca lê: a mesma chave, o mesmo cache.

import { queryOptions, type DataTag, type QueryClient, type UndefinedInitialDataOptions } from "@tanstack/react-query";
import { obterConta } from "../api/estadoDaConta";
import { obterVersoesLegais } from "../api/legal";
import {
  obterLocalizacao,
  obterMeuPerfil,
  obterNamoro,
  obterOrientacao,
  obterPerfilPublico,
  obterPreferencias,
  type PerfilPublico,
} from "../api/perfil";
import { obterVerificacao } from "../api/verificacao";

/** A conta da sessão (null quando anônimo). Quem abre ou fecha uma sessão invalida esta chave. */
export const consultaDaConta = queryOptions({ queryKey: ["conta"], queryFn: obterConta });

/**
 * Versões atuais dos documentos legais e o contato, lidas uma vez por carga: o cadastro aceita a
 * versão que mostrou, e o rodapé do layout lê o contato do mesmo cache. Só o TERMOS_DESATUALIZADOS
 * invalida a chave.
 */
export const consultaDasVersoesLegais = queryOptions({
  queryKey: ["legal", "versoes"],
  queryFn: obterVersoesLegais,
  staleTime: Infinity,
});

/** A situação da verificação de idade da conta, lida do servidor a cada chegada à /verificacao. */
export const consultaDaVerificacao = queryOptions({ queryKey: ["conta", "verificacao"], queryFn: obterVerificacao });

// Perfil (Etapa 3): sem staleTime; quem grava invalida a chave ou escreve a resposta no cache.

export const consultaDoMeuPerfil = queryOptions({ queryKey: ["perfil"], queryFn: obterMeuPerfil });

export const consultaDasPreferencias = queryOptions({
  queryKey: ["perfil", "preferencias"],
  queryFn: obterPreferencias,
});

export const consultaDaLocalizacao = queryOptions({ queryKey: ["perfil", "localizacao"], queryFn: obterLocalizacao });

export const consultaDoNamoro = queryOptions({ queryKey: ["perfil", "namoro"], queryFn: obterNamoro });

export const consultaDaOrientacao = queryOptions({ queryKey: ["perfil", "orientacao"], queryFn: obterOrientacao });

/** null quando a pessoa não existe ou não está visível. */
type ConsultaDoPerfilPublico = UndefinedInitialDataOptions<PerfilPublico | null, Error, PerfilPublico | null, string[]> & {
  queryKey: DataTag<string[], PerfilPublico | null, Error>;
};

export function consultaDoPerfilPublico(contaId: string): ConsultaDoPerfilPublico {
  return queryOptions({ queryKey: ["perfis", contaId], queryFn: () => obterPerfilPublico(contaId) });
}

/**
 * Ao sair, excluir a conta ou perder a sessão: tira do cache a conta e tudo de perfil (o próprio e
 * os de outras pessoas), para a próxima sessão no mesmo aparelho não ver dados da anterior.
 */
export function esquecerASessao(cliente: QueryClient): void {
  cliente.removeQueries({ queryKey: consultaDaConta.queryKey, exact: true });
  cliente.removeQueries({ queryKey: ["perfil"] });
  cliente.removeQueries({ queryKey: ["perfis"] });
}
