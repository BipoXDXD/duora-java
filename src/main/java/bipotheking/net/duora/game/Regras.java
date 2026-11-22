package bipotheking.net.duora.game;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Regras de um cenário como funções puras: recebem o cenário, o estado e o pedido e devolvem uma
 * {@link Decisao}. O servidor é a autoridade; nada aqui conhece HTTP, relógio de sistema ou
 * concorrência.
 */
public final class Regras {

  private Regras() {}

  public static Decisao parceiroEntrou(Cenario cenario, Estado estado) {
    if (estado.fase() != Fase.AGUARDANDO_PAR) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    return aceitar(
        new Estado(
            Fase.MONTANDO, estado.versao(), estado.composicao(), estado.prontos(), estado.sinal()));
  }

  /**
   * Coloca, move ou retira (destino vazio) uma peça própria. Pedir o lugar em que a peça já está é
   * aceito sem mudança, qualquer que seja a versão.
   */
  public static Decisao colocar(
      Cenario cenario,
      Estado estado,
      Assento quem,
      Peca peca,
      Optional<Lugar> destino,
      long versaoEsperada) {
    if (peca.dono() != quem) {
      return recusar(Recusa.PECA_ALHEIA);
    }
    if (destino.isPresent() && !peca.aceita(destino.get())) {
      return recusar(Recusa.LUGAR_INVALIDO);
    }
    Composicao atual = estado.composicao();
    if (atual.lugarDe(peca).equals(destino)) {
      return aceitar(estado);
    }
    if (estado.fase() != Fase.MONTANDO) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    Composicao semAPeca = liberarEspacoPara(cenario, atual, peca);
    if (destino.isPresent() && semAPeca.ocupante(destino.get()).isPresent()) {
      return recusar(Recusa.LUGAR_OCUPADO);
    }
    if (versaoEsperada != estado.versao()) {
      return recusar(Recusa.VERSAO_OBSOLETA);
    }
    Composicao nova = destino.map(lugar -> semAPeca.com(peca, lugar)).orElse(semAPeca);
    if (violaPortao(cenario, atual, nova, quem)) {
      return recusar(Recusa.ESPERE_PRIMEIRA_PECA);
    }
    Optional<Sinal> sinal =
        estado.sinal().filter(s -> nova.lugarDe(s.peca()).equals(atual.lugarDe(s.peca())));
    return aceitar(new Estado(Fase.MONTANDO, estado.versao() + 1, nova, Set.of(), sinal));
  }

  /**
   * Sinaliza uma peça colocada do parceiro por {@link Cenario#duracaoSinal()}. Não muda a versão.
   */
  public static Decisao sinalizar(
      Cenario cenario, Estado estado, Assento quem, Peca peca, Instant agora) {
    if (peca.dono() == quem) {
      return recusar(Recusa.PECA_ALHEIA);
    }
    if (estado.fase() != Fase.MONTANDO || !estado.composicao().colocada(peca)) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    Sinal sinal = new Sinal(peca, quem, agora.plus(cenario.duracaoSinal()));
    return aceitar(
        new Estado(
            estado.fase(),
            estado.versao(),
            estado.composicao(),
            estado.prontos(),
            Optional.of(sinal)));
  }

  /**
   * Confirma (ou desfaz a confirmação de) a composição de uma versão. As duas confirmações na mesma
   * versão revelam o céu.
   */
  public static Decisao confirmar(
      Cenario cenario, Estado estado, Assento quem, boolean pronto, long versao) {
    if (estado.pronto(quem) == pronto) {
      return aceitar(estado);
    }
    if (estado.fase() != Fase.MONTANDO) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    if (versao != estado.versao()) {
      return recusar(Recusa.VERSAO_OBSOLETA);
    }
    if (pronto && !cenario.completa(estado.composicao())) {
      return recusar(Recusa.INCOMPLETA);
    }
    Set<Assento> prontos = EnumSet.noneOf(Assento.class);
    prontos.addAll(estado.prontos());
    if (pronto) {
      prontos.add(quem);
    } else {
      prontos.remove(quem);
    }
    Fase fase = prontos.size() == Assento.values().length ? Fase.REVELADO : Fase.MONTANDO;
    return aceitar(new Estado(fase, estado.versao(), estado.composicao(), prontos, estado.sinal()));
  }

  /** Volta da revelação para a montagem, zerando as confirmações. */
  public static Decisao ajustar(Cenario cenario, Estado estado) {
    if (estado.fase() != Fase.REVELADO) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    return aceitar(
        new Estado(
            Fase.MONTANDO, estado.versao() + 1, estado.composicao(), Set.of(), estado.sinal()));
  }

  /** Lugares para onde a peça própria pode ir agora (vazio fora de {@link Fase#MONTANDO}). */
  public static List<Lugar> lugaresPermitidos(Cenario cenario, Estado estado, Peca peca) {
    if (estado.fase() != Fase.MONTANDO) {
      return List.of();
    }
    Composicao semAPeca = liberarEspacoPara(cenario, estado.composicao(), peca);
    Optional<Lugar> atual = estado.composicao().lugarDe(peca);
    return cenario.da(peca.zona()).stream()
        .filter(lugar -> atual.isEmpty() || !atual.get().equals(lugar))
        .filter(lugar -> semAPeca.ocupante(lugar).isEmpty())
        .toList();
  }

  /** Se colocar a peça agora seria barrado pelo portão da primeira peça. */
  public static boolean bloqueadaPeloPortao(Cenario cenario, Estado estado, Peca peca) {
    Composicao atual = estado.composicao();
    if (estado.fase() != Fase.MONTANDO || atual.colocada(peca)) {
      return false;
    }
    Composicao simulada =
        liberarEspacoPara(cenario, atual, peca).com(peca, cenario.da(peca.zona()).get(0));
    return violaPortao(cenario, atual, simulada, peca.dono());
  }

  /** O {@link Desfecho} do cenário sobre a composição revelada; vazio fora da revelação. */
  public static Optional<Resultado> resultado(Cenario cenario, Estado estado) {
    if (estado.fase() != Fase.REVELADO) {
      return Optional.empty();
    }
    return Optional.of(cenario.desfecho().de(estado.composicao(), cenario));
  }

  /** Tira a própria peça e, se ela é secundária, a outra secundária do mesmo dono (troca). */
  private static Composicao liberarEspacoPara(Cenario cenario, Composicao composicao, Peca peca) {
    Composicao resultado = composicao.sem(peca);
    if (!peca.obrigatoria()) {
      for (Peca irma : cenario.secundariasDe(peca.dono())) {
        resultado = resultado.sem(irma);
      }
    }
    return resultado;
  }

  /**
   * Portão da primeira peça: enquanto o parceiro não colocou nenhuma, cada um tem no máximo {@link
   * Cenario#limiteAntesDoParceiro()}. Mover ou trocar o que já está colocado não aumenta a contagem
   * e é sempre permitido.
   */
  private static boolean violaPortao(
      Cenario cenario, Composicao antes, Composicao depois, Assento quem) {
    int minhasDepois = depois.colocadas(quem);
    return depois.colocadas(quem.parceiro()) == 0
        && minhasDepois > cenario.limiteAntesDoParceiro()
        && minhasDepois > antes.colocadas(quem);
  }

  private static Decisao aceitar(Estado estado) {
    return new Decisao.Aceita(estado);
  }

  private static Decisao recusar(Recusa recusa) {
    return new Decisao.Recusada(recusa);
  }
}
