package bipotheking.net.duora.sala;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * O estado de quem está na fila, como vai no JSON de {@code /api/fila} e no evento {@code
 * fila.estado}: sempre as seis chaves. Posição e estimativa só em {@code AGUARDANDO_VAGA} (a
 * estimativa pode faltar nela); encontro e cenário só, e sempre, em {@code PAREADO}.
 *
 * @param desde quando entrou na fila ou, pareada, quando o par se formou
 */
public record EstadoNaFila(
    SituacaoNaFila situacao,
    Instant desde,
    @Nullable Integer posicao,
    @Nullable Integer estimativaMinutos,
    @Nullable String encontroId,
    @Nullable String cenario) {

  public EstadoNaFila {
    Objects.requireNonNull(situacao, "situacao");
    Objects.requireNonNull(desde, "desde");
    boolean vaga = situacao == SituacaoNaFila.AGUARDANDO_VAGA;
    boolean pareado = situacao == SituacaoNaFila.PAREADO;
    if (vaga != (posicao != null) || (!vaga && estimativaMinutos != null)) {
      throw new IllegalArgumentException("posição e estimativa só aguardando vaga");
    }
    if (pareado != (encontroId != null) || pareado != (cenario != null)) {
      throw new IllegalArgumentException("encontro e cenário só, e sempre, pareado");
    }
  }

  public static EstadoNaFila aguardandoCompativel(Instant desde) {
    return new EstadoNaFila(SituacaoNaFila.AGUARDANDO_COMPATIVEL, desde, null, null, null, null);
  }

  public static EstadoNaFila aguardandoVaga(
      Instant desde, int posicao, @Nullable Integer estimativaMinutos) {
    return new EstadoNaFila(
        SituacaoNaFila.AGUARDANDO_VAGA, desde, posicao, estimativaMinutos, null, null);
  }

  public static EstadoNaFila pareado(Instant desde, String encontroId, String cenario) {
    return new EstadoNaFila(SituacaoNaFila.PAREADO, desde, null, null, encontroId, cenario);
  }
}
