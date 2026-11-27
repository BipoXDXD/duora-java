package bipotheking.net.duora.retencao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * O contexto de teste tem o núcleo de retenção sem o agendamento; a condição e o horário do
 * agendamento são conferidos à parte, sem banco, como no {@code AgendamentoDaOutboxTest}.
 */
class AgendamentoDaRetencaoTest extends BancoDeTeste {

  @Autowired private ApplicationContext contexto;

  private final ApplicationContextRunner semBanco =
      new ApplicationContextRunner()
          .withUserConfiguration(AgendamentoDaRetencao.class)
          .withBean(ExpiracaoDeDados.class, () -> mock(ExpiracaoDeDados.class));

  @Test
  void agendamentoDesligadoNoTeste() {
    assertThat(contexto.getBeanNamesForType(AgendamentoDaRetencao.class)).isEmpty();
    assertThat(contexto.getBeanNamesForType(ExpiracaoDeDados.class)).hasSize(1);
  }

  @Test
  void ligadoSemAPropriedade() {
    semBanco.run(c -> assertThat(c).hasSingleBean(AgendamentoDaRetencao.class));
  }

  @Test
  void desligadoComAPropriedadeFalse() {
    semBanco
        .withPropertyValues("duora.agendamentos.habilitados=false")
        .run(c -> assertThat(c).doesNotHaveBean(AgendamentoDaRetencao.class));
  }

  /** 03:30 em São Paulo (UTC−3, sem horário de verão) é 06:30 UTC. */
  @Test
  void rodaAs3h30DeSaoPauloPelaConfigPrincipal() {
    Clock meiaNoiteUtc = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);
    semBanco
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.config.location=classpath:/application.properties")
        .withUserConfiguration(ComAgendamento.class)
        .run(c -> {
          assertThat(c).hasNotFailed().hasSingleBean(AgendamentoDaRetencao.class);
          assertThat(c.getBean(ScheduledTaskHolder.class).getScheduledTasks())
              .singleElement()
              .satisfies(tarefa -> {
                CronTask cron = (CronTask) tarefa.getTask();
                assertThat(cron.getExpression()).isEqualTo("0 30 3 * * *");
                assertThat(cron.getTrigger().nextExecution(new SimpleTriggerContext(meiaNoiteUtc)))
                    .isEqualTo(Instant.parse("2026-11-22T06:30:00Z"));
              });
        });
  }

  /**
   * Sem {@code @Configuration} de propósito: uma classe aninhada com ela trocaria a configuração do
   * {@code @SpringBootTest} herdado. O {@code @Import} de {@code @EnableScheduling} basta.
   */
  @EnableScheduling
  static class ComAgendamento {}
}
