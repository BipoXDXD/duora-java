package bipotheking.net.duora.room;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.io.Serial;
import java.util.Map;

/** Recusa do terraço; a visão e o motivo do encerramento viajam como campos extras. */
final class ErroApi extends ErroDeNegocio {

  @Serial private static final long serialVersionUID = 1L;

  private final Codigo codigo;

  ErroApi(Codigo codigo, String detalhe) {
    this(codigo, detalhe, Map.of());
  }

  private ErroApi(Codigo codigo, String detalhe, Map<String, Object> extras) {
    super(codigo, detalhe, extras, null);
    this.codigo = codigo;
  }

  static ErroApi comVisao(Codigo codigo, String detalhe, Visao visao) {
    return new ErroApi(codigo, detalhe, Map.of("visao", visao));
  }

  static ErroApi encerrada(MotivoEncerramento motivo) {
    return new ErroApi(Codigo.SALA_ENCERRADA, motivo.detalhe(), Map.of("motivo", motivo.name()));
  }

  @Override
  public Codigo codigo() {
    return codigo;
  }
}
