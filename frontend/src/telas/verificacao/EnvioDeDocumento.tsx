import { useEffect, useId, useRef, useState, type ChangeEvent, type ReactElement } from "react";
import {
  BOTAO_PRIMARIO,
  BOTAO_SECUNDARIO_SEM_MARGEM,
  CAMPO,
  DICA,
  ROTULO,
  seDisponivel,
} from "../../componentes/estilos";
import { Desfecho } from "./Desfecho";
import type { AvisoDoDocumento, LadoDoDocumento } from "./etapas";
import { desenharPrevia, reduzirFoto } from "./fotos";
import { VERIFICACAO_DE_IDADE } from "./textos";

const TEXTOS = VERIFICACAO_DE_IDADE.documento;

const PARAGRAFO = "m-0 text-texto-2 text-[1.0625rem] leading-[1.55]";
const AVISO = "m-0 border-l-[3px] border-coral pl-(--e-4) text-marfim text-[1.0625rem] leading-[1.5]";
const CONTAGEM = "m-0 text-texto-3 text-[0.9375rem]";
const ERRO_DO_CAMPO = "m-0 text-[0.875rem] leading-[1.45] text-coral empty:hidden";

/** A moldura tem a proporção de um RG ou de uma CNH (85,6 × 54 mm): a prévia cabe inteira, sem corte. */
const MOLDURA =
  "grid place-items-center aspect-[1.586] overflow-hidden rounded-(--raio) border bg-noite-950 data-[vazia]:border-dashed";

const AVISOS: Record<AvisoDoDocumento, string> = {
  naoLeu: TEXTOS.naoLeu,
  selfieVenceu: TEXTOS.selfieVenceu,
  fotosIlegiveis: TEXTOS.fotosIlegiveis,
  fotosGrandes: TEXTOS.fotosGrandes,
};

interface PropsDoEnvio {
  frente?: Blob;
  verso?: Blob;
  aviso?: AvisoDoDocumento;
  /** Os envios de documento que sobram hoje; aparecem junto do aviso de que a Didit não leu. */
  envios: number;
  /**
   * false quando a página já abre nesta etapa: o foco fica onde o navegador o pôs. Vale o da montagem:
   * escolher uma foto troca a etapa, mas não devolve o foco ao título.
   */
  focar: boolean;
  /** A foto reduzida do lado; null quando a escolhida não abriu. */
  aoEscolher: (lado: LadoDoDocumento, foto: Blob | null) => void;
  aoFazerSelfie: () => void;
}

/**
 * A faixa de dúvida: as fotos da frente e do verso, só em memória, e a selfie nova que sai logo
 * depois com elas. O botão da selfie fica no foco e na leitura mesmo sem as duas fotos, e a linha
 * logo abaixo diz o que falta.
 */
export function EnvioDeDocumento({
  frente,
  verso,
  aviso,
  envios,
  focar,
  aoEscolher,
  aoFazerSelfie,
}: PropsDoEnvio): ReactElement {
  const [focarNaEntrada] = useState(focar);
  const idDaDica = useId();
  const idDoMotivo = useId();
  const faltamFotos = frente === undefined || verso === undefined;

  return (
    <Desfecho titulo={TEXTOS.titulo} focar={focarNaEntrada}>
      {aviso !== undefined && (
        <div className="grid gap-(--e-2)">
          <p role="alert" className={AVISO}>
            {AVISOS[aviso]}
          </p>
          {aviso === "naoLeu" && <p className={CONTAGEM}>{TEXTOS.envios(envios)}</p>}
        </div>
      )}
      <p className={PARAGRAFO}>{TEXTOS.texto}</p>
      <div className="grid gap-(--e-4) sm:grid-cols-2">
        <CampoDaFoto lado="frente" rotulo={TEXTOS.frente} foto={frente} idDaDica={idDaDica} aoEscolher={aoEscolher} />
        <CampoDaFoto lado="verso" rotulo={TEXTOS.verso} foto={verso} idDaDica={idDaDica} aoEscolher={aoEscolher} />
      </div>
      <p className={DICA} id={idDaDica}>
        {TEXTOS.dica}
      </p>
      <div className="grid gap-(--e-2)">
        <button
          type="button"
          className={`w-full ${BOTAO_PRIMARIO}`}
          aria-disabled={faltamFotos ? true : undefined}
          aria-describedby={idDoMotivo}
          onClick={seDisponivel(faltamFotos, aoFazerSelfie)}
        >
          {TEXTOS.fazerSelfie}
        </button>
        <p className={DICA} id={idDoMotivo}>
          {faltamFotos ? TEXTOS.faltamFotos : TEXTOS.dicaDoEnvio}
        </p>
      </div>
    </Desfecho>
  );
}

