import type { Visao } from "../dominio/visao";

const MARCA = "marca w-[10px] h-[10px] rounded-[50%]";
const NOME = "nome text-marfim font-semibold";
const LINHA = "flex items-center gap-(--e-2)";

/** Quem está no terraço: você e, quando chega, o parceiro com o estado da conexão. */
export function Par({ visao }: { visao: Visao }) {
  const { parceiro } = visao;
  return (
    <ul className="par flex flex-wrap gap-x-(--e-5) gap-y-(--e-2) m-0 p-0 list-none text-texto-2" id="par">
      <li className={LINHA}>
        <span className={`${MARCA} marca-voce bg-ambar`} aria-hidden="true"></span>
        <span className={NOME}>{visao.voce.nome}</span> (você)
      </li>
      {parceiro && (
        <li className={LINHA}>
          <span className={`${MARCA} bg-verde-agua`} aria-hidden="true"></span>
          <span className={NOME}>{parceiro.nome}</span>
          <span className="conexao">{parceiro.conectado ? "" : " · sem sinal"}</span>
        </li>
      )}
    </ul>
  );
}
