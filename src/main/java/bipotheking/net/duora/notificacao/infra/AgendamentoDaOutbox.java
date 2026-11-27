package bipotheking.net.duora.notificacao.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chama o despachante a cada {@code duora.outbox.intervalo}. Desligado nos testes de unidade, que
 * chamam {@link DespachanteDaOutbox#despacharAgora()} direto.
 */
@Component
@ConditionalOnProperty(
    name = "duora.agendamentos.habilitados",
    havingValue = "true",
    matchIfMissing = true)
class AgendamentoDaOutbox {

  private final DespachanteDaOutbox despachante;

  AgendamentoDaOutbox(DespachanteDaOutbox despachante) {
    this.despachante = despachante;
  }

  @Scheduled(fixedDelayString = "${duora.outbox.intervalo}")
  void despachar() {
    despachante.despacharAgora();
  }
}
