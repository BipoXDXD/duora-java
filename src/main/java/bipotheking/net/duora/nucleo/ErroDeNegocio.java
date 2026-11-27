package bipotheking.net.duora.nucleo;

import java.io.Serial;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Recusa prevista do domínio: carrega o código, a mensagem para quem usa o app, campos extras e, se
 * couber, quando tentar de novo. Sem stack trace, porque não é falha do servidor.
 */
public class ErroDeNegocio extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient CodigoDeErro codigo;
  private final transient Map<String, Object> extras;
  private final transient @Nullable Duration tentarDeNovoEm;

  public ErroDeNegocio(CodigoDeErro codigo, String mensagem) {
    this(codigo, mensagem, Map.of(), null);
  }

  protected ErroDeNegocio(
      CodigoDeErro codigo,
      String mensagem,
      Map<String, Object> extras,
      @Nullable Duration tentarDeNovoEm) {
    super(mensagem, null, false, false);
    this.codigo = codigo;
    this.extras = Map.copyOf(extras);
    this.tentarDeNovoEm = tentarDeNovoEm;
  }

  public CodigoDeErro codigo() {
    return codigo;
  }

  /** Campos a mais do corpo do problema, em cópia imutável. */
  public Map<String, Object> extras() {
    return extras;
  }

  public Optional<Duration> tentarDeNovoEm() {
    return Optional.ofNullable(tentarDeNovoEm);
  }
}
