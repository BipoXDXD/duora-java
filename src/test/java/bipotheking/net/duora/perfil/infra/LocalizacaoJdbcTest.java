package bipotheking.net.duora.perfil.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.dominio.RepositorioDeLocalizacao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * O relógio do servidor pode voltar (ajuste do NTP, outro contêiner adiantado): a atualização nunca
 * recua para antes do consentimento, senão o check da tabela vira um 500.
 */
class LocalizacaoJdbcTest extends BancoDeTeste {

  private static final CelulaGeohash SAO_PAULO = new CelulaGeohash("6gyf4");
  private static final CelulaGeohash RIO = new CelulaGeohash("75cm8");
  private static final Instant CONSENTIMENTO = Instant.parse("2026-11-28T12:00:00Z");
  private static final Instant RELOGIO_ATRASADO = CONSENTIMENTO.minus(Duration.ofMinutes(5));

  @Autowired private RepositorioDeLocalizacao localizacoes;
  @Autowired private ContasDeTeste contas;

  @Test
  void atualizarComORelogioAtrasadoNaoRecuaAData() {
    ContaId conta = contas.criarAtiva().id();
    localizacoes.consentirEGravar(conta, SAO_PAULO, CONSENTIMENTO);

    boolean atualizou = localizacoes.atualizar(conta, RIO, RELOGIO_ATRASADO);

    assertThat(atualizou).isTrue();
    assertThat(localizacoes.celula(conta)).contains(RIO);
    assertThat(localizacoes.atualizadaEm(conta)).contains(CONSENTIMENTO);
  }

  @Test
  void consentirDeNovoComORelogioAtrasadoNaoRecuaAData() {
    ContaId conta = contas.criarAtiva().id();
    localizacoes.consentirEGravar(conta, SAO_PAULO, CONSENTIMENTO);

    localizacoes.consentirEGravar(conta, RIO, RELOGIO_ATRASADO);

    assertThat(localizacoes.celula(conta)).contains(RIO);
    assertThat(localizacoes.atualizadaEm(conta)).contains(CONSENTIMENTO);
  }
}
