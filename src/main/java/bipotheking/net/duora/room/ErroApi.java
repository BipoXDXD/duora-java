package bipotheking.net.duora.room;

import java.io.Serial;
import java.util.Optional;

/** Recusa que vira {@code application/problem+json} com {@code codigo} e campos extras. */
final class ErroApi extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final Codigo codigo;
  private final transient Optional<Visao> visao;
  private final transient Optional<MotivoEncerramento> motivo;

  ErroApi(Codigo codigo, String detalhe) {
    this(codigo, detalhe, Optional.empty(), Optional.empty());
  }

  private ErroApi(
      Codigo codigo, String detalhe, Optional<Visao> visao, Optional<MotivoEncerramento> motivo) {
    super(detalhe, null, false, false);
    this.codigo = codigo;
    this.visao = visao;
    this.motivo = motivo;
  }

  static ErroApi comVisao(Codigo codigo, String detalhe, Visao visao) {
    return new ErroApi(codigo, detalhe, Optional.of(visao), Optional.empty());
  }

  static ErroApi encerrada(MotivoEncerramento motivo) {
    return new ErroApi(
        Codigo.SALA_ENCERRADA, motivo.detalhe(), Optional.empty(), Optional.of(motivo));
  }

  Codigo codigo() {
    return codigo;
  }

  Optional<Visao> visao() {
    return visao;
  }

  Optional<MotivoEncerramento> motivo() {
    return motivo;
  }
}
