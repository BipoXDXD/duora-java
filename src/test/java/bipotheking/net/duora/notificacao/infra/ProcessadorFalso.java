package bipotheking.net.duora.notificacao.infra;

import bipotheking.net.duora.notificacao.ProcessadorDeOutbox;
import bipotheking.net.duora.nucleo.Correlacao;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Processador de teste: anota cada chamada, roda um gancho antes de processar e falha as primeiras
 * {@code falhas} vezes.
 */
final class ProcessadorFalso implements ProcessadorDeOutbox<ProcessadorFalso.CargaFalsa> {

  static final String TIPO = "falso";

  record CargaFalsa(String para, String texto) {}

  record Chamada(CargaFalsa carga, UUID idDaMensagem, @Nullable String correlationId) {}

  private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
  private final AtomicInteger falhasRestantes;
  private final String mensagemDoErro;
  private final Runnable antes;

  ProcessadorFalso() {
    this(0);
  }

  ProcessadorFalso(int falhas) {
    this(falhas, "provedor fora do ar", () -> {});
  }

  private ProcessadorFalso(int falhas, String mensagemDoErro, Runnable antes) {
    this.falhasRestantes = new AtomicInteger(falhas);
    this.mensagemDoErro = mensagemDoErro;
    this.antes = antes;
  }

  /** Falha sempre, com esta mensagem na exceção. */
  static ProcessadorFalso falhandoCom(String mensagemDoErro) {
    return new ProcessadorFalso(Integer.MAX_VALUE, mensagemDoErro, () -> {});
  }

  /** Envia sempre, rodando {@code antes} no começo de cada chamada. */
  static ProcessadorFalso comGancho(Runnable antes) {
    return new ProcessadorFalso(0, "", antes);
  }

  @Override
  public String tipo() {
    return TIPO;
  }

  @Override
  public Class<CargaFalsa> tipoDaCarga() {
    return CargaFalsa.class;
  }

  @Override
  public void processar(CargaFalsa carga, UUID idDaMensagem) {
    antes.run();
    chamadas.add(new Chamada(carga, idDaMensagem, MDC.get(Correlacao.CHAVE_MDC)));
    if (falhasRestantes.getAndDecrement() > 0) {
      throw new IllegalStateException(mensagemDoErro);
    }
  }

  List<Chamada> chamadas() {
    return List.copyOf(chamadas);
  }
}
