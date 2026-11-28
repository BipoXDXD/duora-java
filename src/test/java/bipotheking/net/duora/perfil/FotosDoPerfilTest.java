package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.CodigoSessao;
import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.nucleo.TipoDeErro;
import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;
import bipotheking.net.duora.perfil.dominio.FotoDePerfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import bipotheking.net.duora.web.ArmazemDeImagens;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.CodigoWeb;
import bipotheking.net.duora.web.Idempotencia;
import bipotheking.net.duora.web.ImagemProcessada;
import bipotheking.net.duora.web.LimiteDeTaxa;
import bipotheking.net.duora.web.ProcessadorDeImagem;
import bipotheking.net.duora.web.Variante;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.transaction.support.TransactionTemplate;

/** O envio grava a foto pendente e seus dois arquivos juntos, ou nenhum dos dois. */
class FotosDoPerfilTest extends BancoDeTeste {

  private static final String CATEGORIA = "perfil";
  private static final Instant FIM_DOS_TEMPOS = Instant.parse("9999-01-01T00:00:00Z");

  @Autowired private FotosDoPerfil fotos;
  @Autowired private FotosDeTeste fotosDeTeste;
  @Autowired private ContasDeTeste contas;
  @Autowired private RepositorioDeFotos repositorio;
  @Autowired private ArmazemDeImagens armazem;
  @Autowired private ConsultaDeContas consultaDeContas;
  @Autowired private ProcessadorDeImagem processador;
  @Autowired private LimiteDeTaxa limiteDeTaxa;
  @Autowired private Idempotencia idempotencia;
  @Autowired private GeradorDeIds geradorDeIds;
  @Autowired private TransactionTemplate transacao;
  @Autowired private Clock relogioDoSistema;
  @Autowired private AcessoAFoto acesso;

  private final byte[] retrato = FotosDeTeste.fixture(FotosDeTeste.RETRATO);

  @Test
  void envioGravaPendente() {
    ContaId conta = contas.criarAtiva().id();

    FotoEnviada enviada = fotos.enviar(conta, retrato, null);

    FotoDePerfil foto = unicaDa(conta);
    assertThat(foto.estado()).isEqualTo(EstadoDaFoto.PENDENTE);
    assertThat(foto.largura()).isEqualTo(800);
    assertThat(foto.altura()).isEqualTo(1200);
    assertThat(enviada)
        .isEqualTo(new FotoEnviada("PENDENTE", UrlDaFoto.de(foto.id(), Variante.NITIDA)));
    assertThat(variante(foto.id(), Variante.NITIDA)).isPresent();
    assertThat(variante(foto.id(), Variante.BORRADA)).isPresent();
  }

  @Test
  void segundoEnvioTrocaAPendente() {
    ContaId conta = contas.criarAtiva().id();
    FotoId primeira = fotosDeTeste.enviar(conta);

    FotoId segunda = fotosDeTeste.enviar(conta);

    assertThat(repositorio.daConta(conta)).extracting(FotoDePerfil::id).containsExactly(segunda);
    assertThat(variante(primeira, Variante.NITIDA)).isEmpty();
    assertThat(variante(primeira, Variante.BORRADA)).isEmpty();
  }

  @Test
  void envioApagaMarcaDeRecusa() {
    ContaId conta = contas.criarAtiva().id();
    FotoId recusada = fotosDeTeste.enviar(conta);
    transacao.executeWithoutResult(
        _ -> repositorio.decidir(recusada, EstadoDaFoto.RECUSADA, relogio.instant()));

    fotosDeTeste.enviar(conta);

    assertThat(repositorio.daConta(conta))
        .extracting(FotoDePerfil::estado)
        .containsExactly(EstadoDaFoto.PENDENTE);
  }

  @Test
  void falhaNoCommitApagaArquivosNovos() {
    ContaId conta = contas.criarAtiva().id();
    FotoId antiga = fotosDeTeste.enviar(conta);
    Set<UUID> antes = guardadas();
    jdbc.sql(
            """
            create function falhar_no_commit() returns trigger language plpgsql as
            'begin raise exception ''falha simulada no commit''; end'
            """)
        .update();
    jdbc.sql(
            """
            create constraint trigger falha_no_commit after insert on foto
            deferrable initially deferred for each row execute function falhar_no_commit()
            """)
        .update();
    try {
      assertThatThrownBy(() -> fotos.enviar(conta, retrato, null))
          .isNotInstanceOf(ErroDeNegocio.class);
    } finally {
      jdbc.sql("drop trigger falha_no_commit on foto").update();
      jdbc.sql("drop function falhar_no_commit()").update();
    }

    assertThat(guardadas()).isEqualTo(antes);
    assertThat(repositorio.daConta(conta)).extracting(FotoDePerfil::id).containsExactly(antiga);
  }