interface PropsDoCampo {
  lado: LadoDoDocumento;
  rotulo: string;
  foto: Blob | undefined;
  idDaDica: string;
  aoEscolher: (lado: LadoDoDocumento, foto: Blob | null) => void;
}

/**
 * Uma foto do documento. A entrada de arquivo fica escondida só da vista: o foco do teclado chega nela,
 * e o anel aparece no rótulo-botão ao lado. O nome junta o lado e a ação ("Frente do documento Trocar
 * foto"), para o leitor de tela saber se a foto já foi escolhida.
 */
function CampoDaFoto({ lado, rotulo, foto, idDaDica, aoEscolher }: PropsDoCampo): ReactElement {
  const idDaEntrada = useId();
  const idDoRotulo = useId();
  const idDaAcao = useId();
  const idDoErro = useId();
  const [ilegivel, setIlegivel] = useState(false);

  async function escolher(evento: ChangeEvent<HTMLInputElement>): Promise<void> {
    const arquivo = evento.target.files?.[0];
    // Esvazia a entrada: o arquivo não fica preso ao DOM, e a mesma foto pode ser escolhida de novo.
    evento.target.value = "";
    if (arquivo === undefined) {
      return;
    }
    try {
      const reduzida = await reduzirFoto(arquivo);
      setIlegivel(false);
      aoEscolher(lado, reduzida);
    } catch {
      setIlegivel(true);
      aoEscolher(lado, null);
    }
  }

  return (
    <div className={CAMPO}>
      <span className={ROTULO} id={idDoRotulo}>
        {rotulo}
      </span>
      <div
        className={`${MOLDURA} ${ilegivel ? "border-coral" : "border-ceu-600"}`}
        data-vazia={foto === undefined ? "" : undefined}
      >
        {foto !== undefined && <Previa foto={foto} />}
      </div>
      <input
        className="peer sr-only"
        id={idDaEntrada}
        type="file"
        accept="image/jpeg,image/png"
        capture="environment"
        aria-labelledby={`${idDoRotulo} ${idDaAcao}`}
        aria-describedby={`${idDaDica} ${idDoErro}`}
        aria-invalid={ilegivel ? true : undefined}
        onChange={(evento) => {
          void escolher(evento);
        }}
      />
      <label
        className={`inline-flex items-center ${BOTAO_SECUNDARIO_SEM_MARGEM} peer-focus-visible:[outline:2px_solid_transparent] peer-focus-visible:[box-shadow:var(--foco)]`}
        htmlFor={idDaEntrada}
        id={idDaAcao}
      >
        {foto === undefined ? TEXTOS.escolherFoto : TEXTOS.trocarFoto}
      </label>
      <p className={ERRO_DO_CAMPO} id={idDoErro}>
        {ilegivel ? TEXTOS.fotoIlegivel : ""}
      </p>
    </div>
  );
}

/** A prévia desenhada no canvas: sem URL blob:, e escondida do leitor de tela (o nome do campo já diz tudo). */
function Previa({ foto }: { foto: Blob }): ReactElement {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    if (canvas.current !== null) {
      // Uma prévia que não desenha deixa a moldura vazia; a foto reduzida segue valendo para o envio.
      desenharPrevia(canvas.current, foto).catch(() => undefined);
    }
  }, [foto]);
  return <canvas ref={canvas} aria-hidden="true" className="block size-full object-contain" />;
}
