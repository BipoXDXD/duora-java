package bipotheking.net.duora.room;

import bipotheking.net.duora.game.Abertura;
import bipotheking.net.duora.game.Assento;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.DefinicaoDeAbertura;
import bipotheking.net.duora.game.Estado;
import bipotheking.net.duora.game.Fase;
import bipotheking.net.duora.game.Lugar;
import bipotheking.net.duora.game.Peca;
import bipotheking.net.duora.game.Regras;
import bipotheking.net.duora.game.Resultado;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A sala do ponto de vista de quem pede (JSON do contrato), com o id do cenário à frente. Campos
 * anuláveis seguem o contrato: {@code parceiro} antes de alguém entrar, {@code convite} fora de A
 * em espera, {@code sinal}, {@code resultado}, {@code ajuste} e {@code abertura} quando não há.
 * {@code conviteDaSala} é o resumo SHA-256 (hex) do convite, nos dois assentos e em toda fase: o
 * front reconhece o próprio link sem receber o token depois do pareamento.
 */
record Visao(
    String cenario,
    String fase,
    long versao,
    Voce voce,
    @Nullable Parceiro parceiro,
    @Nullable String convite,
    List<VisaoPeca> pecas,
    Prontos prontos,
    Confirmacao confirmacao,
    @Nullable VisaoSinal sinal,
    @Nullable Map<String, String> resultado,
    @Nullable VisaoAjuste ajuste,
    @Nullable VisaoAbertura abertura,
    String conviteDaSala) {

  private static final String BLOQUEIO_PORTAO = "ESPERE_PRIMEIRA_PECA";

  record Voce(Assento assento, String nome) {}

  record Parceiro(Assento assento, String nome, boolean conectado) {}

  /** Peças próprias trazem lugares permitidos e bloqueio; as do parceiro, destinos sugeríveis. */
  sealed interface VisaoPeca permits PecaPropria, PecaDoParceiro {}

  record PecaPropria(
      String peca,
      Assento assento,
      @Nullable String lugar,
      List<String> lugaresPermitidos,
      @Nullable String bloqueio)
      implements VisaoPeca {}

  record PecaDoParceiro(
      String peca, Assento assento, @Nullable String lugar, List<String> destinosSugeriveis)
      implements VisaoPeca {}

  record Prontos(boolean voce, boolean parceiro) {}

  record Confirmacao(boolean podeConfirmar, int faltam) {}

  record VisaoSinal(String peca, Assento deAssento, String lugar) {}

  /** Pedido de ajuste pendente na revelação. */
  record VisaoAjuste(Assento deAssento) {}

  /**
   * A abertura do encontro na montagem, ativa ou concluída.
   *
   * @param voceTocou se quem pede tocou há no máximo a janela do cenário
   * @param outroTocou se o parceiro tocou há no máximo a janela do cenário
   */
  record VisaoAbertura(boolean ativa, boolean voceTocou, boolean outroTocou, boolean acesa) {}

  @Override
  public String toString() {
    return "Visao[fase=" + fase + ", versao=" + versao + "]";
  }

  static Visao de(
      Cenario cenario,
      Retrato retrato,
      String resumoConvite,
      Assento quem,
      Instant agora,
      LimitesSala limites) {
    Estado estado = retrato.estado();
    Assento outro = quem.parceiro();
    return new Visao(
        cenario.id(),
        estado.fase().nome(),
        estado.versao(),
        new Voce(quem, retrato.participante(quem).orElseThrow().apelido().valor()),
        retrato
            .participante(outro)
            .map(p ->
                new Parceiro(outro, p.apelido().valor(), retrato.conectado(outro, agora, limites)))
            .orElse(null),
        conviteVisivel(retrato, quem),
        cenario.pecas().stream().map(peca -> visaoDa(cenario, peca, estado, quem)).toList(),
        new Prontos(estado.pronto(quem), estado.pronto(outro)),
        new Confirmacao(Regras.podeConfirmar(cenario, estado), cenario.faltam(estado.composicao())),
        // O sinal só vale na montagem: na revelação, a sugestão não é acionável.
        estado.fase() instanceof Fase.Montando
            ? estado
                .sinalAtivoEm(agora)
                .map(s -> new VisaoSinal(s.peca().id(), s.deAssento(), s.destino().id()))
                .orElse(null)
            : null,
        Regras.resultado(cenario, estado).map(Resultado::valores).orElse(null),
        ajuste(estado),
        abertura(cenario, estado, quem, agora),
        resumoConvite);
  }

  private static @Nullable VisaoAbertura abertura(
      Cenario cenario, Estado estado, Assento quem, Instant agora) {
    if (!(estado.fase() instanceof Fase.Montando montando)) {
      return null;
    }
    Optional<Duration> janela = cenario.abertura().map(DefinicaoDeAbertura::janela);
    return montando
        .abertura()
        .flatMap(abertura -> janela.map(j -> visaoDaAbertura(abertura, j, quem, agora)))
        .orElse(null);
  }

  private static VisaoAbertura visaoDaAbertura(
      Abertura abertura, Duration janela, Assento quem, Instant agora) {
    return new VisaoAbertura(
        abertura.ativa(),
        tocouNaJanela(abertura, quem, janela, agora),
        tocouNaJanela(abertura, quem.parceiro(), janela, agora),
        abertura.acesa());
  }

  /** Toque há no máximo {@code janela} (inclusive), como na regra do acender junto. */
  private static boolean tocouNaJanela(
      Abertura abertura, Assento assento, Duration janela, Instant agora) {
    Instant toque = abertura.toques().get(assento);
    return toque != null && !toque.plus(janela).isBefore(agora);
  }

  private static @Nullable VisaoAjuste ajuste(Estado estado) {
    if (!(estado.fase() instanceof Fase.Revelado revelado)) {
      return null;
    }
    return revelado.pedidoDeAjuste().map(VisaoAjuste::new).orElse(null);
  }

  private static @Nullable String conviteVisivel(Retrato retrato, Assento quem) {
    if (quem != Assento.A || !(retrato.estado().fase() instanceof Fase.AguardandoPar)) {
      return null;
    }
    return retrato.convite().map(Convite::valor).orElse(null);
  }

  private static VisaoPeca visaoDa(Cenario cenario, Peca peca, Estado estado, Assento quem) {
    String lugar = estado.composicao().lugarDe(peca).map(Lugar::id).orElse(null);
    if (peca.dono() != quem) {
      return new PecaDoParceiro(
          peca.id(),
          peca.dono(),
          lugar,
          Regras.destinosSugeriveis(cenario, estado, peca).stream().map(Lugar::id).toList());
    }
    return new PecaPropria(
        peca.id(),
        peca.dono(),
        lugar,
        Regras.lugaresPermitidos(cenario, estado, peca).stream().map(Lugar::id).toList(),
        Regras.bloqueadaPeloPortao(cenario, estado, peca) ? BLOQUEIO_PORTAO : null);
  }
}
