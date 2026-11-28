import type { ReactElement } from "react";
import { Link, NavLink, Outlet } from "react-router";
import { NAVEGACAO_DO_PERFIL } from "./textos";

const SECOES = [
  { para: "/perfil", nome: NAVEGACAO_DO_PERFIL.meuPerfil },
  { para: "/perfil/preferencias", nome: NAVEGACAO_DO_PERFIL.preferencias },
  { para: "/perfil/namoro", nome: NAVEGACAO_DO_PERFIL.namoro },
  { para: "/perfil/orientacao", nome: NAVEGACAO_DO_PERFIL.orientacao },
] as const;

/**
 * A seção atual não depende só da cor: o peso e o traço sob o nome mudam juntos (o traço vira
 * CanvasText no alto contraste). O traço encosta na linha da lista, como uma aba.
 */
const LINK_DA_SECAO = [
  "inline-flex items-center min-h-[44px] -mb-px border-b-2 border-transparent text-[1.0625rem] text-texto-2",
  "no-underline hover:text-marfim [transition:color_var(--transicao)]",
  "aria-[current=page]:border-ambar aria-[current=page]:text-marfim aria-[current=page]:font-semibold",
  "forced-colors:aria-[current=page]:border-[CanvasText]",
].join(" ");

const LINK_DE_SAIDA =
  "inline-flex items-center min-h-[44px] text-[0.9375rem] text-texto-2 underline underline-offset-4 hover:text-marfim";

/**
 * Moldura das telas de perfil, dentro da casca de conta: a navegação entre as quatro seções e a
 * saída para Minha conta e para o terraço; a tela entra logo abaixo. O terraço não é rota da casca,
 * então "Voltar ao terraço" carrega a página inteira.
 */
export function LayoutDoPerfil(): ReactElement {
  return (
    <>
      <nav aria-label={NAVEGACAO_DO_PERFIL.rotulo} className="mb-(--e-7) grid gap-(--e-2)">
        <ul className="m-0 p-0 list-none flex flex-wrap gap-x-(--e-5) border-b border-noite-700">
          {SECOES.map(({ para, nome }) => (
            <li key={para}>
              <NavLink to={para} end className={LINK_DA_SECAO}>
                {nome}
              </NavLink>
            </li>
          ))}
        </ul>
        <ul className="m-0 p-0 list-none flex flex-wrap gap-x-(--e-5)">
          <li>
            <Link to="/conta" className={LINK_DE_SAIDA}>
              {NAVEGACAO_DO_PERFIL.minhaConta}
            </Link>
          </li>
          <li>
            <a href="/" className={LINK_DE_SAIDA}>
              {NAVEGACAO_DO_PERFIL.voltarAoTerraco}
            </a>
          </li>
        </ul>
      </nav>
      <Outlet />
    </>
  );
}
