package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RetencaoDeIdempotenciaTest extends BancoDeTeste {

  @Autowired private List<PoliticaDeRetencao> politicas;

  private PoliticaDeRetencao chaves() {
    return politicas.stream()
        .filter(p -> p.nome().equals("chaves-de-idempotencia"))
        .findFirst()
        .orElseThrow();
  }

  private Instant limite() {
    return INICIO.minus(chaves().prazo());
  }

  private void inserirChave(Instant criadaEm, @Nullable String resposta) {
    OffsetDateTime em = OffsetDateTime.ofInstant(criadaEm, ZoneOffset.UTC);
    jdbc.sql(
            """
            insert into chave_de_idempotencia
                (escopo, chave, impressao, dono, resposta, criada_em, reservada_em)
            values ('entrar', 'chave-0001', 'impressao', gen_random_uuid(),
                    cast(:resposta as jsonb), :em, :em)
            """)
        .param("resposta", resposta, Types.VARCHAR)
        .param("em", em)
        .update();
  }

  private long linhas() {
    return jdbc.sql("select count(*) from chave_de_idempotencia").query(Long.class).single();
  }

  @Test
  void chaveDeVinteEQuatroHorasExatasFica() {
    inserirChave(INICIO.minus(Duration.ofHours(24)), "{\"ok\":true}");

    assertThat(chaves().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void chaveDeVinteEQuatroHorasEUmSegundoSome() {
    inserirChave(INICIO.minus(Duration.ofHours(24)).minusSeconds(1), "{\"ok\":true}");

    assertThat(chaves().expirar(limite())).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void reservaSemRespostaTambemSome() {
    inserirChave(INICIO.minus(Duration.ofHours(24)).minusSeconds(1), null);

    assertThat(chaves().expirar(limite())).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void tamanhoContaTodasAsChaves() {
    inserirChave(INICIO.minus(Duration.ofHours(1)), null);

    assertThat(chaves().tamanho()).isOne();
  }
}
