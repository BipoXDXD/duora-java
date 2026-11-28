package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.Resumos;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class IdempotenciaExecutarTest extends BancoDeTeste {

  private static final String ESCOPO = "convite";
  private static final String CHAVE = "chave-de-teste-0002";
  private static final String IMPRESSAO = Resumos.sha256Hex("cenario=terraco");
  private static final String OUTRA_IMPRESSAO = Resumos.sha256Hex("cenario=praia");
  private static final Resposta RESPOSTA = new Resposta("enc-1");

  @Autowired private Idempotencia idempotencia;
  @Autowired private TransactionTemplate transacao;

  private final AtomicInteger execucoes = new AtomicInteger();

  record Resposta(String encontroId) {}

  private final Supplier<Resposta> efeito =
      () -> {
        execucoes.incrementAndGet();
        return RESPOSTA;
      };

  private final Supplier<Resposta> efeitoQueFalha =
      () -> {
        execucoes.incrementAndGet();
        throw new IllegalStateException("efeito falhou");
      };

  private Resposta executar(Supplier<Resposta> qual) {
    return idempotencia.executar(ESCOPO, CHAVE, IMPRESSAO, Resposta.class, qual);
  }

  private void falharUmaVez() {
    assertThatThrownBy(() -> executar(efeitoQueFalha))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("efeito falhou");
  }

  private long respostasGravadas() {
    return jdbc.sql("select count(*) from chave_de_idempotencia where resposta is not null")
        .query(Long.class)
        .single();
  }

  private static boolean recusadaCom(Throwable erro, CodigoWeb codigo) {
    return erro instanceof ErroDeNegocio negocio && negocio.codigo() == codigo;
  }

  @Test
  void semChaveRodaOEfeitoUmaVez() {
    AtomicBoolean emTransacao = new AtomicBoolean();

    Resposta resposta =
        idempotencia.executar(
            ESCOPO,
            null,
            IMPRESSAO,
            Resposta.class,
            () -> {
              emTransacao.set(TransactionSynchronizationManager.isActualTransactionActive());
              return efeito.get();
            });

    assertThat(resposta).isEqualTo(RESPOSTA);
    assertThat(execucoes).hasValue(1);
    assertThat(emTransacao).isTrue();
  }

  @Test
  void mesmaChaveEMesmaImpressaoDevolveARespostaSemRodarDeNovo() {
    executar(efeito);

    assertThat(executar(efeito)).isEqualTo(RESPOSTA);
    assertThat(execucoes).hasValue(1);
  }

  @Test
  void impressaoDiferenteRecebeChaveReusada() {
    executar(efeito);

    assertThatThrownBy(
            () -> idempotencia.executar(ESCOPO, CHAVE, OUTRA_IMPRESSAO, Resposta.class, efeito))
        .matches(erro -> recusadaCom(erro, CodigoWeb.CHAVE_DE_IDEMPOTENCIA_REUSADA));
    assertThat(execucoes).hasValue(1);
  }

  @Test
  void efeitoQueFalhaLiberaAChave() {
    falharUmaVez();

    assertThat(idempotencia.reservar(ESCOPO, CHAVE, IMPRESSAO, Resposta.class))
        .isInstanceOf(Idempotencia.Reserva.Tua.class);
  }

  @Test
  void efeitoQueFalhaNaoDeixaResposta() {
    falharUmaVez();

    assertThat(respostasGravadas()).isZero();
  }

  @Test
  void duasSimultaneasRodamUmEfeito() throws Exception {
    CountDownLatch largada = new CountDownLatch(1);
    List<Future<Resposta>> tentativas = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        tentativas.add(
            executor.submit(() -> {
              largada.await();
              return executar(efeito);
            }));
      }
      largada.countDown();

      List<Resposta> respostas = new ArrayList<>();
      List<Throwable> recusas = new ArrayList<>();
      for (Future<Resposta> tentativa : tentativas) {
        try {
          respostas.add(tentativa.get(10, TimeUnit.SECONDS));
        } catch (ExecutionException e) {
          recusas.add(e.getCause());
        }
      }

      assertThat(execucoes).hasValue(1);
      assertThat(respostas).isNotEmpty().containsOnly(RESPOSTA);
      assertThat(recusas).allMatch(erro -> recusadaCom(erro, CodigoWeb.REQUISICAO_EM_ANDAMENTO));
    }
  }

  @Test
  void dentroDeTransacaoLanca() {
    assertThatThrownBy(() -> transacao.execute(status -> executar(efeito)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(execucoes).hasValue(0);
  }
}
