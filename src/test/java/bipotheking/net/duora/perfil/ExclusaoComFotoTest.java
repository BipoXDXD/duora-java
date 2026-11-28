package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.CodigoSessao;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.web.ArmazemDeImagens;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.Variante;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** A exclusão da conta leva as fotos: as linhas na transação, os arquivos depois do commit. */
class ExclusaoComFotoTest extends BancoDeTeste {

  private static final String CATEGORIA = "perfil";
  private static final Instant FIM_DOS_TEMPOS = Instant.parse("9999-01-01T00:00:00Z");
  private static final Duration PACIENCIA = Duration.ofSeconds(20);

  @Autowired private ExclusaoDeConta exclusao;
  @Autowired private FotosDoPerfil fotos;
  @Autowired private FotosDeTeste fotosDeTeste;
  @Autowired private ContasDeTeste contas;
  @Autowired private ArmazemDeImagens armazem;
  @Autowired private TransactionTemplate transacao;

  @Test
  void exclusaoApagaLinhasEArquivos() {
    ContaId conta = contas.criarAtiva().id();
    FotoId aprovada = fotosDeTeste.enviarAprovada(conta);
    FotoId pendente = fotosDeTeste.enviar(conta);

    exclusao.excluir(conta, true);

    assertThat(fotosDa(conta)).isZero();
    for (FotoId foto : List.of(aprovada, pendente)) {
      assertThat(armazem.ler(CATEGORIA, foto.valor(), Variante.NITIDA)).isEmpty();
      assertThat(armazem.ler(CATEGORIA, foto.valor(), Variante.BORRADA)).isEmpty();
    }
  }

  @Test
  void exclusaoDesfeitaMantemArquivos() {
    ContaId conta = contas.criarAtiva().id();
    FotoId aprovada = fotosDeTeste.enviarAprovada(conta);

    transacao.executeWithoutResult(status -> {
      exclusao.excluir(conta, true);
      status.setRollbackOnly();
    });

    assertThat(fotosDa(conta)).isOne();
    assertThat(armazem.ler(CATEGORIA, aprovada.valor(), Variante.NITIDA)).isPresent();
    assertThat(armazem.ler(CATEGORIA, aprovada.valor(), Variante.BORRADA)).isPresent();
  }

  /**
   * A exclusão fica aberta (já com a conta como lápide, ainda sem commit) enquanto o envio chega. O
   * envio espera a trava das fotos que a exclusão tomou e, depois do commit, encontra a conta
   * excluída: não sobra linha nem arquivo.
   */
  @Test
  void envioDuranteExclusaoNaoDeixaFoto() throws Exception {
    ContaId conta = contas.criarAtiva().id();
    Set<UUID> antes = guardadas();
    byte[] retrato = FotosDeTeste.fixture(FotosDeTeste.RETRATO);

    Future<FotoEnviada> envio;
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      envio =
          transacao.execute(_ -> {
            exclusao.excluir(conta, true);
            Future<FotoEnviada> emCurso = executor.submit(() -> fotos.enviar(conta, retrato, null));
            esperarTravaOuFim(emCurso);
            return emCurso;
          });
      assertThatThrownBy(() -> Objects.requireNonNull(envio).get())
          .cause()
          .isInstanceOfSatisfying(
              ErroDeNegocio.class,
              erro -> assertThat(erro.codigo()).isEqualTo(CodigoSessao.CONTA_INDISPONIVEL));
    }

    assertThat(fotosDa(conta)).isZero();
    assertThat(guardadas()).isEqualTo(antes);
  }

  /**
   * Espera o envio ficar preso numa trava ou terminar, o que vier antes. Qualquer trava: sem a
   * consultiva, o envio esperaria a linha da conta que a exclusão alterou e gravaria a foto depois.
   */
  private void esperarTravaOuFim(Future<?> envio) {
    Instant prazo = Instant.now().plus(PACIENCIA);
    while (!envio.isDone() && travasEmEspera() == 0) {
      if (Instant.now().isAfter(prazo)) {
        throw new AssertionError("O envio nem terminou nem esperou a trava");
      }
      LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
    }
  }

  private long travasEmEspera() {
    return jdbc.sql("select count(*) from pg_locks where not granted").query(Long.class).single();
  }

  private long fotosDa(ContaId conta) {
    return jdbc.sql("select count(*) from foto where conta_id = :conta")
        .param("conta", conta.valor())
        .query(Long.class)
        .single();
  }

  /** Todos os ids guardados na categoria, página por página. */
  private Set<UUID> guardadas() {
    Set<UUID> ids = new HashSet<>();
    @Nullable UUID cursor = null;
    while (true) {
      List<UUID> pagina = armazem.guardadasAntesDe(CATEGORIA, FIM_DOS_TEMPOS, cursor, 500);
      if (pagina.isEmpty()) {
        return ids;
      }
      ids.addAll(pagina);
      cursor = pagina.getLast();
    }
  }
}
