package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * A retenção das denúncias públicas: a resolvida some 183 dias depois de criada; a pendente fica
 * até alguém resolvê-la.
 */
class RetencaoDeDenunciasPublicasTest extends BancoDeTeste {

  private static final Duration PRAZO = Duration.ofDays(183);

  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private ExpiracaoDeDados expiracao;

  @Test
  void resolvidaCom183DiasE1sSome() {
    inserir(1, "RESOLVIDA", INICIO.minus(PRAZO).minusSeconds(1));

    assertThat(denuncias().expirar(limite())).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void resolvidaCom183DiasExatosFica() {
    inserir(1, "RESOLVIDA", INICIO.minus(PRAZO));

    assertThat(denuncias().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void pendenteAntigaFica() {
    inserir(1, "PENDENTE", INICIO.minus(Duration.ofDays(400)));

    assertThat(denuncias().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void apaga2500EmLotes() {
    inserir(2500, "RESOLVIDA", INICIO.minus(PRAZO).minus(Duration.ofDays(1)));

    assertThat(denuncias().expirar(limite())).isEqualTo(2500);
    assertThat(linhas()).isZero();
  }

  @Test
  void tamanhoContaAsResolvidasVencidas() {
    inserir(3, "RESOLVIDA", INICIO.minus(PRAZO).minus(Duration.ofDays(1)));
    inserir(2, "PENDENTE", INICIO.minus(PRAZO).minus(Duration.ofDays(1)));
    inserir(1, "RESOLVIDA", INICIO);

    assertThat(denuncias().tamanho()).isEqualTo(3);
    denuncias().expirar(limite());
    assertThat(denuncias().tamanho()).isZero();
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

  private void inserir(int quantas, String situacao, Instant criadaEm) {
    jdbc.sql(
            """
            insert into denuncia_publica (id, motivo, descricao, criada_em, situacao)
            select gen_random_uuid(), 'GOLPE', 'Descrição de teste da denúncia', :criada, :situacao
            from generate_series(1, :quantas)
            """)
        .param("criada", OffsetDateTime.ofInstant(criadaEm, ZoneOffset.UTC))
        .param("situacao", situacao)
        .param("quantas", quantas)
        .update();
  }

  private long linhas() {
    return jdbc.sql("select count(*) from denuncia_publica").query(Long.class).single();
  }
}
