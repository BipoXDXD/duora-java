package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeBloqueios;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeLinks;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.web.LimiteDeTaxa;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** O corte do tamanho do token, sem banco: o link falso conta as consultas. */
class ConfirmacaoDeEntradaTest {

  private static final String IP = "10.0.0.1";

  private final LinksQueContam links = new LinksQueContam();
  private final ConfirmacaoDeEntrada confirmacao =
      new ConfirmacaoDeEntrada(
          mock(LimiteDeTaxa.class),
          links,
          mock(RepositorioDeBloqueios.class),
          new ResumoDeEmail("chave-de-teste-com-pelo-menos-32-bytes"),
          mock(RepositorioDeContas.class),
          mock(Sessoes.class),
          new TransactionTemplate(new TransacaoDeMentira()),
          Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void tokenDe64CaracteresConsultaOBanco() {
    assertThatThrownBy(() -> confirmacao.confirmar("a".repeat(64), IP, Optional.empty()))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoEntrada.LINK_INVALIDO));

    assertThat(links.consultas).isOne();
  }

  @Test
  void tokenDe65CaracteresNemConsultaOBanco() {
    assertThatThrownBy(() -> confirmacao.confirmar("a".repeat(65), IP, Optional.empty()))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoEntrada.LINK_INVALIDO));

    assertThat(links.consultas).isZero();
  }

  /** Nenhum link existe; só conta quantas vezes alguém tentou consumir um. */
  private static final class LinksQueContam implements RepositorioDeLinks {

    private int consultas;

    @Override
    public void travarEmail(String resumoDoEmail) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void invalidarAbertos(Email email, Instant agora) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void gravar(UUID id, Email email, byte[] tokenHash, Instant criadoEm, Instant expiraEm) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Email> consumir(byte[] tokenHash, Instant agora) {
      consultas++;
      return Optional.empty();
    }

    @Override
    public int apagarDoEmail(Email email) {
      throw new UnsupportedOperationException();
    }
  }

  /** Roda o callback do {@link TransactionTemplate} sem transação de verdade. */
  private static final class TransacaoDeMentira implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(@Nullable TransactionDefinition definicao) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {}

    @Override
    public void rollback(TransactionStatus status) {}
  }
}
