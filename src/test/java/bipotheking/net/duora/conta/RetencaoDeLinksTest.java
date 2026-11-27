package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A retenção dos links de entrada: some o link vencido há mais de um dia. */
class RetencaoDeLinksTest extends BancoDeTeste {

  @Autowired private List<PoliticaDeRetencao> politicas;

  private PoliticaDeRetencao links() {
    return politicas.stream()
        .filter(p -> p.nome().equals("links-de-entrada"))
        .findFirst()
        .orElseThrow();
  }

  private Instant limite() {
    return INICIO.minus(links().prazo());
  }

  /** {@code quantos} links do mesmo e-mail, criados 15 min antes de vencer em {@code expiraEm}. */
  private void inserirLinks(int quantos, Instant expiraEm) {
    jdbc.sql(
            """
            insert into link_de_entrada (id, email, token_hash, criado_em, expira_em)
            select gen_random_uuid(), 'ana@teste.duora.invalid',
                   sha256(convert_to(gen_random_uuid()::text, 'UTF8')),
                   :expira - interval '15 minutes', :expira
            from generate_series(1, :quantos)
            """)
        .param("expira", OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC))
        .param("quantos", quantos)
        .update();
  }

  private long linhas() {
    return jdbc.sql("select count(*) from link_de_entrada").query(Long.class).single();
  }

  @Test
  void linkVencidoHaMaisDeUmDiaSome() {
    inserirLinks(1, INICIO.minus(Duration.ofDays(1)).minusSeconds(1));

    assertThat(links().expirar(limite())).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void linkVencidoHaUmDiaExatoFica() {
    inserirLinks(1, INICIO.minus(Duration.ofDays(1)));

    assertThat(links().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void linkValidoFica() {
    inserirLinks(1, INICIO.plus(Duration.ofMinutes(15)));

    assertThat(links().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void apaga2500EmLotes() {
    inserirLinks(2500, INICIO.minus(Duration.ofDays(2)));

    assertThat(links().expirar(limite())).isEqualTo(2500);
    assertThat(linhas()).isZero();
  }

  @Test
  void tamanhoContaSoOsVencidosAlemDoPrazo() {
    inserirLinks(3, INICIO.minus(Duration.ofDays(2)));
    inserirLinks(2, INICIO.minus(Duration.ofHours(2)));
    inserirLinks(1, INICIO.plus(Duration.ofMinutes(15)));

    assertThat(links().tamanho()).isEqualTo(3);
    links().expirar(limite());
    assertThat(links().tamanho()).isZero();
  }

  @Test
  void nomeEPrazo() {
    assertThat(links().nome()).isEqualTo("links-de-entrada");
    assertThat(links().prazo()).isEqualTo(Duration.parse("P1D"));
  }
}
