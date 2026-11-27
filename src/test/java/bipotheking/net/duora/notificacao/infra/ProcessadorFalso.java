package bipotheking.net.duora.notificacao.infra;

import bipotheking.net.duora.notificacao.ProcessadorDeOutbox;
import bipotheking.net.duora.nucleo.Correlacao;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/** Processador de teste: anota cada chamada e falha as primeiras {@code falhas} vezes. */
final class ProcessadorFalso implements ProcessadorDeOutbox<ProcessadorFalso.CargaFalsa> {

  static final String TIPO = "falso";

  record CargaFalsa(String para, String texto) {}

  record Chamada(CargaFalsa carga, UUID idDaMensagem, @Nullable String correlationId) {}

  private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
  private final AtomicInteger falhasRestantes;

  ProcessadorFalso() {
    this(0);
  }

  ProcessadorFalso(int falhas) {
    this.falhasRestantes = new AtomicInteger(falhas);
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
    chamadas.add(new Chamada(carga, idDaMensagem, MDC.get(Correlacao.CHAVE_MDC)));
    if (falhasRestantes.getAndDecrement() > 0) {
      throw new IllegalStateException("provedor fora do ar");
    }
  }

  List<Chamada> chamadas() {
    return List.copyOf(chamadas);
  }
}
