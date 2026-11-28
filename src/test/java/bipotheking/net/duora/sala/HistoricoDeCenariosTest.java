package bipotheking.net.duora.sala;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HistoricoDeCenariosTest extends BancoDeTeste {

  @Autowired private HistoricoDeCenarios historico;
  @Autowired private ContasDeTeste contas;

  @Test
  void registrarDuasVezesUmaLinha() {
    ContaId conta = contas.criarAtiva().id();

    historico.registrar(conta, "terraco");
    relogio.avancar(Duration.ofHours(1));
    historico.registrar(conta, "terraco");

    assertThat(historico.jogados(conta)).containsExactly("terraco");
    assertThat(
            jdbc.sql("select primeira_vez_em from cenario_jogado where conta_id = :c")
                .param("c", conta.valor())
                .query(OffsetDateTime.class)
                .list())
        .extracting(OffsetDateTime::toInstant)
        .containsExactly(INICIO);
  }

  @Test
  void semHistoricoVazio() {
    ContaId conta = contas.criarAtiva().id();

    assertThat(historico.jogados(conta)).isEmpty();
  }

  @Test
  void historicoESoDaConta() {
    ContaId lia = contas.criarAtiva().id();
    ContaId rui = contas.criarAtiva().id();

    historico.registrar(lia, "terraco");

    assertThat(historico.jogados(rui)).isEmpty();
    assertThat(historico.jogados(lia)).containsExactly("terraco");
  }
}
