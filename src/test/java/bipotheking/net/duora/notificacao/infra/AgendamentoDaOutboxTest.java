package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/** A condição do agendamento e o intervalo que ele lê da config principal, sem banco. */
class AgendamentoDaOutboxTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner()
          .withUserConfiguration(AgendamentoDaOutbox.class)
          .withBean(DespachanteDaOutbox.class, () -> mock(DespachanteDaOutbox.class));

  @Test
  void ligadoComAPropriedadeTrue() {
    contexto
        .withPropertyValues("duora.agendamentos.habilitados=true")
        .run(c -> assertThat(c).hasSingleBean(AgendamentoDaOutbox.class));
  }

  @Test
  void ligadoSemAPropriedade() {
    contexto.run(c -> assertThat(c).hasSingleBean(AgendamentoDaOutbox.class));
  }

  @Test
  void desligadoComAPropriedadeFalse() {
    contexto
        .withPropertyValues("duora.agendamentos.habilitados=false")
        .run(c -> assertThat(c).doesNotHaveBean(AgendamentoDaOutbox.class));
  }

  @Test
  void intervaloDaConfigPrincipalChegaAoAgendamento() {
    contexto
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.config.location=classpath:/application.properties")
        .withUserConfiguration(ComAgendamento.class)
        .run(c -> {
          assertThat(c).hasNotFailed().hasSingleBean(AgendamentoDaOutbox.class);
          assertThat(c.getBean(ScheduledTaskHolder.class).getScheduledTasks())
              .singleElement()
              .satisfies(tarefa ->
                  assertThat(((FixedDelayTask) tarefa.getTask()).getIntervalDuration())
                      .isEqualTo(Duration.ofSeconds(2)));
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  static class ComAgendamento {}
}
