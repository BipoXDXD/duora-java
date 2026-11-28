package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;
import bipotheking.net.duora.perfil.dominio.FotoDePerfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import bipotheking.net.duora.web.ArmazemDeImagens;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.Variante;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/** A moderação das fotos: a fila das pendentes, a aprovação e a recusa. */
class FilaDeFotosTest extends BancoDeTeste {

  private static final String CATEGORIA = "perfil";

  @Autowired private FilaDeFotos fila;
  @Autowired private FotosDeTeste fotosDeTeste;
  @Autowired private ContasDeTeste contas;
  @Autowired private RepositorioDeFotos repositorio;
  @Autowired private ArmazemDeImagens armazem;

  @Test
  void pendentesEmOrdemDeEnvio() {
    ContaId primeira = contas.criarAtiva().id();
    ContaId segunda = contas.criarAtiva().id();
    ContaId terceira = contas.criarAtiva().id();
    FotoId daSegunda = fotosDeTeste.enviar(segunda);
    relogio.avancar(Duration.ofMinutes(1));
    FotoId daPrimeira = fotosDeTeste.enviar(primeira);
    relogio.avancar(Duration.ofMinutes(1));
    fotosDeTeste.enviar(terceira);

    assertThat(fila.pendentes(2))
        .containsExactly(
            new FotoPendente(daSegunda, segunda, INICIO),
            new FotoPendente(daPrimeira, primeira, INICIO.plus(Duration.ofMinutes(1))));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 51})
  void limiteForaDaFaixaRecusado(int limite) {
    assertThatThrownBy(() -> fila.pendentes(limite)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aprovarTrocaAAtual() {
    ContaId conta = contas.criarAtiva().id();
    FotoId anterior = fotosDeTeste.enviarAprovada(conta);
    FotoId nova = fotosDeTeste.enviar(conta);

    fila.aprovar(nova);

    assertThat(repositorio.daConta(conta))
        .extracting(FotoDePerfil::id, FotoDePerfil::estado)
        .containsExactly(tuple(nova, EstadoDaFoto.APROVADA));
    assertThat(armazem.ler(CATEGORIA, anterior.valor(), Variante.NITIDA)).isEmpty();
    assertThat(armazem.ler(CATEGORIA, anterior.valor(), Variante.BORRADA)).isEmpty();
    assertThat(armazem.ler(CATEGORIA, nova.valor(), Variante.NITIDA)).isPresent();
  }

  @Test
  void recusarApagaArquivosEMarca() {
    ContaId conta = contas.criarAtiva().id();
    FotoId foto = fotosDeTeste.enviar(conta);
    relogio.avancar(Duration.ofHours(2));

    fila.recusar(foto);

    FotoDePerfil recusada = repositorio.porId(foto).orElseThrow();
    assertThat(recusada.estado()).isEqualTo(EstadoDaFoto.RECUSADA);
    assertThat(recusada.decididaEm()).isEqualTo(INICIO.plus(Duration.ofHours(2)));
    assertThat(armazem.ler(CATEGORIA, foto.valor(), Variante.NITIDA)).isEmpty();
    assertThat(armazem.ler(CATEGORIA, foto.valor(), Variante.BORRADA)).isEmpty();
  }

  @Test
  void aprovarIdInexistente() {
    assertThatThrownBy(() -> fila.aprovar(new FotoId(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoFoto.FOTO_INEXISTENTE));
  }

  @Test
  void aprovarJaAprovada() {
    ContaId conta = contas.criarAtiva().id();
    FotoId aprovada = fotosDeTeste.enviarAprovada(conta);

    assertThatThrownBy(() -> fila.aprovar(aprovada))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoFoto.FOTO_INEXISTENTE));
    assertThat(repositorio.porId(aprovada).orElseThrow().estado()).isEqualTo(EstadoDaFoto.APROVADA);
  }

  @Test
  void aprovarERecusarSimultaneosTemUmVencedor() throws Exception {
    ContaId conta = contas.criarAtiva().id();
    FotoId foto = fotosDeTeste.enviar(conta);
    CountDownLatch largada = new CountDownLatch(1);
    List<Throwable> falhas = new ArrayList<>();
    int vencedores = 0;
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<Void>> decisoes =
          List.of(
              executor.submit(decisao(largada, () -> fila.aprovar(foto))),
              executor.submit(decisao(largada, () -> fila.recusar(foto))));
      largada.countDown();
      for (Future<Void> decisao : decisoes) {
        try {
          decisao.get();
          vencedores++;
        } catch (ExecutionException erro) {
          falhas.add(erro.getCause());
        }
      }
    }

    assertThat(vencedores).isOne();
    assertThat(falhas)
        .singleElement()
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoFoto.FOTO_INEXISTENTE));
    assertThat(repositorio.porId(foto).orElseThrow().estado()).isNotEqualTo(EstadoDaFoto.PENDENTE);
  }

  private static Callable<Void> decisao(CountDownLatch largada, Runnable acao) {
    return () -> {
      largada.await();
      acao.run();
      return null;
    };
  }
}
