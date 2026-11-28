package bipotheking.net.duora.pareamento.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import bipotheking.net.duora.pareamento.MotivoVeto;
import bipotheking.net.duora.pareamento.ParDeContas;
import bipotheking.net.duora.pareamento.VetosDePar;
import bipotheking.net.duora.web.BancoDeTeste;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A conta excluída vira lápide (a linha não sai), então o {@code on delete cascade} de {@code
 * par_vetado} nunca dispara: os vetos saem pelo gancho da exclusão.
 */
class PareamentoAoExcluirContaTest extends BancoDeTeste {

  @Autowired private VetosDePar vetos;
  @Autowired private ContasDeTeste contas;
  @Autowired private ExclusaoDeConta exclusao;

  @Test
  void excluirContaApagaOsVetosDosDoisLados() {
    ContaId a = contas.criarAtiva().id();
    ContaId b = contas.criarAtiva().id();
    ContaId c = contas.criarAtiva().id();
    ContaId d = contas.criarAtiva().id();
    vetos.vetar(ParDeContas.de(a, b), MotivoVeto.NAO);
    vetos.vetar(ParDeContas.de(b, c), MotivoVeto.SAIDA);
    vetos.vetar(ParDeContas.de(d, a), MotivoVeto.BLOQUEIO);

    exclusao.excluir(a, true);

    assertThat(vetos.vetadosEntre(List.of(a, b, c, d))).containsExactly(ParDeContas.de(b, c));
  }
}
