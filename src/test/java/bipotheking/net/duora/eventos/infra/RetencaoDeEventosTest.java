package bipotheking.net.duora.eventos.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RetencaoDeEventosTest extends BancoDeTeste {

  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private ContasDeTeste contas;

  private PoliticaDeRetencao eventos() {
    return politicas.stream()
        .filter(p -> p.nome().equals("eventos-de-conta"))
        .findFirst()
        .orElseThrow();
  }

  private Instant limite() {
    return INICIO.minus(eventos().prazo());
  }

  private void inserirEvento(Instant criadoEm) {
    ContaId conta = contas.criarAtiva().id();
    jdbc.sql(
            """
            insert into evento_conta (conta_id, nome, dados, criado_em)
            values (:conta, 'fila.estado', '{}', :em)
            """)
        .param("conta", conta.valor())
        .param("em", OffsetDateTime.ofInstant(criadoEm, ZoneOffset.UTC))
        .update();
  }

  private long linhas() {
    return jdbc.sql("select count(*) from evento_conta").query(Long.class).single();
  }

  @Test
  void eventoDeUmDiaExatoFica() {
    inserirEvento(INICIO.minus(Duration.ofDays(1)));

    assertThat(eventos().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void eventoDeUmDiaEUmSegundoSome() {
    inserirEvento(INICIO.minus(Duration.ofDays(1)).minusSeconds(1));

    assertThat(eventos().expirar(limite())).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void tamanhoContaSoOVencido() {
    inserirEvento(INICIO.minus(Duration.ofHours(1)));
    inserirEvento(INICIO.minus(Duration.ofHours(25)));

    assertThat(eventos().tamanho()).isOne();
  }
}
