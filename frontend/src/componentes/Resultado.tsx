import { textoResultado } from "../dominio/mensagens";
import type { Visao } from "../dominio/visao";
import type { Acao } from "../sessao/tipos";
import { BOTAO_SECUNDARIO, TEXTO_SECUNDARIO, TITULO_DE_RESULTADO, seDisponivel } from "./estilos";

interface PropsResultado {
  visao: Visao;
  enviando: Acao | null;
  aoAjustar: () => void;
}

/** A noite revelada. Fica no DOM, escondida, durante a montagem, como os outros blocos da montagem. */
export function Resultado({ visao, enviando, aoAjustar }: PropsResultado) {
  const revelado = visao.fase === "REVELADO" && visao.resultado !== null;
  const { titulo, texto } = revelado ? textoResultado(visao) : { titulo: "", texto: "" };
  return (
    <section className="resultado celular:order-3" id="resultado" hidden={!revelado}>
      <h2 className={TITULO_DE_RESULTADO} id="resultado-titulo" tabIndex={-1}>
        {titulo}
      </h2>
      <p className={TEXTO_SECUNDARIO} id="resultado-texto">
        {texto}
      </p>
      <button
        type="button"
        className={BOTAO_SECUNDARIO}
        id="ajustar"
        aria-disabled={enviando !== null ? true : undefined}
        onClick={seDisponivel(enviando !== null, aoAjustar)}
      >
        {enviando === "ajustar" ? "Enviando…" : "Ajustar de novo"}
      </button>
    </section>
  );
}
