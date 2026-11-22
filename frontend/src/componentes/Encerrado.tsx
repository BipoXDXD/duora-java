import type { AcoesDaSessao } from "../sessao/sessao";
import { BOTAO_PRIMARIO, TELA, TEXTO_SECUNDARIO, TITULO_DE_RESULTADO } from "./estilos";

interface PropsEncerrado {
  titulo: string;
  texto: string;
  acoes: Pick<AcoesDaSessao, "criarOutro">;
}

export function Encerrado({ titulo, texto, acoes }: PropsEncerrado) {
  return (
    <section className={`encerrado ${TELA}`} id="encerrado" aria-labelledby="encerrado-titulo">
      <h2 className={TITULO_DE_RESULTADO} id="encerrado-titulo" tabIndex={-1}>
        {titulo}
      </h2>
      <p className={`${TEXTO_SECUNDARIO} max-w-[36ch]`} id="encerrado-texto">
        {texto}
      </p>
      <button
        type="button"
        className={`${BOTAO_PRIMARIO} justify-self-start`}
        id="criar-outro"
        onClick={acoes.criarOutro}
      >
        Criar outro terraço
      </button>
    </section>
  );
}