  @Test
  void enviosSimultaneosDaMesmaContaDeixamUmaPendente() throws Exception {
    ContaId conta = contas.criarAtiva().id();
    Set<UUID> antes = guardadas();
    CountDownLatch largada = new CountDownLatch(1);
    Callable<FotoEnviada> envio =
        () -> {
          largada.await();
          return fotos.enviar(conta, retrato, null);
        };
    List<FotoEnviada> respostas;
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<FotoEnviada> primeiro = executor.submit(envio);
      Future<FotoEnviada> segundo = executor.submit(envio);
      largada.countDown();
      respostas = List.of(primeiro.get(), segundo.get());
    }

    FotoDePerfil pendente = unicaDa(conta);
    assertThat(respostas).extracting(FotoEnviada::estado).containsOnly("PENDENTE");
    Set<UUID> novas = new HashSet<>(guardadas());
    novas.removeAll(antes);
    assertThat(novas).containsExactly(pendente.id().valor());
  }

  @Test
  void doTitularSemFoto() {
    ContaId conta = contas.criarAtiva().id();

    assertThat(fotos.doTitular(conta)).isEqualTo(new FotoDoTitular(null, null, null));
  }

  @Test
  void doTitularComPendenteTrazAsDuasUrls() {
    ContaId conta = contas.criarAtiva().id();
    FotoId aprovada = fotosDeTeste.enviarAprovada(conta);
    FotoId pendente = fotosDeTeste.enviar(conta);

    assertThat(fotos.doTitular(conta))
        .isEqualTo(new FotoDoTitular(urls(aprovada), urls(pendente), null));
  }

  @Test
  void discoCheioVira503ELiberaAChave() {
    ContaId conta = contas.criarAtiva().id();
    FotosDoPerfil comDiscoCheio = fotosCom(new ArmazemSemEspaco(armazem));

    assertThatThrownBy(() -> comDiscoCheio.enviar(conta, retrato, "disco-cheio-0001"))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> {
              assertThat(erro.codigo()).isEqualTo(CodigoWeb.PROCESSAMENTO_INDISPONIVEL);
              assertThat(erro.codigo().tipo()).isEqualTo(TipoDeErro.INDISPONIVEL);
            });
    assertThat(repositorio.daConta(conta)).isEmpty();

    assertThat(fotos.enviar(conta, retrato, "disco-cheio-0001").estado()).isEqualTo("PENDENTE");
  }

  @Test
  void contaQueDeixouDeEstarAtivaNaoGuardaFoto() {
    ContaId conta = contas.criar(EstadoConta.SUSPENSA, LocalDate.of(1990, 5, 10)).id();
    Set<UUID> antes = guardadas();

    assertThatThrownBy(() -> fotos.enviar(conta, retrato, "conta-suspensa-01"))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoSessao.CONTA_INDISPONIVEL));

    assertThat(repositorio.daConta(conta)).isEmpty();
    assertThat(guardadas()).isEqualTo(antes);
    assertThatThrownBy(() -> fotos.enviar(conta, retrato, "conta-suspensa-01"))
        .as("a chave foi liberada: o segundo envio chega à conta, não à reserva")
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoSessao.CONTA_INDISPONIVEL));
  }

  private FotoDePerfil unicaDa(ContaId conta) {
    List<FotoDePerfil> daConta = repositorio.daConta(conta);
    assertThat(daConta).hasSize(1);
    return daConta.getFirst();
  }

  private Optional<Resource> variante(FotoId foto, Variante variante) {
    return armazem.ler(CATEGORIA, foto.valor(), variante);
  }

  private static UrlsDaFoto urls(FotoId foto) {
    return new UrlsDaFoto(
        "/api/fotos/" + foto.valor() + "/nitida", "/api/fotos/" + foto.valor() + "/borrada");
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

  private FotosDoPerfil fotosCom(ArmazemDeImagens outroArmazem) {
    return new FotosDoPerfil(
        repositorio,
        consultaDeContas,
        processador,
        outroArmazem,
        limiteDeTaxa,
        idempotencia,
        geradorDeIds,
        transacao,
        relogioDoSistema,
        acesso);
  }

  /** Armazém com o disco cheio: {@code guardar} falha como o {@code ArmazemEmDisco} falharia. */
  private record ArmazemSemEspaco(ArmazemDeImagens real) implements ArmazemDeImagens {

    @Override
    public void guardar(String categoria, UUID id, ImagemProcessada imagem) {
      throw new UncheckedIOException(new IOException("No space left on device"));
    }

    @Override
    public Optional<Resource> ler(String categoria, UUID id, Variante variante) {
      return real.ler(categoria, id, variante);
    }

    @Override
    public void apagar(String categoria, UUID id) {
      real.apagar(categoria, id);
    }

    @Override
    public List<UUID> guardadasAntesDe(
        String categoria, Instant limite, @Nullable UUID depoisDe, int maximo) {
      return real.guardadasAntesDe(categoria, limite, depoisDe, maximo);
    }
  }
}
