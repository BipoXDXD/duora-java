package bipotheking.net.duora.game;

import java.time.Duration;
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

  /**
   * O par entrou pelo convite: começa a montagem e, se o cenário declara abertura, ela começa
   * {@code agora}.
   */
  public static Decisao parceiroEntrou(Cenario cenario, Estado estado, Instant agora) {
    if (!(estado.fase() instanceof Fase.AguardandoPar)) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    Optional<Abertura> abertura = cenario.abertura().map(definicao -> Abertura.iniciada(agora));
    return aceitar(comFase(estado, new Fase.Montando(abertura)));
  }

  /**
   * Toque de quem na abertura ativa: se o outro tocou há no máximo {@link
   * DefinicaoDeAbertura#janela()}, as lanternas acendem e a abertura conclui. O máximo vale antes
   * do toque: tocar depois dele conclui apagada (como {@link #avancarAbertura}), sem acender. Tocar
   * depois da conclusão não muda nada. Nunca muda a versão.
   */
  public static Decisao tocarAbertura(Cenario cenario, Estado estado, Assento quem, Instant agora) {
    if (!(estado.fase() instanceof Fase.Montando montando)) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    Optional<Abertura> abertura = montando.abertura();
    Optional<DefinicaoDeAbertura> definicao = cenario.abertura();
    if (abertura.isEmpty() || definicao.isEmpty()) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    Abertura tocada =
        abertura
            .get()
            .avancada(agora, definicao.get().maximo())
            .tocada(quem, agora, definicao.get().janela());
    if (tocada == abertura.get()) {
      return aceitar(estado);
    }
    return aceitar(comFase(estado, new Fase.Montando(Optional.of(tocada))));
  }

  /**
   * Conclui apagada a abertura ativa que chegou a {@link DefinicaoDeAbertura#maximo()}. Total: em
   * qualquer outro caso devolve a mesma instância do estado.
   */
  public static Estado avancarAbertura(Cenario cenario, Estado estado, Instant agora) {
    if (!(estado.fase() instanceof Fase.Montando montando)) {
      return estado;
    }
    Optional<Abertura> abertura = montando.abertura();
    Optional<Duration> maximo = cenario.abertura().map(DefinicaoDeAbertura::maximo);
    if (abertura.isEmpty() || maximo.isEmpty()) {
      return estado;
    }
    Abertura avancada = abertura.get().avancada(agora, maximo.get());
    if (avancada == abertura.get()) {
      return estado;
    }
    return comFase(estado, new Fase.Montando(Optional.of(avancada)));
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
    if (emAbertura(estado)) {
      return recusar(Recusa.ABERTURA_EM_ANDAMENTO);
    }
    if (!(estado.fase() instanceof Fase.Montando montando)) {
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
        estado
            .sinal()
            .filter(s -> nova.lugarDe(s.peca()).equals(atual.lugarDe(s.peca())))
            .filter(s -> nova.ocupante(s.destino()).isEmpty());
    return aceitar(new Estado(montando, estado.versao() + 1, nova, Set.of(), sinal));
  }

  /**
   * Sugere levar uma peça colocada do parceiro a um lugar livre da mesma zona, por {@link
   * Cenario#duracaoSinal()}. O novo sinal substitui o anterior, de quem for. Não muda a versão.
   */
  public static Decisao sinalizar(
      Cenario cenario, Estado estado, Assento quem, Peca peca, Lugar destino, Instant agora) {
    if (peca.dono() == quem) {
      return recusar(Recusa.PECA_ALHEIA);
    }
    if (emAbertura(estado)) {
      return recusar(Recusa.ABERTURA_EM_ANDAMENTO);
    }
    Composicao composicao = estado.composicao();
    Optional<Lugar> atual = composicao.lugarDe(peca);
    if (!(estado.fase() instanceof Fase.Montando) || atual.isEmpty()) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    if (!cenario.da(peca.zona()).contains(destino) || atual.get().equals(destino)) {
      return recusar(Recusa.LUGAR_INVALIDO);
    }
    if (composicao.ocupante(destino).isPresent()) {
      return recusar(Recusa.LUGAR_OCUPADO);
    }
    Sinal sinal = new Sinal(peca, quem, destino, agora.plus(cenario.duracaoSinal()));
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
    if (emAbertura(estado)) {
      return recusar(Recusa.ABERTURA_EM_ANDAMENTO);
    }
    if (!(estado.fase() instanceof Fase.Montando montando)) {
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
    Fase fase = prontos.size() == Assento.values().length ? new Fase.Revelado() : montando;
    return aceitar(new Estado(fase, estado.versao(), estado.composicao(), prontos, estado.sinal()));
  }

  /**
   * Pede para voltar da revelação à montagem. Sem pedido pendente, o de quem pede fica gravado; com
   * o pedido do outro pendente, pedir é aceitar (pedidos cruzados viram aceite): volta à montagem
   * numa versão nova, sem confirmações, sinal nem abertura (ela acontece uma vez por encontro).
   * Repetir o próprio pedido não muda nada.
   */
  public static Decisao pedirAjuste(Cenario cenario, Estado estado, Assento quem) {
    if (!(estado.fase() instanceof Fase.Revelado revelado)) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    Optional<Assento> pedido = revelado.pedidoDeAjuste();
    if (pedido.isEmpty()) {
      return aceitar(naRevelacao(estado, Optional.of(quem)));
    }
    if (pedido.get() == quem) {
      return aceitar(estado);
    }
    return aceitar(
        new Estado(
            new Fase.Montando(),
            estado.versao() + 1,
            estado.composicao(),
            Set.of(),
            Optional.empty()));
  }

  /**
   * Limpa o pedido de ajuste pendente, venha de quem vier: recusa o do outro ou cancela o próprio.
   * Sem pedido, não muda nada.
   */
  public static Decisao recusarAjuste(Cenario cenario, Estado estado, Assento quem) {
    if (!(estado.fase() instanceof Fase.Revelado revelado)) {
      return recusar(Recusa.FASE_INVALIDA);
    }
    if (revelado.pedidoDeAjuste().isEmpty()) {
      return aceitar(estado);
    }
    return aceitar(naRevelacao(estado, Optional.empty()));
  }

  /**
   * Lugares para onde se pode sugerir levar uma peça colocada: os livres da zona dela, sem o atual,
   * na ordem do cenário. Vazio fora de {@link Fase.Montando}, durante a abertura e com a peça fora
   * da cena.
   */
  public static List<Lugar> destinosSugeriveis(Cenario cenario, Estado estado, Peca peca) {
    Composicao composicao = estado.composicao();
    Optional<Lugar> atual = composicao.lugarDe(peca);
    if (!(estado.fase() instanceof Fase.Montando) || emAbertura(estado) || atual.isEmpty()) {
      return List.of();
    }
    return cenario.da(peca.zona()).stream()
        .filter(lugar -> !lugar.equals(atual.get()))
        .filter(lugar -> composicao.ocupante(lugar).isEmpty())
        .toList();
  }

  /**
   * Lugares para onde a peça própria pode ir agora (vazio fora de {@link Fase.Montando} e durante a
   * abertura).
   */
  public static List<Lugar> lugaresPermitidos(Cenario cenario, Estado estado, Peca peca) {
    if (!(estado.fase() instanceof Fase.Montando) || emAbertura(estado)) {
      return List.of();
    }
    Composicao semAPeca = liberarEspacoPara(cenario, estado.composicao(), peca);
    Optional<Lugar> atual = estado.composicao().lugarDe(peca);
    return cenario.da(peca.zona()).stream()
        .filter(lugar -> atual.isEmpty() || !atual.get().equals(lugar))
        .filter(lugar -> semAPeca.ocupante(lugar).isEmpty())
        .toList();
  }

  /**
   * Se colocar a peça agora seria barrado pelo portão da primeira peça. Não considera a abertura:
   * quem chama confere {@link #lugaresPermitidos} (vazio durante a abertura) ou o {@link #colocar}
   * (que a recusa antes do portão).
   */
  public static boolean bloqueadaPeloPortao(Cenario cenario, Estado estado, Peca peca) {
    Composicao atual = estado.composicao();
    if (!(estado.fase() instanceof Fase.Montando) || atual.colocada(peca)) {
      return false;
    }
    Composicao simulada =
        liberarEspacoPara(cenario, atual, peca).com(peca, cenario.da(peca.zona()).get(0));
    return violaPortao(cenario, atual, simulada, peca.dono());
  }

  /**
   * Se confirmar agora seria aceito por completude: em {@link Fase.Montando}, sem abertura ativa e
   * com a composição completa.
   */
  public static boolean podeConfirmar(Cenario cenario, Estado estado) {
    return estado.fase() instanceof Fase.Montando montando
        && !montando.aberturaAtiva()
        && cenario.completa(estado.composicao());
  }

  /** O {@link Desfecho} do cenário sobre a composição revelada; vazio fora da revelação. */
  public static Optional<Resultado> resultado(Cenario cenario, Estado estado) {
    if (!(estado.fase() instanceof Fase.Revelado)) {
      return Optional.empty();
    }
    return Optional.of(cenario.desfecho().de(estado.composicao(), cenario));
  }

  private static boolean emAbertura(Estado estado) {
    return estado.fase() instanceof Fase.Montando montando && montando.aberturaAtiva();
  }

  private static Estado comFase(Estado estado, Fase fase) {
    return new Estado(fase, estado.versao(), estado.composicao(), estado.prontos(), estado.sinal());
  }

  private static Estado naRevelacao(Estado estado, Optional<Assento> pedidoDeAjuste) {
    return comFase(estado, new Fase.Revelado(pedidoDeAjuste));
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
