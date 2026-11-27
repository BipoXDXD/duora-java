package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.CodigoCadastro;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ContaTest {

  private static final ContaId ID =
      new ContaId(UUID.fromString("0196f1a0-0000-7000-8000-000000000001"));
  private static final Email EMAIL = new Email("ana@teste.duora.invalid");
  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);
  private static final Instant AGORA = Instant.parse("2026-11-22T12:00:00Z");
  private static final LocalDate HOJE = LocalDate.of(2026, 11, 22);

  @Test
  void pendenteSemNascimentoNemAtivacaoValida() {
    assertThat(conta(EstadoConta.PENDENTE, EMAIL, null, null).estado())
        .isEqualTo(EstadoConta.PENDENTE);
  }

  @Test
  void excluidaSemEmailValida() {
    assertThat(conta(EstadoConta.EXCLUIDA, null, null, null).email()).isNull();
  }

  @ParameterizedTest
  @EnumSource(value = EstadoConta.class, names = "EXCLUIDA", mode = EnumSource.Mode.EXCLUDE)
  void semEmailForaDeExcluidaRecusada(EstadoConta estado) {
    assertThatThrownBy(() -> conta(estado, null, NASCIMENTO, AGORA))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void excluidaComEmailRecusada() {
    assertThatThrownBy(() -> conta(EstadoConta.EXCLUIDA, EMAIL, null, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = EstadoConta.class,
      names = {"AGUARDANDO_VERIFICACAO", "ATIVA", "SUSPENSA", "BANIDA"})
  void semNascimentoDepoisDoCadastroRecusada(EstadoConta estado) {
    assertThatThrownBy(() -> conta(estado, EMAIL, null, AGORA))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void ativaSemAtivacaoRecusada() {
    assertThatThrownBy(() -> conta(EstadoConta.ATIVA, EMAIL, NASCIMENTO, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void banidaSemAtivacaoValida() {
    assertThat(conta(EstadoConta.BANIDA, EMAIL, NASCIMENTO, null).ativadaEm()).isNull();
  }

  @Test
  void pendenteMaiorConclui() {
    assertThat(pendente().concluirCadastro(NASCIMENTO, HOJE))
        .isInstanceOf(ResultadoDoCadastro.Concluido.class);
  }

  @Test
  void pendenteMenorRecusada() {
    assertThat(pendente().concluirCadastro(LocalDate.of(2008, 11, 23), HOJE))
        .isInstanceOf(ResultadoDoCadastro.RecusadoPorIdade.class);
  }

  @Test
  void aguardandoNaoConcluiDeNovo() {
    Conta aguardando = conta(EstadoConta.AGUARDANDO_VERIFICACAO, EMAIL, NASCIMENTO, null);

    assertThatThrownBy(() -> aguardando.concluirCadastro(NASCIMENTO, HOJE))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoCadastro.CADASTRO_JA_CONCLUIDO));
  }

  @Test
  void pendenteSemDataRecusada() {
    assertThatThrownBy(() -> pendente().concluirCadastro(null, HOJE))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoCadastro.DATA_INVALIDA));
  }

  @Test
  void aguardandoSemDataRecebeCadastroJaConcluido() {
    Conta aguardando = conta(EstadoConta.AGUARDANDO_VERIFICACAO, EMAIL, NASCIMENTO, null);

    assertThatThrownBy(() -> aguardando.concluirCadastro(null, HOJE))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoCadastro.CADASTRO_JA_CONCLUIDO));
  }

  @Test
  void dataFuturaRecusada() {
    assertThatThrownBy(() -> pendente().concluirCadastro(HOJE.plusDays(1), HOJE))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoCadastro.DATA_INVALIDA));
  }

  @Test
  void dataAntesDe1900Recusada() {
    assertThatThrownBy(() -> pendente().concluirCadastro(LocalDate.of(1899, 12, 31), HOJE))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoCadastro.DATA_INVALIDA));
  }

  @Test
  void data1900_01_01Aceita() {
    assertThat(pendente().concluirCadastro(LocalDate.of(1900, 1, 1), HOJE))
        .isInstanceOf(ResultadoDoCadastro.Concluido.class);
  }

  @Test
  void toStringSoTrazIdEEstado() {
    Conta ativa = conta(EstadoConta.ATIVA, EMAIL, NASCIMENTO, AGORA);

    assertThat(ativa.toString())
        .isEqualTo("Conta[id=" + ID + ", estado=ATIVA]")
        .doesNotContain("1990", "ana@");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void resumoLevaOSeloRecebido(boolean verificada) {
    Conta ativa = conta(EstadoConta.ATIVA, EMAIL, NASCIMENTO, AGORA);

    assertThat(ativa.resumo(verificada).verificada()).isEqualTo(verificada);
  }

  private static Conta pendente() {
    return conta(EstadoConta.PENDENTE, EMAIL, null, null);
  }

  private static Conta conta(
      EstadoConta estado,
      @Nullable Email email,
      @Nullable LocalDate nascimento,
      @Nullable Instant ativadaEm) {
    return new Conta(ID, email, estado, nascimento, AGORA, ativadaEm);
  }
}
