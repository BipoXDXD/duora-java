import { textoMotivoConfirmacao } from "../dominio/mensagens";
import type { Visao } from "../dominio/visao";
import type { Acao } from "../sessao/tipos";
import { BOTAO_PRIMARIO, BOTAO_TEXTO, seDisponivel } from "./estilos";

interface PropsAcoes {
  visao: Visao;
  enviando: Acao | null;
  somLigado: boolean;
  /** Confirma a composição ou, se você já confirmou, volta a ajustar: quem decide é a sessão. */
  aoConfirmar: () => void;
  aoAlternarSom: () => void;
}

/** Confirmação e som. Na noite revelada some só a confirmação; o som continua acessível para os meteoros. */
export function Acoes({ visao, enviando, somLigado, aoConfirmar, aoAlternarSom }: PropsAcoes) {
  const voceConfirmou = visao.prontos.voce;
  const revelado = visao.fase === "REVELADO";
  const indisponivel = enviando !== null || (!visao.confirmacao.podeConfirmar && !voceConfirmou);
  let textoPronto = voceConfirmou ? "Voltar a ajustar" : "Pronto para olhar o céu";
  if (enviando === "pronto") {
    textoPronto = "Enviando…";
  }

  return (
    <div className="acoes grid gap-(--e-2) mt-auto celular:order-5">
      <button
        type="button"
        className={BOTAO_PRIMARIO}
        id="pronto"
        aria-disabled={indisponivel ? true : undefined}
        hidden={revelado}
        onClick={seDisponivel(indisponivel, aoConfirmar)}
      >
        {textoPronto}
      </button>
      <p className="motivo m-0 text-texto-2 text-[0.875rem] min-h-[1.3em]" id="motivo" hidden={revelado}>
        {textoMotivoConfirmacao(visao)}
      </p>
      <button
        type="button"
        className={`botao-som ${BOTAO_TEXTO}`}
        id="som"
        aria-pressed={somLigado}
        onClick={aoAlternarSom}
      >
        Som{" "}
        <span className="estado-som" id="estado-som" aria-hidden="true">
          {somLigado ? "ligado" : "desligado"}
        </span>
      </button>
    </div>
  );
}
