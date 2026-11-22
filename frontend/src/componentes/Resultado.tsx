import { useCenario } from "../cenario/contexto";
import { textoResultado } from "../dominio/mensagens";
import type { Visao } from "../dominio/visao";
import type { AcoesDaSessao } from "../sessao/sessao";
import type { Acao } from "../sessao/tipos";
import { BOTAO_SECUNDARIO, TEXTO_SECUNDARIO, TITULO_DE_RESULTADO, seDisponivel } from "./estilos";

interface PropsResultado {
  visao: Visao;
  enviando: Acao | null;
  acoes: Pick<AcoesDaSessao, "ajustar">;
}

/** A noite revelada. Fica no DOM, escondida, durante a montagem, como os outros blocos da montagem. */
export function Resultado({ visao, enviando, acoes }: PropsResultado) {
  const c = useCenario();
  const revelado = visao.fase === "REVELADO" && visao.resultado !== null;
  const { titulo, texto } = revelado ? textoResultado(c, visao) : { titulo: "", texto: "" };
  return (
    <section className="resultado celular:order-3 celular-deitado:order-3" id="resultado" hidden={!revelado}>
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
        onClick={seDisponivel(enviando !== null, () => void acoes.ajustar())}
      >
        {enviando === "ajustar" ? "Enviando…" : "Ajustar de novo"}
      </button>
    </section>
  );
}
