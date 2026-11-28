package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bipotheking.net.duora.conta.dominio.CredencialDoNavegador;
import bipotheking.net.duora.conta.dominio.ProvaDeVida;
import bipotheking.net.duora.conta.dominio.RepositorioDeAceites;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.web.Idempotencia;
import bipotheking.net.duora.web.LimiteDeTaxa;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A conta da sessão que sumiu entre a checagem da sessão e o caso de uso (excluída numa requisição
 * paralela) recebe a mesma recusa da sessão, que o front trata recarregando.
 */
class VerificacaoDeIdadeTest {

  private final RepositorioDeContas contas = mock(RepositorioDeContas.class);
  private final VerificacaoDeIdade verificacao =
      new VerificacaoDeIdade(
          mock(LimiteDeTaxa.class),
          contas,
          mock(RepositorioDeVerificacoes.class),
          mock(ProvaDeVida.class),
          mock(CredencialDoNavegador.class),
          new LimitesDaVerificacao(3, 2),
          mock(EfeitosDaVerificacao.class),
          mock(Idempotencia.class),
          mock(RepositorioDeAceites.class),
          mock(DocumentosLegais.class),
          mock(TransactionTemplate.class),
          Clock.systemUTC());

  @Test
  void contaQueSumiuRecebeContaIndisponivel() {
    ContaId id = new ContaId(UUID.randomUUID());
    when(contas.porId(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> verificacao.situacao(id))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoSessao.CONTA_INDISPONIVEL));
  }
}
