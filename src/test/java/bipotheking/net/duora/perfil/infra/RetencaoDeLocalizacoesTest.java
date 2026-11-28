package bipotheking.net.duora.perfil.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.FaixaEtaria;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.Raio;
import bipotheking.net.duora.perfil.ServicoDePerfil;
import bipotheking.net.duora.perfil.dominio.AjustesDeBusca;
import bipotheking.net.duora.perfil.dominio.AjustesVersionados;
import bipotheking.net.duora.perfil.dominio.Apelido;
import bipotheking.net.duora.perfil.dominio.Bio;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Intencoes;
import bipotheking.net.duora.perfil.dominio.RepositorioDeAjustes;
import bipotheking.net.duora.perfil.dominio.RepositorioDeLocalizacao;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A célula some 90 dias depois da última atualização. */
class RetencaoDeLocalizacoesTest extends BancoDeTeste {

  private static final CelulaGeohash SAO_PAULO = new CelulaGeohash("6gyf4");

  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private RepositorioDeLocalizacao localizacoes;
  @Autowired private ContasDeTeste contas;
  @Autowired private ServicoDePerfil perfis;
  @Autowired private RepositorioDeAjustes ajustes;

  private PoliticaDeRetencao retencao() {
    return politicas.stream()
        .filter(p -> p.nome().equals("localizacoes-paradas"))
        .findFirst()
        .orElseThrow();
  }

  private ContaId gravadaHa(Duration idade) {
    ContaId conta = contas.criarAtiva().id();
    localizacoes.consentirEGravar(conta, SAO_PAULO, INICIO.minus(idade));
    return conta;
  }

  private Instant limite() {
    return INICIO.minus(retencao().prazo());
  }

  @Test
  void vencidaSome() {
    ContaId vencida = gravadaHa(Duration.ofDays(91));
    ContaId recente = gravadaHa(Duration.ofDays(89));

    assertThat(retencao().expirar(limite())).isOne();

    assertThat(localizacoes.celula(vencida)).isEmpty();
    assertThat(localizacoes.celula(recente)).contains(SAO_PAULO);
  }

  @Test
  void localizacaoVencidaVoltaORaio() {
    ContaId vencida = comRaioDe20KmGravadoHa(Duration.ofDays(91));
    ContaId recente = comRaioDe20KmGravadoHa(Duration.ofDays(89));

    assertThat(retencao().expirar(limite())).isOne();

    assertThat(ajustes.buscar(vencida))
        .contains(
            new AjustesVersionados(
                new AjustesDeBusca(FaixaEtaria.TODAS, Raio.SEM_LIMITE, false), 2L));
    assertThat(ajustes.buscar(recente))
        .contains(
            new AjustesVersionados(
                new AjustesDeBusca(FaixaEtaria.TODAS, Raio.ATE_20_KM, true), 1L));
  }

  private ContaId comRaioDe20KmGravadoHa(Duration idade) {
    ContaId conta = gravadaHa(idade);
    perfis.salvar(
        conta,
        null,
        new EdicaoDoPerfil(
            Apelido.de("Bia"),
            Optional.empty(),
            new Intencoes(Set.of(Intencao.AMIZADE)),
            Bio.de(null)));
    assertThat(
            ajustes.criar(
                conta, new AjustesDeBusca(FaixaEtaria.TODAS, Raio.ATE_20_KM, true), INICIO))
        .isTrue();
    return conta;
  }

  @Test
  void tamanhoContaSoVencidas() {
    gravadaHa(Duration.ofDays(91));
    gravadaHa(Duration.ofDays(89));

    assertThat(retencao().tamanho()).isOne();
  }

  @Test
  void nomeEPrazo() {
    assertThat(retencao().nome()).isEqualTo("localizacoes-paradas");
    assertThat(retencao().prazo()).isEqualTo(Duration.parse("P90D"));
  }
}
