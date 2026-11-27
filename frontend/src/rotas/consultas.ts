// Consultas do TanStack Query que mais de uma tela da casca lê: a mesma chave, o mesmo cache.

import { queryOptions } from "@tanstack/react-query";
import { obterConta } from "../api/estadoDaConta";
import { obterVersoesLegais } from "../api/legal";
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
