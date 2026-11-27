package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.dominio.FinalidadeDaTentativa;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A retenção das tentativas de verificação de idade: some a tentativa criada há mais de 1 dia. */
class RetencaoDeTentativasTest extends BancoDeTeste {

  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private RepositorioDeVerificacoes verificacoes;
  @Autowired private ContasDeTeste contas;

  private ContaId conta;

  @BeforeEach
  void criarConta() {
    conta = contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, LocalDate.of(1990, 5, 10)).id();
  }

  private PoliticaDeRetencao tentativas() {
    return politicas.stream()
        .filter(p -> p.nome().equals("tentativas-de-verificacao"))
        .findFirst()
        .orElseThrow();
  }

  private void reservarEm(Instant criadaEm) {
    verificacoes.reservar(conta, FinalidadeDaTentativa.SELFIE, 3, criadaEm);
  }

  private Instant limite() {
    return INICIO.minus(tentativas().prazo());
  }

  private long linhas() {
    return jdbc.sql("select count(*) from tentativa_verificacao").query(Long.class).single();
  }

  @Test
  void tentativaDe25hSome() {
    reservarEm(INICIO.minus(Duration.ofHours(25)));

    assertThat(tentativas().expirar(limite())).isOne();
    assertThat(linhas()).isZero();
  }

  @Test
  void tentativaDe23hFica() {
    reservarEm(INICIO.minus(Duration.ofHours(23)));

    assertThat(tentativas().expirar(limite())).isZero();
    assertThat(linhas()).isOne();
  }

  @Test
  void tamanhoContaSoAsVencidasAntesDeExpirar() {
    reservarEm(INICIO.minus(Duration.ofHours(25)));
    reservarEm(INICIO.minus(Duration.ofHours(23)));

    assertThat(tentativas().tamanho()).isOne();
  }

  @Test
  void nomeEPrazo() {
    assertThat(tentativas().nome()).isEqualTo("tentativas-de-verificacao");
    assertThat(tentativas().prazo()).isEqualTo(Duration.parse("P1D"));
  }
}
