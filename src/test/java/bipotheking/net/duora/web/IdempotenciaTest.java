package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.Resumos;
import bipotheking.net.duora.web.Idempotencia.Reserva;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
  private static final String OUTRA_IMPRESSAO = Resumos.sha256Hex("bia@exemplo.invalid");
  private static final Resposta RESPOSTA = new Resposta("Confira seu e-mail", 1);
  private static final Duration DEPOIS_DA_RESERVA_MAXIMA = Duration.ofSeconds(31);

  @Autowired private Idempotencia idempotencia;
  @Autowired private TransactionTemplate transacao;

  record Resposta(String mensagem, int tentativas) {}

  private Reserva<Resposta> reservar() {
    return idempotencia.reservar(ESCOPO, CHAVE, IMPRESSAO, Resposta.class);
  }

  /** Reserva e devolve o dono, falhando o teste se a chave não vier livre. */
  private UUID reservarComoDono() {
    Reserva<Resposta> reserva = reservar();
    assertThat(reserva).isInstanceOf(Reserva.Tua.class);
    return ((Reserva.Tua<Resposta>) reserva).dono();
  }

  private void concluirEmTransacao(UUID dono) {
    transacao.executeWithoutResult(status -> idempotencia.concluir(ESCOPO, CHAVE, dono, RESPOSTA));
  }

  private long reservasAbertasDe(UUID dono) {
    return jdbc.sql(
            "select count(*) from chave_de_idempotencia where dono = :dono and resposta is null")
        .param("dono", dono)
        .query(Long.class)
        .single();
  }

  private static void recusadaCom(Throwable erro, CodigoWeb codigo) {
    assertThat(erro).isInstanceOf(ErroDeNegocio.class);
    assertThat(((ErroDeNegocio) erro).codigo()).isEqualTo(codigo);
  }

  @Test
  void primeiraReservaETua() {
    assertThat(reservar()).isInstanceOf(Reserva.Tua.class);
  }

  @Test
  void concluidaDevolveRespostaSemEfeito() {
    concluirEmTransacao(reservarComoDono());

    assertThat(reservar()).isEqualTo(new Reserva.JaRespondida<>(RESPOSTA));
  }

  @Test
  void mesmaChaveImpressaoDiferenteRecusada() {
    reservar();

    assertThatThrownBy(() -> idempotencia.reservar(ESCOPO, CHAVE, OUTRA_IMPRESSAO, Resposta.class))
        .satisfies(erro -> recusadaCom(erro, CodigoWeb.CHAVE_DE_IDEMPOTENCIA_REUSADA));
  }

  @Test
  void concluidaComOutraImpressaoRecusada() {
    concluirEmTransacao(reservarComoDono());

    assertThatThrownBy(() -> idempotencia.reservar(ESCOPO, CHAVE, OUTRA_IMPRESSAO, Resposta.class))
        .satisfies(erro -> recusadaCom(erro, CodigoWeb.CHAVE_DE_IDEMPOTENCIA_REUSADA));
  }

  @Test
  void escoposDiferentesNaoColidem() {
    reservar();

    assertThat(idempotencia.reservar("outro-escopo", CHAVE, IMPRESSAO, Resposta.class))
        .isInstanceOf(Reserva.Tua.class);
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
    List<Future<Reserva<Resposta>>> tentativas = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        tentativas.add(
            executor.submit(() -> {
              largada.await();
              return reservar();
            }));
      }
      largada.countDown();

      List<Reserva<Resposta>> vencedoras = new ArrayList<>();
      List<Throwable> recusas = new ArrayList<>();
      for (Future<Reserva<Resposta>> tentativa : tentativas) {
        try {
          vencedoras.add(tentativa.get(10, TimeUnit.SECONDS));
        } catch (ExecutionException e) {
          recusas.add(e.getCause());
        }
      }

      assertThat(vencedoras).singleElement().isInstanceOf(Reserva.Tua.class);
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
    UUID primeiroDono = reservarComoDono();
    relogio.avancar(DEPOIS_DA_RESERVA_MAXIMA);

    assertThat(reservarComoDono()).isNotEqualTo(primeiroDono);
  }

  @Test
  void liberarPermiteNovaReserva() {
    idempotencia.liberar(ESCOPO, CHAVE, reservarComoDono());

    assertThat(reservar()).isInstanceOf(Reserva.Tua.class);
  }

  @Test
  void liberarNaoApagaConcluida() {
    UUID dono = reservarComoDono();
    concluirEmTransacao(dono);
    idempotencia.liberar(ESCOPO, CHAVE, dono);

    assertThat(reservar()).isEqualTo(new Reserva.JaRespondida<>(RESPOSTA));
  }

  @Test
  void liberarDeDonoVencidoNaoApagaReservaRetomada() {
    UUID donoVencido = reservarComoDono();
    relogio.avancar(DEPOIS_DA_RESERVA_MAXIMA);
    UUID donoAtual = reservarComoDono();

    idempotencia.liberar(ESCOPO, CHAVE, donoVencido);
    concluirEmTransacao(donoAtual);

    assertThat(reservar()).isEqualTo(new Reserva.JaRespondida<>(RESPOSTA));
  }

  @Test
  void concluirDeDonoVencidoFalha() {
    UUID donoVencido = reservarComoDono();
    relogio.avancar(DEPOIS_DA_RESERVA_MAXIMA);
    reservarComoDono();

    assertThatThrownBy(() -> concluirEmTransacao(donoVencido))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void concluirSemReservaAbertaFalha() {
    assertThatThrownBy(() -> concluirEmTransacao(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void concluirSemTransacaoFalha() {
    UUID dono = reservarComoDono();

    assertThatThrownBy(() -> idempotencia.concluir(ESCOPO, CHAVE, dono, RESPOSTA))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void rollbackDoEfeitoNaoGravaResposta() {
    UUID dono = reservarComoDono();

    assertThatThrownBy(() ->
            transacao.executeWithoutResult(status -> {
              idempotencia.concluir(ESCOPO, CHAVE, dono, RESPOSTA);
              throw new IllegalStateException("efeito falhou");
            }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("efeito falhou");

    assertThat(reservasAbertasDe(dono)).isOne();
  }
}
