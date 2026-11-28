package bipotheking.net.duora.perfil.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.dominio.RepositorioDeLocalizacao;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A célula some 90 dias depois da última atualização. */
class RetencaoDeLocalizacoesTest extends BancoDeTeste {

  private static final CelulaGeohash SAO_PAULO = new CelulaGeohash("6gyf4");

  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private RepositorioDeLocalizacao localizacoes;
  @Autowired private ContasDeTeste contas;

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
