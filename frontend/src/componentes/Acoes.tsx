import { textoMotivoConfirmacao } from "../dominio/mensagens";
import type { Visao } from "../dominio/visao";
import type { AcoesDaSessao } from "../sessao/sessao";
import type { Acao } from "../sessao/tipos";
import { BOTAO_PRIMARIO, BOTAO_TEXTO, seDisponivel } from "./estilos";

interface PropsAcoes {
  visao: Visao;
  enviando: Acao | null;
  somLigado: boolean;
  /** confirmar: confirma a composição ou, se você já confirmou, volta a ajustar; quem decide é a sessão. */
  acoes: Pick<AcoesDaSessao, "confirmar">;
  aoAlternarSom: () => void;
}

/** Confirmação e som. Na noite revelada some só a confirmação; o som continua acessível para os meteoros. */
export function Acoes({ visao, enviando, somLigado, acoes, aoAlternarSom }: PropsAcoes) {
  const voceConfirmou = visao.prontos.voce;
  const revelado = visao.fase === "REVELADO";
  const indisponivel = enviando !== null || (!visao.confirmacao.podeConfirmar && !voceConfirmou);
  let textoPronto = voceConfirmou ? "Voltar a ajustar" : "Pronto para olhar o céu";
  if (enviando === "pronto") {
    textoPronto = "Enviando…";
  }

  return (
    <div className="acoes grid gap-(--e-2) mt-auto celular:order-5 celular-deitado:order-5">
      <button
        type="button"
        className={BOTAO_PRIMARIO}
        id="pronto"
        aria-disabled={indisponivel ? true : undefined}
        hidden={revelado}
        onClick={seDisponivel(indisponivel, () => void acoes.confirmar())}
      >
        {textoPronto}
      </button>
      <p className="motivo m-0 text-texto-2 text-[0.875rem] min-h-[1.3em]" id="motivo" hidden={revelado}>
        {textoMotivoConfirmacao(visao)}
      </p>
      <BotaoSom somLigado={somLigado} aoAlternarSom={aoAlternarSom} />
    </div>
  );
}

interface PropsBotaoSom {
  somLigado: boolean;
  aoAlternarSom: () => void;
  /** Fora da montagem o botão continua na página, oculto, como no HTML antigo: o estado do som não some com a tela. */
  oculto?: boolean;
}

/** Estado do som: aria-pressed para leitores de tela; o texto visível fica fora do nome. */
export function BotaoSom({ somLigado, aoAlternarSom, oculto = false }: PropsBotaoSom) {
  return (
    <button
      type="button"
      className={`botao-som ${BOTAO_TEXTO}`}
      id="som"
      aria-pressed={somLigado}
      hidden={oculto}
      onClick={aoAlternarSom}
    >
      Som{" "}
      <span className="estado-som" id="estado-som" aria-hidden="true">
        {somLigado ? "ligado" : "desligado"}
      </span>
    </button>
  );
}
