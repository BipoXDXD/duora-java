import { Moldura } from "./Moldura";
import { Topo } from "./Topo";

function nada(): void {}

/**
 * Tela de quando o cenário não carrega: a moldura e o céu do esqueleto do index.html, o topo sem
 * "Sair" e o aviso no painel. O detalhe técnico do erro fica no console.
 */
export function ErroDeCenario() {
  return (
    <Moldura momento="crepusculo" confirmandoSaida={false} ceuPintado={false}>
      <Topo
        mostrarSair={false}
        nomeParceiro={null}
        confirmando={false}
        enviandoSaida={false}
        aoAlternarSaida={nada}
        aoContinuar={nada}
        aoEncerrar={nada}
      />
      <section className="cena" aria-hidden="true">
        <div className="ceu"></div>
      </section>
      <main className="painel [grid-area:painel] flex flex-col gap-(--e-5) px-(--e-6) pt-(--e-5) pb-(--e-6) bg-noite-800 celular:px-(--e-4) celular-deitado:px-(--e-4)">
        <p role="alert" className="m-0 text-[1.25rem] leading-[1.35] max-w-[32ch] celular:text-[1.125rem]">
          Não foi possível carregar o cenário. Recarregue a página.
        </p>
      </main>
    </Moldura>
  );
}
