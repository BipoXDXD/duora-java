import { useRef, useState, type SubmitEvent } from "react";
import { validarApelido } from "../dominio/apelido";
import { BOTAO_PRIMARIO, CAMPO, DICA, ENTRADA_DE_TEXTO, ROTULO, TELA } from "./estilos";

interface PropsEntrada {
  /** A URL trouxe #convite=: a ação vira "Entrar no terraço". */
  comConvite: boolean;
  enviando: boolean;
  /** Erro do servidor para o apelido; "" quando não há. */
  erro: string;
  aoEnviar: (apelido: string) => void;
  /** Digitou um apelido válido: o erro do servidor (se houver) deixa de valer. */
  aoCorrigir: () => void;
}

/**
 * O erro mostrado é o do servidor até o campo decidir outro: a validação local o troca, e digitar
 * um apelido válido o apaga. Uma resposta nova do servidor (erro diferente ou fim de um envio)
 * devolve a palavra ao servidor, mesmo que o texto seja o mesmo de antes.
 */
function useErroDoApelido(erro: string, enviando: boolean) {
  const [doCampo, setDoCampo] = useState<string | null>(null);
  const [anterior, setAnterior] = useState({ erro, enviando });
  if (anterior.erro !== erro || anterior.enviando !== enviando) {
    setAnterior({ erro, enviando });
    if (anterior.erro !== erro || (anterior.enviando && !enviando)) {
      setDoCampo(null);
    }
  }
  return [doCampo ?? erro, setDoCampo] as const;
}

export function Entrada({ comConvite, enviando, erro, aoEnviar, aoCorrigir }: PropsEntrada) {
  const refApelido = useRef<HTMLInputElement>(null);
  const [erroMostrado, mostrarErro] = useErroDoApelido(erro, enviando);
  const acao = comConvite ? "Entrar no terraço" : "Criar terraço";

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault();
    const campo = refApelido.current;
    if (enviando || campo === null) {
      return;
    }
    const apelido = campo.value.trim();
    const motivo = validarApelido(apelido);
    mostrarErro(motivo);
    if (motivo) {
      campo.focus();
      return;
    }
    aoEnviar(apelido);
  }

  function aoDigitar() {
    const campo = refApelido.current;
    if (erroMostrado && campo !== null && !validarApelido(campo.value.trim())) {
      mostrarErro("");
      aoCorrigir();
    }
  }

  return (
    <form className={`entrada ${TELA}`} id="entrada" noValidate onSubmit={enviar}>
      <div className={CAMPO}>
        <label className={ROTULO} htmlFor="apelido">
          Seu apelido
        </label>
        <input
          ref={refApelido}
          className={ENTRADA_DE_TEXTO}
          id="apelido"
          name="apelido"
          type="text"
          // maxLength conta unidades UTF-16: 20 caracteres (pontos de código, como validarApelido e o
          // servidor contam) cabem em até 40. O limite de verdade fica na validação.
          maxLength={40}
          autoComplete="nickname"
          spellCheck={false}
          required
          aria-describedby="apelido-dica apelido-erro"
          aria-invalid={erroMostrado ? true : undefined}
          onInput={aoDigitar}
        />
        <p className={DICA} id="apelido-dica">
          De 1 a 20 caracteres. A outra pessoa vê este nome.
        </p>
        <p className="erro-campo m-0 text-[0.875rem] leading-[1.45] text-coral empty:hidden" id="apelido-erro">
          {erroMostrado}
        </p>
      </div>
      <button type="submit" className={BOTAO_PRIMARIO} id="entrar" aria-disabled={enviando ? true : undefined}>
        {enviando ? "Enviando…" : acao}
      </button>
    </form>
  );
}
