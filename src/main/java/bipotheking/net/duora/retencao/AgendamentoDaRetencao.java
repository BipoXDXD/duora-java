package bipotheking.net.duora.retencao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda a {@link ExpiracaoDeDados} uma vez por dia, em {@code duora.retencao.cron} no fuso de São
 * Paulo. Desligado nos testes, que chamam {@link ExpiracaoDeDados#executar()} direto.
 */
@Component
@ConditionalOnProperty(
    name = "duora.agendamentos.habilitados",
    havingValue = "true",
    matchIfMissing = true)
class AgendamentoDaRetencao {

  private final ExpiracaoDeDados expiracao;

  AgendamentoDaRetencao(ExpiracaoDeDados expiracao) {
    this.expiracao = expiracao;
  }

  @Scheduled(cron = "${duora.retencao.cron}", zone = "America/Sao_Paulo")
  void expirar() {
    expiracao.executar();
  }
}
