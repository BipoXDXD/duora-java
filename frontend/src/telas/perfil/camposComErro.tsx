// A ligação de cada campo do formulário com a mensagem de erro dele, igual nas telas do perfil.

import type { ReactElement } from "react";

export interface CamposComErro<C extends string> {
  /** As descrições do campo (aria-describedby): as fixas e, se houver, o erro dele. */
  descricao: (campo: C, ...fixas: string[]) => string | undefined;
  /** O parágrafo do erro, ou nada sem erro. */
  erroDo: (campo: C) => ReactElement | null;
  /** O aria-invalid do campo: true com erro, ausente sem. */
  invalido: (campo: C) => true | undefined;
}

export function camposComErro<C extends string>(
  erros: Partial<Record<C, string>>,
  idDoErro: (campo: C) => string,
  classeDoErro: string,
): CamposComErro<C> {
  return {
    descricao: (campo, ...fixas) => {
      const ids = erros[campo] === undefined ? fixas : [...fixas, idDoErro(campo)];
      return ids.length > 0 ? ids.join(" ") : undefined;
    },
    erroDo: (campo) => {
      const texto = erros[campo];
      return texto === undefined ? null : (
        <p className={classeDoErro} id={idDoErro(campo)}>
          {texto}
        </p>
      );
    },
    invalido: (campo) => (erros[campo] === undefined ? undefined : true),
  };
}
