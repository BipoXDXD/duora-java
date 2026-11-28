package bipotheking.net.duora.pareamento;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class VetosDeParTest extends BancoDeTeste {

  @Autowired private VetosDePar vetos;
  @Autowired private ContasDeTeste contas;

  @Test
  void vetarDuasVezesNaoDuplica() {
    ParDeContas par = ParDeContas.de(contas.criarAtiva().id(), contas.criarAtiva().id());

    vetos.vetar(par, MotivoVeto.NAO);
    vetos.vetar(par, MotivoVeto.SAIDA);

    assertThat(linhas()).isEqualTo(1);
    assertThat(vetos.motivo(par)).contains(MotivoVeto.NAO);
  }

  @Test
  void vetoRealSobrepoeConectados() {
    ParDeContas par = ParDeContas.de(contas.criarAtiva().id(), contas.criarAtiva().id());

    vetos.vetar(par, MotivoVeto.CONECTADOS);
    vetos.vetar(par, MotivoVeto.DENUNCIA);

    assertThat(vetos.motivo(par)).contains(MotivoVeto.DENUNCIA);
  }

  @Test
  void conectadosNaoSobrepoeVetoReal() {
    ParDeContas par = ParDeContas.de(contas.criarAtiva().id(), contas.criarAtiva().id());

    vetos.vetar(par, MotivoVeto.BLOQUEIO);
    vetos.vetar(par, MotivoVeto.CONECTADOS);

    assertThat(vetos.motivo(par)).contains(MotivoVeto.BLOQUEIO);
  }

  @Test
  void valeNosDoisSentidos() {
    ContaId a = contas.criarAtiva().id();
    ContaId b = contas.criarAtiva().id();

    vetos.vetar(ParDeContas.de(a, b), MotivoVeto.SEM_DECISAO);

    assertThat(vetos.motivo(ParDeContas.de(b, a))).contains(MotivoVeto.SEM_DECISAO);
    assertThat(vetos.vetadosEntre(List.of(b, a))).containsExactly(ParDeContas.de(a, b));
  }

  @Test
  void vetadosEntreTrazSoParesDoConjunto() {
    ContaId a = contas.criarAtiva().id();
    ContaId b = contas.criarAtiva().id();
    ContaId c = contas.criarAtiva().id();
    ContaId d = contas.criarAtiva().id();
    vetos.vetar(ParDeContas.de(a, b), MotivoVeto.NAO);
    vetos.vetar(ParDeContas.de(b, c), MotivoVeto.SAIDA);
    vetos.vetar(ParDeContas.de(c, d), MotivoVeto.NAO);

    assertThat(vetos.vetadosEntre(List.of(a, b, c)))
        .containsExactlyInAnyOrder(ParDeContas.de(a, b), ParDeContas.de(b, c));
  }

  @Test
  void listaVaziaDevolveVazio() {
    assertThat(vetos.vetadosEntre(List.of())).isEmpty();
  }

  /**
   * Foco de revisão 1: com o bit mais alto diferente, {@link UUID#compareTo} (com sinal) poria a
   * conta {@code 8000…} antes da {@code 0000…}, e o {@code check (conta_menor < conta_maior)} do
   * banco recusaria o par.
   */
  @Test
  void parComBitAltoDiferenteGrava() {
    ContaId baixa = contaComId(new UUID(0L, 1L));
    ContaId alta = contaComId(new UUID(Long.MIN_VALUE, 1L));
    ParDeContas par = ParDeContas.de(alta, baixa);

    vetos.vetar(par, MotivoVeto.NAO);

    assertThat(vetos.motivo(par)).contains(MotivoVeto.NAO);
    assertThat(vetos.vetadosEntre(List.of(alta, baixa))).containsExactly(par);
  }

  private int linhas() {
    return jdbc.sql("select count(*) from par_vetado").query(Integer.class).single();
  }

  private ContaId contaComId(UUID id) {
    jdbc.sql(
            """
            insert into conta (id, email, estado, data_nascimento, criada_em, ativada_em)
            values (:id, :email, 'ATIVA', date '1990-05-10', :agora, :agora)
            """)
        .param("id", id)
        .param("email", id + "@teste.duora.invalid")
        .param("agora", OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC))
        .update();
    return new ContaId(id);
  }
}
