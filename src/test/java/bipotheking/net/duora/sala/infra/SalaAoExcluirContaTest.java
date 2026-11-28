package bipotheking.net.duora.sala.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import bipotheking.net.duora.sala.HistoricoDeCenarios;
import bipotheking.net.duora.sala.dominio.RepositorioDaFila;
import bipotheking.net.duora.web.BancoDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A conta excluída vira lápide (a linha não sai), então o {@code on delete cascade} de {@code
 * cenario_jogado} e {@code fila_espera} nunca dispara: o histórico e o lugar na fila saem pelo
 * gancho da exclusão.
 */
class SalaAoExcluirContaTest extends BancoDeTeste {

  @Autowired private HistoricoDeCenarios historico;
  @Autowired private ContasDeTeste contas;
  @Autowired private ExclusaoDeConta exclusao;
  @Autowired private RepositorioDaFila fila;

  @Test
  void excluirContaApagaOHistorico() {
    ContaId lia = contas.criarAtiva().id();
    ContaId rui = contas.criarAtiva().id();
    historico.registrar(lia, "terraco");
    historico.registrar(rui, "terraco");

    exclusao.excluir(lia, true);

    assertThat(historico.jogados(lia)).isEmpty();
    assertThat(historico.jogados(rui)).containsExactly("terraco");
  }

  @Test
  void excluirContaTiraDaFila() {
    ContaId lia = contas.criarAtiva().id();
    ContaId rui = contas.criarAtiva().id();
    fila.inserir(lia, INICIO, true);
    fila.inserir(rui, INICIO, true);

    exclusao.excluir(lia, true);

    assertThat(fila.daConta(lia)).isEmpty();
    assertThat(fila.daConta(rui)).isPresent();
  }
}
