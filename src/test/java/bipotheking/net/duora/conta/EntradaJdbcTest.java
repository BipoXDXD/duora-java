package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeBloqueios;
import bipotheking.net.duora.conta.dominio.RepositorioDeLinks;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.nucleo.Resumos;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Os repositórios de links e de bloqueios contra o Postgres. */
class EntradaJdbcTest extends BancoDeTeste {

  private static final Email EMAIL = new Email("ana@teste.duora.invalid");
  private static final byte[] TOKEN = Resumos.sha256("token");
  private static final Instant EXPIRA = INICIO.plus(Duration.ofMinutes(15));

  @Autowired private RepositorioDeLinks links;
  @Autowired private RepositorioDeBloqueios bloqueios;
  @Autowired private GeradorDeIds geradorDeIds;

  @Test
  void linkConsumidoUmaVezSo() {
    links.gravar(geradorDeIds.novo(), EMAIL, TOKEN, INICIO, EXPIRA);

    assertThat(links.consumir(TOKEN, INICIO)).contains(EMAIL);
    assertThat(links.consumir(TOKEN, INICIO)).isEmpty();
  }

  @Test
  void linkVencidoNaoEhConsumido() {
    links.gravar(geradorDeIds.novo(), EMAIL, TOKEN, INICIO, EXPIRA);

    assertThat(links.consumir(TOKEN, EXPIRA)).isEmpty();
  }

  @Test
  void linkInvalidadoNaoEhConsumido() {
    links.gravar(geradorDeIds.novo(), EMAIL, TOKEN, INICIO, EXPIRA);

    links.invalidarAbertos(EMAIL, INICIO);

    assertThat(links.consumir(TOKEN, INICIO)).isEmpty();
  }

  @Test
  void apagarDoEmailDevolveQuantosApagou() {
    links.gravar(geradorDeIds.novo(), EMAIL, TOKEN, INICIO, EXPIRA);
    links.gravar(geradorDeIds.novo(), EMAIL, Resumos.sha256("outro"), INICIO, EXPIRA);

    assertThat(links.apagarDoEmail(EMAIL)).isEqualTo(2);
    assertThat(links.consumir(TOKEN, INICIO)).isEmpty();
  }

  @Test
  void bloquearDuasVezesNaoFalha() {
    byte[] resumo = Resumos.sha256("bloqueado");

    bloqueios.bloquear(resumo, "MENOR", INICIO);
    bloqueios.bloquear(resumo, "MENOR", INICIO);

    assertThat(bloqueios.bloqueado(resumo)).isTrue();
    assertThat(bloqueios.bloqueado(Resumos.sha256("livre"))).isFalse();
  }
}
