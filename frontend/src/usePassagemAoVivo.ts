// A passagem do crepúsculo para a noite vista ao vivo, não ao recarregar já revelado. É o gatilho
// do acorde da revelação e das notas das peças.

import { useState } from "react";
import type { Momento } from "./dominio/dados";
import type { EstadoDaSessao } from "./sessao/sessao";

interface Passagem {
  /** O último momento mostrado com a sessão já carregada; null antes do primeiro. */
  readonly visto: Momento | null;
  readonly aoVivo: boolean;
}

/**
 * true da passagem ao vivo para a noite até o momento mudar de novo. A tela "carregando" não conta
 * como momento visto: quem abre a página já na noite revelada não viu a passagem.
 */
export function usePassagemAoVivo(estado: EstadoDaSessao): boolean {
  const { tela, momento } = estado;
  const [passagem, setPassagem] = useState<Passagem>({ visto: null, aoVivo: false });
  if (tela !== "carregando" && momento !== passagem.visto) {
    // Ajuste de estado no render (o "valor do render anterior" do React), sem efeito extra.
    const nova = { visto: momento, aoVivo: passagem.visto === "crepusculo" && momento === "noite" };
    setPassagem(nova);
    return nova.aoVivo;
  }
  return passagem.aoVivo;
}
