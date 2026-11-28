package bipotheking.net.duora.pareamento;

import static bipotheking.net.duora.pareamento.dominio.CandidatoBuilder.T0;
import static bipotheking.net.duora.pareamento.dominio.CandidatoBuilder.candidato;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PareamentoTest extends BancoDeTeste {

  private static final Duration CICLO = Duration.ofSeconds(5);

  @Autowired private Pareamento pareamento;
  @Autowired private VetosDePar vetos;

  @Test
  void parVetadoNoBancoNaoEPareado() {
    Candidato a = candidato(1).build();
    Candidato b = candidato(2).entrouSegundosDepois(1).build();
    assertThat(pareamento.compativeis(a, b)).isTrue();
    vetos.vetar(ParDeContas.de(gravar(a), gravar(b)), MotivoVeto.NAO);

    ResultadoDoCiclo resultado = pareamento.parear(List.of(a, b), 10, T0, CICLO);

    assertThat(resultado.pares()).isEmpty();
    assertThat(resultado.semPar()).containsExactly(a, b);
  }

  @Test
  void intencoesEmComumDelegaAoDominio() {
    Candidato a = candidato(1).intencoes(Intencao.AMIZADE, Intencao.NAMORO).build();
    Candidato b = candidato(2).intencoes(Intencao.AMIZADE).build();

    assertThat(pareamento.intencoesEmComum(a.preferencias(), b.preferencias()))
        .containsExactly(Intencao.AMIZADE);
  }

  /** A conta do candidato no banco, para o veto respeitar a chave estrangeira. */
  private ContaId gravar(Candidato candidato) {
    ContaId conta = candidato.conta();
    jdbc.sql(
            """
            insert into conta (id, email, estado, data_nascimento, criada_em, ativada_em)
            values (:id, :email, 'ATIVA', date '1990-05-10', :agora, :agora)
            """)
        .param("id", conta.valor())
        .param("email", conta.valor() + "@teste.duora.invalid")
        .param("agora", OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC))
        .update();
    return conta;
  }
}
