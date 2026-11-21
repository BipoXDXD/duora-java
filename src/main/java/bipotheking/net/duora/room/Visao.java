package bipotheking.net.duora.room;

import bipotheking.net.duora.game.Assento;
import bipotheking.net.duora.game.Estado;
import bipotheking.net.duora.game.Fase;
import bipotheking.net.duora.game.Lugar;
import bipotheking.net.duora.game.Peca;
import bipotheking.net.duora.game.Regras;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * O terraço do ponto de vista de quem pede (JSON do contrato). Campos anuláveis seguem o contrato:
 * {@code parceiro} antes de alguém entrar, {@code convite} fora de A em espera, {@code sinal} e
 * {@code resultado} quando não há.
 */
record Visao(
    Fase fase,
    long versao,
    Voce voce,
    @Nullable Parceiro parceiro,
    @Nullable String convite,
    List<VisaoPeca> pecas,
    Prontos prontos,
    Confirmacao confirmacao,
    @Nullable VisaoSinal sinal,
    @Nullable VisaoResultado resultado) {

  private static final String BLOQUEIO_PORTAO = "ESPERE_PRIMEIRA_PECA";

  record Voce(Assento assento, String nome) {}

  record Parceiro(Assento assento, String nome, boolean conectado) {}

  /** Peças próprias trazem lugares permitidos e bloqueio; as do parceiro, não. */
  sealed interface VisaoPeca permits PecaPropria, PecaDoParceiro {}

  record PecaPropria(
      String peca,
      Assento assento,
      @Nullable String lugar,
      List<String> lugaresPermitidos,
      @Nullable String bloqueio)
      implements VisaoPeca {}

  record PecaDoParceiro(String peca, Assento assento, @Nullable String lugar)
      implements VisaoPeca {}

  record Prontos(boolean voce, boolean parceiro) {}

  record Confirmacao(boolean podeConfirmar, int faltam) {}

  record VisaoSinal(String peca, Assento deAssento) {}

  record VisaoResultado(String secundariaA, String secundariaB, String ladoTelescopio) {}

  @Override
  public String toString() {
    return "Visao[fase=" + fase + ", versao=" + versao + "]";
  }

  static Visao de(Retrato retrato, Assento quem, Instant agora, LimitesSala limites) {
    Estado estado = retrato.estado();
    Assento outro = quem.parceiro();
    return new Visao(
        estado.fase(),
        estado.versao(),
        new Voce(quem, retrato.participante(quem).orElseThrow().apelido().valor()),
        retrato
            .participante(outro)
            .map(p ->
                new Parceiro(outro, p.apelido().valor(), retrato.conectado(outro, agora, limites)))
            .orElse(null),
        conviteVisivel(retrato, quem),
        Arrays.stream(Peca.values()).map(peca -> visaoDa(peca, estado, quem)).toList(),
        new Prontos(estado.pronto(quem), estado.pronto(outro)),
        new Confirmacao(
            estado.fase() == Fase.MONTANDO && estado.composicao().completa(),
            estado.composicao().faltam()),
        estado
            .sinalAtivoEm(agora)
            .map(s -> new VisaoSinal(s.peca().id(), s.deAssento()))
            .orElse(null),
        Regras.resultado(estado)
            .map(r ->
                new VisaoResultado(
                    r.secundariaA().id(), r.secundariaB().id(), r.ladoTelescopio().id()))
            .orElse(null));
  }

  private static @Nullable String conviteVisivel(Retrato retrato, Assento quem) {
    if (quem != Assento.A || retrato.estado().fase() != Fase.AGUARDANDO_PAR) {
      return null;
    }
    return retrato.convite().map(Convite::valor).orElse(null);
  }

  private static VisaoPeca visaoDa(Peca peca, Estado estado, Assento quem) {
    String lugar = estado.composicao().lugarDe(peca).map(Lugar::id).orElse(null);
    if (peca.dono() != quem) {
      return new PecaDoParceiro(peca.id(), peca.dono(), lugar);
    }
    return new PecaPropria(
        peca.id(),
        peca.dono(),
        lugar,
        Regras.lugaresPermitidos(estado, peca).stream().map(Lugar::id).toList(),
        Regras.bloqueadaPeloPortao(estado, peca) ? BLOQUEIO_PORTAO : null);
  }
}
