package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

class ContasJdbcTest extends BancoDeTeste {

  private static final Email EMAIL = new Email("ana@teste.duora.invalid");

  @Autowired private RepositorioDeContas repositorio;
  @Autowired private ConsultaDeContas consulta;
  @Autowired private ContasDeTeste contas;
  @Autowired private GeradorDeIds geradorDeIds;

  @Test
  void emailUnicoNoBanco() {
    repositorio.criarPendente(EMAIL);

    assertThatThrownBy(() -> repositorio.criarPendente(EMAIL))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void excluidaSemEmailNaoColide() {
    inserir(EstadoConta.EXCLUIDA, null, null);
    inserir(EstadoConta.EXCLUIDA, null, null);

    Integer excluidas =
        jdbc.sql("select count(*) from conta where estado = 'EXCLUIDA' and email is null")
            .query(Integer.class)
            .single();
    assertThat(excluidas).isEqualTo(2);
  }

  @Test
  void checkRecusaAtivaSemNascimento() {
    assertThatThrownBy(() -> inserir(EstadoConta.ATIVA, "bia@teste.duora.invalid", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void porIdDevolveResumo() {
    ContaId id = contas.criarAtiva().id();

    assertThat(consulta.porId(id))
        .contains(new ContaResumo(id, EstadoConta.ATIVA, LocalDate.of(1990, 5, 10), false, INICIO));
  }

  @Test
  void porEmailDevolveAContaPendente() {
    ContaId id = repositorio.criarPendente(EMAIL);

    assertThat(repositorio.porEmail(EMAIL))
        .contains(new Conta(id, EMAIL, EstadoConta.PENDENTE, null, INICIO, null));
  }

  @Test
  void porIdDesconhecidoVemVazio() {
    ContaId desconhecida = new ContaId(geradorDeIds.novo());

    assertThat(repositorio.porId(desconhecida)).isEmpty();
    assertThat(consulta.porId(desconhecida)).isEmpty();
  }

  private void inserir(
      EstadoConta estado, @Nullable String email, @Nullable LocalDate dataDeNascimento) {
    UUID id = geradorDeIds.novo();
    jdbc.sql(
            """
            insert into conta (id, email, estado, data_nascimento, criada_em)
            values (:id, :email, :estado, :nascimento, :agora)
            """)
        .param("id", id)
        .param("email", email)
        .param("estado", estado.name())
        .param("nascimento", dataDeNascimento)
        .param("agora", INICIO.atOffset(ZoneOffset.UTC))
        .update();
  }
}
