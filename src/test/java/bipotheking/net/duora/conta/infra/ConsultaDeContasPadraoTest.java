package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConsultaDeContasPadraoTest extends BancoDeTeste {

  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);

  @Autowired private ConsultaDeContas consulta;
  @Autowired private ContasDeTeste contas;

  @Test
  void porIdsDevolveSoAsExistentes() {
    ContaId a = contas.criarAtiva().id();
    ContaId b = contas.criarAtiva().id();
    ContaId inexistente = new ContaId(UUID.randomUUID());

    Map<ContaId, ContaResumo> resumos = consulta.porIds(List.of(a, b, inexistente));

    assertThat(resumos).containsOnlyKeys(a, b);
  }

  @Test
  void porIdsVazioDevolveVazio() {
    assertThat(consulta.porIds(List.of())).isEmpty();
  }

  @Test
  void porIdsTrazOSeloVerificado() {
    ContaId verificada = contas.criarVerificada().id();
    ContaId semSelo = contas.criarAtiva().id();

    Map<ContaId, ContaResumo> resumos = consulta.porIds(List.of(verificada, semSelo));

    assertThat(resumos.get(verificada).verificada()).isTrue();
    assertThat(resumos.get(semSelo).verificada()).isFalse();
  }

  @Test
  void porIdMantemOComportamento() {
    ContaId id = contas.criarVerificada().id();

    assertThat(consulta.porId(id))
        .contains(new ContaResumo(id, EstadoConta.ATIVA, NASCIMENTO, true, INICIO, true));
  }
}
