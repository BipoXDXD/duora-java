package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.retencao.ExpiracaoDeDados;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * A retenção das denúncias públicas: a resolvida some 183 dias depois da resolução; a pendente fica
 * até alguém resolvê-la.
 */
class RetencaoDeDenunciasPublicasTest extends BancoDeTeste {

  private static final Duration PRAZO = Duration.ofDays(183);
  private static final Instant UM_ANO_ATRAS = INICIO.minus(Duration.ofDays(365));

  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private ExpiracaoDeDados expiracao;

  @Test
  void resolvidaCom183DiasE1sSome() {
    inserirResolvidas(1, UM_ANO_ATRAS, INICIO.minus(PRAZO).minusSeconds(1));

    assertThat(denuncias().expirar(limite())).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void resolvidaCom183DiasExatosFica() {
    inserirResolvidas(1, UM_ANO_ATRAS, INICIO.minus(PRAZO));

    assertThat(denuncias().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  /** O prazo corre da resolução, não da criação: o caso fechado ontem fica seis meses. */
  @Test
  void criadaHaUmAnoEResolvidaOntemFica() {
    inserirResolvidas(1, UM_ANO_ATRAS, INICIO.minus(Duration.ofDays(1)));

    assertThat(denuncias().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void pendenteAntigaFica() {
    inserirPendentes(1, INICIO.minus(Duration.ofDays(400)));

    assertThat(denuncias().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void apaga2500EmLotes() {
    inserirResolvidas(2500, UM_ANO_ATRAS, INICIO.minus(PRAZO).minus(Duration.ofDays(1)));

    assertThat(denuncias().expirar(limite())).isEqualTo(2500);
    assertThat(linhas()).isZero();
  }

  @Test
  void tamanhoContaAsResolvidasVencidas() {
    inserirResolvidas(3, UM_ANO_ATRAS, INICIO.minus(PRAZO).minus(Duration.ofDays(1)));
    inserirPendentes(2, UM_ANO_ATRAS);
    inserirResolvidas(1, UM_ANO_ATRAS, INICIO);

    assertThat(denuncias().tamanho()).isEqualTo(3);
    denuncias().expirar(limite());
    assertThat(denuncias().tamanho()).isZero();
  }

  @Test
  void resolvidaSemInstanteDeResolucaoRecusadaPeloBanco() {
    assertThatThrownBy(() ->
            jdbc.sql(
                    """
                    insert into denuncia_publica (id, motivo, descricao, criada_em, situacao)
                    values (gen_random_uuid(), 'GOLPE', 'Descrição de teste', :criada, 'RESOLVIDA')
                    """)
                .param("criada", OffsetDateTime.ofInstant(INICIO, ZoneOffset.UTC))
                .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** A tabela de retenção da política de privacidade, nos nomes e prazos do código. */
  @Test
  void prazosDaContaExpostos() {
    assertThat(expiracao.prazos())
        .containsEntry("links-de-entrada", Duration.parse("P1D"))
        .containsEntry("sessoes-expiradas", Duration.parse("PT0S"))
        .containsEntry("registros-de-acesso", Duration.parse("P183D"))
        .containsEntry("uso-conta-nova", Duration.parse("P8D"))
        .containsEntry("denuncias-publicas", Duration.parse("P183D"));
  }

  private PoliticaDeRetencao denuncias() {
    return politicas.stream()
        .filter(p -> p.nome().equals("denuncias-publicas"))
        .findFirst()
        .orElseThrow();
  }

  private Instant limite() {
    return INICIO.minus(denuncias().prazo());
  }

  private void inserirPendentes(int quantas, Instant criadaEm) {
    jdbc.sql(
            """
            insert into denuncia_publica (id, motivo, descricao, criada_em)
            select gen_random_uuid(), 'GOLPE', 'Descrição de teste da denúncia', :criada
            from generate_series(1, :quantas)
            """)
        .param("criada", OffsetDateTime.ofInstant(criadaEm, ZoneOffset.UTC))
        .param("quantas", quantas)
        .update();
  }

  private void inserirResolvidas(int quantas, Instant criadaEm, Instant resolvidaEm) {
    jdbc.sql(
            """
            insert into denuncia_publica
                (id, motivo, descricao, criada_em, situacao, resolvida_em)
            select gen_random_uuid(), 'GOLPE', 'Descrição de teste da denúncia', :criada,
                   'RESOLVIDA', :resolvida
            from generate_series(1, :quantas)
            """)
        .param("criada", OffsetDateTime.ofInstant(criadaEm, ZoneOffset.UTC))
        .param("resolvida", OffsetDateTime.ofInstant(resolvidaEm, ZoneOffset.UTC))
        .param("quantas", quantas)
        .update();
  }

  private long linhas() {
    return jdbc.sql("select count(*) from denuncia_publica").query(Long.class).single();
  }
}
