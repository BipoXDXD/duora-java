package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.notificacao.Outbox;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RetencaoDaOutboxTest extends BancoDeTeste {

  private static final Duration UM_SEGUNDO = Duration.ofSeconds(1);

  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private Outbox outbox;

  private PoliticaDeRetencao politica(String nome) {
    return politicas.stream().filter(p -> p.nome().equals(nome)).findFirst().orElseThrow();
  }

  private PoliticaDeRetencao enviadas() {
    return politica("outbox-enviada");
  }

  private PoliticaDeRetencao falhadas() {
    return politica("outbox-falhada");
  }

  private static Instant limite(PoliticaDeRetencao politica) {
    return INICIO.minus(politica.prazo());
  }

  private static OffsetDateTime utc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }

  private void inserirEnviadas(int quantas, Instant enviadaEm) {
    jdbc.sql(
            """
            insert into outbox (id, tipo, titular, criada_em, proxima_tentativa_em, enviada_em)
            select gen_random_uuid(), 'email', 'titular-a', :em, :em, :em
            from generate_series(1, :quantas)
            """)
        .param("em", utc(enviadaEm))
        .param("quantas", quantas)
        .update();
  }

  private void inserirEnviada(Instant enviadaEm) {
    inserirEnviadas(1, enviadaEm);
  }

  private void inserirFalhada(Instant falhouEm) {
    jdbc.sql(
            """
            insert into outbox
                (id, tipo, titular, criada_em, proxima_tentativa_em, falhou_em, ultimo_erro)
            values (gen_random_uuid(), 'email', 'titular-a', :em, :em, :em, 'http-500')
            """)
        .param("em", utc(falhouEm))
        .update();
  }

  private void inserirPendente(Instant criadaEm) {
    jdbc.sql(
            """
            insert into outbox (id, tipo, titular, carga, criada_em, proxima_tentativa_em)
            values (gen_random_uuid(), 'email', 'titular-a', '{"para":"ana@exemplo.invalid"}',
                    :em, :em)
            """)
        .param("em", utc(criadaEm))
        .update();
  }

  private long linhas() {
    return jdbc.sql("select count(*) from outbox").query(Long.class).single();
  }

  @Test
  void enviadaHaSeteDiasExatosFica() {
    inserirEnviada(INICIO.minus(Duration.ofDays(7)));

    assertThat(enviadas().expirar(limite(enviadas()))).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void enviadaHaSeteDiasEUmSegundoSome() {
    inserirEnviada(INICIO.minus(Duration.ofDays(7)).minus(UM_SEGUNDO));

    assertThat(enviadas().expirar(limite(enviadas()))).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void falhadaHaTrintaDiasExatosFica() {
    inserirFalhada(INICIO.minus(Duration.ofDays(30)));

    assertThat(falhadas().expirar(limite(falhadas()))).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void falhadaHaTrintaDiasEUmSegundoSome() {
    inserirFalhada(INICIO.minus(Duration.ofDays(30)).minus(UM_SEGUNDO));

    assertThat(falhadas().expirar(limite(falhadas()))).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void enviadaNaoSaiPelaPoliticaDasFalhadas() {
    inserirEnviada(INICIO.minus(Duration.ofDays(60)));

    assertThat(falhadas().expirar(limite(falhadas()))).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void pendenteNuncaSome() {
    inserirPendente(INICIO.minus(Duration.ofDays(365)));

    assertThat(enviadas().expirar(INICIO)).isZero();
    assertThat(falhadas().expirar(INICIO)).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void pendenteEsquecidaSaiPelaPoliticaDasFalhadas() {
    inserirPendente(INICIO.minus(Duration.ofDays(365)));
    outbox.esquecerTitular("titular-a");

    assertThat(enviadas().expirar(INICIO.plus(UM_SEGUNDO))).isZero();
    assertThat(falhadas().expirar(INICIO.plus(UM_SEGUNDO))).isOne();
  }

  @Test
  void apagaEmLotes() {
    inserirEnviadas(2500, INICIO.minus(Duration.ofDays(8)));

    assertThat(enviadas().expirar(limite(enviadas()))).isEqualTo(2500);
    assertThat(linhas()).isZero();
  }

  @Test
  void tamanhoContaSoAsVencidasAlemDoPrazo() {
    inserirEnviada(INICIO.minus(Duration.ofDays(8)));
    inserirEnviada(INICIO.minus(Duration.ofDays(1)));
    inserirFalhada(INICIO.minus(Duration.ofDays(31)));
    inserirFalhada(INICIO.minus(Duration.ofDays(1)));
    inserirPendente(INICIO.minus(Duration.ofDays(31)));

    assertThat(enviadas().tamanho()).isOne();
    assertThat(falhadas().tamanho()).isOne();

    enviadas().expirar(limite(enviadas()));
    falhadas().expirar(limite(falhadas()));

    assertThat(enviadas().tamanho()).isZero();
    assertThat(falhadas().tamanho()).isZero();
  }
}
