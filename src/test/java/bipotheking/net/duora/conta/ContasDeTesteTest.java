package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.perfil.Pendencia;
import bipotheking.net.duora.perfil.Perfis;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContasDeTesteTest extends BancoDeTeste {

  @Autowired private ContasDeTeste contas;
  @Autowired private ConsultaDeContas consulta;
  @Autowired private Perfis perfis;

  @Test
  void criarComApelidoDeixaProntoParaFila() {
    ContaDeTeste conta = contas.criar("Ana", LocalDate.of(1990, 5, 10));

    assertThat(perfis.prontoParaFila(conta.id())).isTrue();
    assertThat(perfis.publico(conta.id()))
        .hasValueSatisfying(publico -> {
          assertThat(publico.apelido()).isEqualTo("Ana");
          assertThat(publico.verificado()).isTrue();
        });
  }

  @Test
  void criarVerificadaTemSeloESemPerfil() {
    ContaDeTeste conta = contas.criarVerificada();

    assertThat(consulta.porId(conta.id()))
        .hasValueSatisfying(resumo -> {
          assertThat(resumo.estado()).isEqualTo(EstadoConta.ATIVA);
          assertThat(resumo.verificada()).isTrue();
        });
    assertThat(perfis.pendencias(conta.id())).containsExactly(Pendencia.PERFIL);
  }
}
