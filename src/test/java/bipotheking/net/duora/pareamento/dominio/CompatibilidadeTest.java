package bipotheking.net.duora.pareamento.dominio;

import static bipotheking.net.duora.pareamento.dominio.CandidatoBuilder.candidato;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.pareamento.Candidato;
import bipotheking.net.duora.perfil.Genero;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.Raio;
import org.junit.jupiter.api.Test;

class CompatibilidadeTest {

  @Test
  void amizadeComAmizadeECompativel() {
    assertThat(Compatibilidade.compativeis(candidato(1).build(), candidato(2).build())).isTrue();
  }

  @Test
  void amizadeComNamoroSoNaoE() {
    Candidato namoro =
        candidato(2)
            .intencoes(Intencao.NAMORO)
            .genero(Genero.MULHER)
            .buscando(Genero.MULHER)
            .build();

    assertThat(Compatibilidade.compativeis(candidato(1).build(), namoro)).isFalse();
  }

  @Test
  void idadeNaBordaDaFaixaECompativel() {
    Candidato exigente = candidato(1).faixa(25, 30).build();

    assertThat(Compatibilidade.compativeis(exigente, candidato(2).idade(30).build())).isTrue();
  }

  @Test
  void idadeUmAnoForaNaoE() {
    Candidato exigente = candidato(1).faixa(25, 30).build();

    assertThat(Compatibilidade.compativeis(exigente, candidato(2).idade(31).build())).isFalse();
  }

  @Test
  void faixaDeUmLadoBastaParaRecusar() {
    Candidato aberto = candidato(1).idade(40).build();
    Candidato exigente = candidato(2).idade(28).faixa(18, 30).build();

    assertThat(Compatibilidade.compativeis(aberto, exigente)).isFalse();
  }

  @Test
  void namoroExigeGeneroNosDoisSentidos() {
    Candidato a =
        candidato(1)
            .intencoes(Intencao.NAMORO)
            .genero(Genero.MULHER)
            .buscando(Genero.HOMEM)
            .build();
    Candidato b =
        candidato(2).intencoes(Intencao.NAMORO).genero(Genero.HOMEM).buscando(Genero.HOMEM).build();

    assertThat(Compatibilidade.intencoesEmComum(a.preferencias(), b.preferencias())).isEmpty();
  }

  @Test
  void semGeneroProprioNaoHaNamoro() {
    Candidato semGenero = candidato(1).intencoes(Intencao.NAMORO).buscando(Genero.MULHER).build();
    Candidato b =
        candidato(2)
            .intencoes(Intencao.NAMORO)
            .genero(Genero.MULHER)
            .buscando(Genero.MULHER, Genero.HOMEM, Genero.PESSOA_NAO_BINARIA, Genero.OUTRO)
            .build();

    assertThat(Compatibilidade.intencoesEmComum(semGenero.preferencias(), b.preferencias()))
        .isEmpty();
  }

  @Test
  void namoroEAmizadeComGeneroIncompativelSobraAmizade() {
    Candidato a =
        candidato(1)
            .intencoes(Intencao.AMIZADE, Intencao.NAMORO)
            .genero(Genero.MULHER)
            .buscando(Genero.HOMEM)
            .build();
    Candidato b =
        candidato(2)
            .intencoes(Intencao.AMIZADE, Intencao.NAMORO)
            .genero(Genero.MULHER)
            .buscando(Genero.MULHER)
            .build();

    assertThat(Compatibilidade.intencoesEmComum(a.preferencias(), b.preferencias()))
        .containsExactly(Intencao.AMIZADE);
  }

  @Test
  void generoOutroCasaPeloEnum() {
    Candidato a =
        candidato(1).intencoes(Intencao.NAMORO).genero(Genero.OUTRO).buscando(Genero.OUTRO).build();
    Candidato b =
        candidato(2).intencoes(Intencao.NAMORO).genero(Genero.OUTRO).buscando(Genero.OUTRO).build();

    assertThat(Compatibilidade.intencoesEmComum(a.preferencias(), b.preferencias()))
        .containsExactly(Intencao.NAMORO);
  }

  @Test
  void raioObrigatorioDeUmLadoFiltra() {
    Candidato perto = candidato(1).raio(Raio.ATE_5_KM, true).celula("6gyf4").build();
    Candidato longe = candidato(2).celula("6gyft").build();

    assertThat(Compatibilidade.compativeis(perto, longe)).isFalse();
  }

  @Test
  void mesmoCasoSoPreferenciaNaoFiltra() {
    Candidato perto = candidato(1).raio(Raio.ATE_5_KM, false).celula("6gyf4").build();
    Candidato longe = candidato(2).celula("6gyft").build();

    assertThat(Compatibilidade.compativeis(perto, longe)).isTrue();
  }

  @Test
  void raioNaBordaPassa() {
    Candidato perto = candidato(1).raio(Raio.ATE_5_KM, true).celula("6gyf4").build();
    Candidato vizinho = candidato(2).celula("6gycf").build();

    assertThat(Compatibilidade.compativeis(perto, vizinho)).isTrue();
  }

  @Test
  void raioObrigatorioSemCelulaNaoE() {
    Candidato perto = candidato(1).raio(Raio.ATE_50_KM, true).celula("6gyf4").build();
    Candidato semCelula = candidato(2).build();

    assertThat(Compatibilidade.compativeis(perto, semCelula)).isFalse();
  }

  @Test
  void semLimiteObrigatorioNaoFiltra() {
    Candidato semLimite = candidato(1).raio(Raio.SEM_LIMITE, true).build();
    Candidato semCelula = candidato(2).build();

    assertThat(Compatibilidade.compativeis(semLimite, semCelula)).isTrue();
  }

  @Test
  void contaDeTesteNuncaComReal() {
    assertThat(Compatibilidade.compativeis(candidato(1).deTeste().build(), candidato(2).build()))
        .isFalse();
  }

  @Test
  void toStringDoCandidatoNaoExpoeGeneroNemCelula() {
    Candidato candidato =
        candidato(1)
            .intencoes(Intencao.NAMORO)
            .genero(Genero.MULHER)
            .buscando(Genero.MULHER)
            .celula("6gyf4")
            .build();

    assertThat(candidato.toString()).doesNotContain("MULHER").doesNotContain("6gyf4");
  }
}
