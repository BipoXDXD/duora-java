// Consultas do TanStack Query que mais de uma tela da casca lê: a mesma chave, o mesmo cache.

import { queryOptions } from "@tanstack/react-query";
import { obterConta } from "../api/estadoDaConta";

/** A conta da sessão (null quando anônimo). Quem abre ou fecha uma sessão invalida esta chave. */
export const consultaDaConta = queryOptions({ queryKey: ["conta"], queryFn: obterConta });
