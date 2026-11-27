package bipotheking.net.duora.conta.infra;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * STS em memória: guarda o último pedido de {@code AssumeRole} e responde a credencial combinada ou
 * lança a falha combinada. Nada sai da JVM; qualquer outra operação cai no {@code
 * UnsupportedOperationException} padrão da interface.
 */
final class StsFalso implements StsClient {

  private final @Nullable Credentials credencial;
  private final @Nullable RuntimeException falha;
  private @Nullable AssumeRoleRequest pedido;
  private boolean fechado;

  private StsFalso(@Nullable Credentials credencial, @Nullable RuntimeException falha) {
    this.credencial = credencial;
    this.falha = falha;
  }

  static StsFalso respondendo(Credentials credencial) {
    return new StsFalso(credencial, null);
  }

  static StsFalso falhando(RuntimeException falha) {
    return new StsFalso(null, falha);
  }

  @Override
  public AssumeRoleResponse assumeRole(AssumeRoleRequest pedido) {
    this.pedido = pedido;
    if (falha != null) {
      throw falha;
    }
    return AssumeRoleResponse.builder().credentials(credencial).build();
  }

  /** O último pedido recebido; falha o teste se nenhum chegou. */
  AssumeRoleRequest pedido() {
    if (pedido == null) {
      throw new AssertionError("AssumeRole não foi chamado");
    }
    return pedido;
  }

  boolean fechado() {
    return fechado;
  }

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void close() {
    fechado = true;
  }
}
