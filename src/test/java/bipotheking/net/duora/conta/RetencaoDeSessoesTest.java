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

/** A retenção das sessões: some a sessão cujo {@code expira_em} já passou. */
class RetencaoDeSessoesTest extends BancoDeTeste {

  @Autowired private List<PoliticaDeRetencao> politicas;

  private PoliticaDeRetencao sessoes() {
    return politicas.stream()
        .filter(p -> p.nome().equals("sessoes-expiradas"))
        .findFirst()
        .orElseThrow();
  }

  private Instant limite() {
    return INICIO.minus(sessoes().prazo());
  }

  /** Uma conta pendente nova com uma sessão que vence em {@code expiraEm}. */
  private void inserirSessao(Instant expiraEm) {
    jdbc.sql(
            """
            with nova as (
                insert into conta (id, email, estado, criada_em)
                values (gen_random_uuid(),
                        gen_random_uuid()::text || '@teste.duora.invalid', 'PENDENTE', :agora)
                returning id)
            insert into sessao (id, conta_id, token_hash, criada_em, expira_em, renovada_em)
            select gen_random_uuid(), nova.id,
                   sha256(convert_to(gen_random_uuid()::text, 'UTF8')),
                   :agora, :expira, :agora
            from nova
            """)
        .param("agora", OffsetDateTime.ofInstant(INICIO.minus(Duration.ofDays(30)), ZoneOffset.UTC))
        .param("expira", OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC))
        .update();
  }

  private long linhas() {
    return jdbc.sql("select count(*) from sessao").query(Long.class).single();
  }

  @Test
  void sessaoVencidaSome() {
    inserirSessao(INICIO.minusSeconds(1));

    assertThat(sessoes().expirar(limite())).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void sessaoQueVenceAgoraFica() {
    inserirSessao(INICIO);

    assertThat(sessoes().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void sessaoValidaFica() {
    inserirSessao(INICIO.plus(Duration.ofDays(30)));

    assertThat(sessoes().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void tamanhoContaVencidas() {
    inserirSessao(INICIO.minusSeconds(1));
    inserirSessao(INICIO.minus(Duration.ofDays(1)));
    inserirSessao(INICIO.plus(Duration.ofDays(30)));

    assertThat(sessoes().tamanho()).isEqualTo(2);
    sessoes().expirar(limite());
    assertThat(sessoes().tamanho()).isZero();
  }

  @Test
  void nomeEPrazo() {
    assertThat(sessoes().nome()).isEqualTo("sessoes-expiradas");
    assertThat(sessoes().prazo()).isEqualTo(Duration.parse("PT0S"));
  }
}
