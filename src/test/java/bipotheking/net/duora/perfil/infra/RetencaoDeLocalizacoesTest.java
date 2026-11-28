package bipotheking.net.duora.perfil.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.FaixaEtaria;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.Raio;
import bipotheking.net.duora.perfil.ServicoDePerfil;
import bipotheking.net.duora.perfil.dominio.AjustesDeBusca;
import bipotheking.net.duora.perfil.dominio.AjustesVersionados;
import bipotheking.net.duora.perfil.dominio.Apelido;
import bipotheking.net.duora.perfil.dominio.Bio;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Intencoes;
import bipotheking.net.duora.perfil.dominio.RepositorioDeAjustes;
import bipotheking.net.duora.perfil.dominio.RepositorioDeLocalizacao;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** A célula some 90 dias depois da última atualização. */
class RetencaoDeLocalizacoesTest extends BancoDeTeste {

  private static final CelulaGeohash SAO_PAULO = new CelulaGeohash("6gyf4");

  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private RepositorioDeLocalizacao localizacoes;
  @Autowired private ContasDeTeste contas;
  @Autowired private ServicoDePerfil perfis;
  @Autowired private RepositorioDeAjustes ajustes;
  @Autowired private TransactionTemplate transacao;

  private final ExecutorService pool = Executors.newFixedThreadPool(2);

  @AfterEach
  void encerrarPool() {
    pool.shutdownNow();
  }

  private PoliticaDeRetencao retencao() {
    return politicas.stream()
        .filter(p -> p.nome().equals("localizacoes-paradas"))
        .findFirst()
        .orElseThrow();
  }

  private ContaId gravadaHa(Duration idade) {
    ContaId conta = contas.criarAtiva().id();
    localizacoes.consentirEGravar(conta, SAO_PAULO, INICIO.minus(idade));
    return conta;
  }

  private Instant limite() {
    return INICIO.minus(retencao().prazo());
  }

  @Test
  void vencidaSome() {
    ContaId vencida = gravadaHa(Duration.ofDays(91));
    ContaId recente = gravadaHa(Duration.ofDays(89));

    assertThat(retencao().expirar(limite())).isOne();

    assertThat(localizacoes.celula(vencida)).isEmpty();
    assertThat(localizacoes.celula(recente)).contains(SAO_PAULO);
  }

  @Test
  void localizacaoVencidaVoltaORaio() {
    ContaId vencida = comRaioDe20KmGravadoHa(Duration.ofDays(91));
    ContaId recente = comRaioDe20KmGravadoHa(Duration.ofDays(89));

    assertThat(retencao().expirar(limite())).isOne();

    assertThat(ajustes.buscar(vencida))
        .contains(
            new AjustesVersionados(
                new AjustesDeBusca(FaixaEtaria.TODAS, Raio.SEM_LIMITE, false), 2L));
    assertThat(ajustes.buscar(recente))
        .contains(
            new AjustesVersionados(
                new AjustesDeBusca(FaixaEtaria.TODAS, Raio.ATE_20_KM, true), 1L));
  }

  @Test
  void raioGravadoEnquantoORetencaoEsperaTambemVolta() throws Exception {
    ContaId conta = comPerfil(gravadaHa(Duration.ofDays(91)));
    CountDownLatch criou = new CountDownLatch(1);
    CountDownLatch liberar = new CountDownLatch(1);

    Future<Boolean> gravacao =
        pool.submit(() ->
            transacao.execute(_ -> {
              boolean criado =
                  ajustes.criar(
                      conta, new AjustesDeBusca(FaixaEtaria.TODAS, Raio.ATE_20_KM, true), INICIO);
              criou.countDown();
              aguardar(liberar);
              return criado;
            }));
    aguardar(criou);
    Future<Integer> lote = pool.submit(() -> retencao().expirar(limite()));
    esperarDeleteBloqueado();
    liberar.countDown();

    assertThat(gravacao.get(10, TimeUnit.SECONDS)).isTrue();
    assertThat(lote.get(10, TimeUnit.SECONDS)).isOne();
    assertThat(localizacoes.celula(conta)).isEmpty();
    assertThat(ajustes.buscar(conta))
        .contains(
            new AjustesVersionados(
                new AjustesDeBusca(FaixaEtaria.TODAS, Raio.SEM_LIMITE, false), 2L));
  }

  /** O DELETE da retenção está na fila do lock que a gravação do raio segura. */
  private void esperarDeleteBloqueado() throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      long esperando =
          jdbc.sql(
                  """
                  select count(*) from pg_stat_activity
                  where wait_event_type = 'Lock' and query ilike '%delete from localizacao%'
                  """)
              .query(Long.class)
              .single();
      if (esperando > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new IllegalStateException("A retenção não chegou a esperar o lock");
  }

  private static void aguardar(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Latch não liberado a tempo");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private ContaId comRaioDe20KmGravadoHa(Duration idade) {
    ContaId conta = comPerfil(gravadaHa(idade));
    assertThat(
            ajustes.criar(
                conta, new AjustesDeBusca(FaixaEtaria.TODAS, Raio.ATE_20_KM, true), INICIO))
        .isTrue();
    return conta;
  }

  private ContaId comPerfil(ContaId conta) {
    perfis.salvar(
        conta,
        null,
        new EdicaoDoPerfil(
            Apelido.de("Bia"),
            Optional.empty(),
            new Intencoes(Set.of(Intencao.AMIZADE)),
            Bio.de(null)));
    return conta;
  }

  @Test
  void tamanhoContaSoVencidas() {
    gravadaHa(Duration.ofDays(91));
    gravadaHa(Duration.ofDays(89));

    assertThat(retencao().tamanho()).isOne();
  }

  @Test
  void nomeEPrazo() {
    assertThat(retencao().nome()).isEqualTo("localizacoes-paradas");
    assertThat(retencao().prazo()).isEqualTo(Duration.parse("P90D"));
  }
}
