package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.Resumos;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class IdempotenciaTest extends BancoDeTeste {

  private static final String ESCOPO = "entrar";
  private static final String CHAVE = "chave-de-teste-0001";
  private static final String IMPRESSAO = Resumos.sha256Hex("ana@exemplo.invalid");
  private static final Resposta RESPOSTA = new Resposta("Confira seu e-mail", 1);

  @Autowired private Idempotencia idempotencia;
  @Autowired private TransactionTemplate transacao;

  record Resposta(String mensagem, int tentativas) {}

  private Optional<Resposta> reservar() {
    return idempotencia.reservar(ESCOPO, CHAVE, IMPRESSAO, Resposta.class);
  }

  private void concluirEmTransacao() {
    transacao.executeWithoutResult(status -> idempotencia.concluir(ESCOPO, CHAVE, RESPOSTA));
  }

  private static void recusadaCom(Throwable erro, CodigoWeb codigo) {
    assertThat(erro).isInstanceOf(ErroDeNegocio.class);
    assertThat(((ErroDeNegocio) erro).codigo()).isEqualTo(codigo);
  }

  @Test
  void primeiraReservaEVazia() {
    assertThat(reservar()).isEmpty();
  }

  @Test
  void concluidaDevolveRespostaSemEfeito() {
    reservar();
    concluirEmTransacao();

    assertThat(reservar()).contains(RESPOSTA);
  }

  @Test
  void mesmaChaveImpressaoDiferenteRecusada() {
    reservar();

    assertThatThrownBy(() ->
            idempotencia.reservar(
                ESCOPO, CHAVE, Resumos.sha256Hex("bia@exemplo.invalid"), Resposta.class))
        .satisfies(erro -> recusadaCom(erro, CodigoWeb.CHAVE_DE_IDEMPOTENCIA_REUSADA));
  }

  @Test
  void escoposDiferentesNaoColidem() {
    reservar();

    assertThat(idempotencia.reservar("outro-escopo", CHAVE, IMPRESSAO, Resposta.class)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "x y z 123"})
  void chaveMalFormadaRecusada(String chave) {
    assertThatThrownBy(() -> idempotencia.reservar(ESCOPO, chave, IMPRESSAO, Resposta.class))
        .satisfies(erro -> recusadaCom(erro, CodigoWeb.CHAVE_DE_IDEMPOTENCIA_INVALIDA));
  }

  @Test
  void reservasSimultaneasSoUmaGanha() throws Exception {
    CountDownLatch largada = new CountDownLatch(1);
    List<Future<Optional<Resposta>>> tentativas = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        tentativas.add(
            executor.submit(() -> {
              largada.await();
              return reservar();
            }));
      }
      largada.countDown();

      List<Optional<Resposta>> vencedoras = new ArrayList<>();
      List<Throwable> recusas = new ArrayList<>();
      for (Future<Optional<Resposta>> tentativa : tentativas) {
        try {
          vencedoras.add(tentativa.get(10, TimeUnit.SECONDS));
        } catch (ExecutionException e) {
          recusas.add(e.getCause());
        }
      }

      assertThat(vencedoras).containsExactly(Optional.empty());
      assertThat(recusas)
          .singleElement()
          .satisfies(erro -> {
            recusadaCom(erro, CodigoWeb.REQUISICAO_EM_ANDAMENTO);
            assertThat(((ErroDeNegocio) erro).tentarDeNovoEm()).contains(Duration.ofSeconds(1));
          });
    }
  }

  @Test
  void reservaNoLimiteSegueEmAndamento() {
    reservar();
    relogio.avancar(Duration.ofSeconds(30));

    assertThatThrownBy(this::reservar)
        .satisfies(erro -> recusadaCom(erro, CodigoWeb.REQUISICAO_EM_ANDAMENTO));
  }

  @Test
  void reservaVencidaERetomada() {
    reservar();
    relogio.avancar(Duration.ofSeconds(31));

    assertThat(reservar()).isEmpty();
  }

  @Test
  void liberarPermiteNovaReserva() {
    reservar();
    idempotencia.liberar(ESCOPO, CHAVE);

    assertThat(reservar()).isEmpty();
  }

  @Test
  void liberarNaoApagaConcluida() {
    reservar();
    concluirEmTransacao();
    idempotencia.liberar(ESCOPO, CHAVE);

    assertThat(reservar()).contains(RESPOSTA);
  }

  @Test
  void concluirSemTransacaoFalha() {
    reservar();

    assertThatThrownBy(() -> idempotencia.concluir(ESCOPO, CHAVE, RESPOSTA))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void rollbackDoEfeitoNaoGravaResposta() {
    reservar();

    assertThatThrownBy(() ->
            transacao.executeWithoutResult(status -> {
              idempotencia.concluir(ESCOPO, CHAVE, RESPOSTA);
              throw new IllegalStateException("efeito falhou");
            }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(
            jdbc.sql("select count(*) from chave_de_idempotencia where resposta is not null")
                .query(Long.class)
                .single())
        .isZero();
  }
}
