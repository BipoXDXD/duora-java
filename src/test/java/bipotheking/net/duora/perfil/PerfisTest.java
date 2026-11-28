package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.perfil.dominio.AjustesDeBusca;
import bipotheking.net.duora.perfil.dominio.Apelido;
import bipotheking.net.duora.perfil.dominio.Bio;
import bipotheking.net.duora.perfil.dominio.DadosDeNamoro;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Intencoes;
import bipotheking.net.duora.perfil.dominio.PronomesEscolhidos;
import bipotheking.net.duora.perfil.dominio.RepositorioDeLocalizacao;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.Variante;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** As projeções do perfil, com o relógio em 2026-11-22T12:00Z. */
class PerfisTest extends BancoDeTeste {

  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);
  private static final CelulaGeohash CELULA = new CelulaGeohash("6gyf4");

  @Autowired private Perfis perfis;
  @Autowired private ContasDeTeste contas;
  @Autowired private ServicoDePerfil servicoDePerfil;
  @Autowired private ServicoDePreferencias servicoDePreferencias;
  @Autowired private ServicoDeNamoro servicoDeNamoro;
  @Autowired private ServicoDeOrientacao servicoDeOrientacao;
  @Autowired private PausaDaConta pausa;
  @Autowired private RepositorioDeLocalizacao localizacoes;
  @Autowired private FotosDeTeste fotos;
  @Autowired private PerfisDeTeste perfisDeTeste;

  @Test
  void publicoTrazApelidoPronomesEVerificado() {
    ContaId conta = contas.criarVerificada().id();
    salvarPerfil(conta, Set.of(Intencao.AMIZADE), Pronomes.ELE_DELE);

    PerfilPublico publico = perfis.publico(conta).orElseThrow();

    assertThat(publico)
        .isEqualTo(
            new PerfilPublico(conta, "Bia", Optional.empty(), Optional.of("ele/dele"), true));
  }

  @Test
  void semVerificacaoNaoTrazSelo() {
    ContaId conta = contas.criarAtiva().id();
    salvarPerfil(conta, Set.of(Intencao.AMIZADE), null);

    assertThat(perfis.publico(conta).orElseThrow().verificado()).isFalse();
  }

  @Test
  void publicoSemFotoVemVazio() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();

    assertThat(perfis.publico(conta).orElseThrow().fotoBorradaUrl()).isEmpty();
  }

  @Test
  void publicoComFotoAprovadaApontaParaBorrada() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();
    FotoId foto = fotos.enviarAprovada(conta);

    assertThat(perfis.publico(conta).orElseThrow().fotoBorradaUrl())
        .contains(UrlDaFoto.de(foto, Variante.BORRADA));
  }

  @Test
  void completoTrazIdade() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();

    assertThat(perfis.completo(conta).orElseThrow().idade()).isEqualTo(36);
  }

  @Test
  void completoApontaParaNitida() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();
    FotoId foto = fotos.enviarAprovada(conta);

    assertThat(perfis.completo(conta).orElseThrow().fotoUrl())
        .contains(UrlDaFoto.de(foto, Variante.NITIDA));
  }

  @Test
  void completoSoTrazOrientacaoComMostrar() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();

    servicoDeOrientacao.salvar(conta, Orientacao.LESBICA, null, false, true);
    assertThat(perfis.completo(conta).orElseThrow().orientacao()).isEmpty();

    servicoDeOrientacao.salvar(conta, Orientacao.LESBICA, null, true, true);
    assertThat(perfis.completo(conta).orElseThrow().orientacao()).contains("Lésbica");
  }

  @Test
  void publicoNuncaTrazOrientacao() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();
    servicoDeOrientacao.salvar(conta, Orientacao.LESBICA, null, true, true);

    PerfilPublico publico = perfis.publico(conta).orElseThrow();

    assertThat(PerfilPublico.class.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly("conta", "apelido", "fotoBorradaUrl", "pronomes", "verificado");
    assertThat(publico.toString()).doesNotContain("Lésbica").doesNotContain("LESBICA");
  }

  @Test
  void preferenciasMontaTudo() {
    ContaId conta = contas.criarAtiva().id();
    salvarPerfil(conta, EnumSet.of(Intencao.AMIZADE, Intencao.NAMORO), null);
    localizacoes.consentirEGravar(conta, CELULA, relogio.instant());
    servicoDePreferencias.salvar(
        conta, null, new AjustesDeBusca(new FaixaEtaria(25, 40), Raio.ATE_20_KM, true));
    servicoDeNamoro.salvar(
        conta,
        DadosDeNamoro.de(Genero.MULHER, null, Set.of(Genero.HOMEM, Genero.PESSOA_NAO_BINARIA)),
        true);

    assertThat(perfis.preferencias(conta))
        .contains(
            new Preferencias(
                EnumSet.of(Intencao.AMIZADE, Intencao.NAMORO),
                new FaixaEtaria(25, 40),
                Raio.ATE_20_KM,
                true,
                Optional.of(Genero.MULHER),
                EnumSet.of(Genero.HOMEM, Genero.PESSOA_NAO_BINARIA)));
  }

  @Test
  void preferenciasSemNamoroVemVazias() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();

    Preferencias preferencias = perfis.preferencias(conta).orElseThrow();

    assertThat(preferencias.generoProprio()).isEmpty();
    assertThat(preferencias.generosBuscados()).isEmpty();
  }

  @Test
  void preferenciasSemAjustesUsaPadrao() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();

    assertThat(perfis.preferencias(conta))
        .contains(
            new Preferencias(
                EnumSet.of(Intencao.AMIZADE),
                FaixaEtaria.TODAS,
                Raio.SEM_LIMITE,
                false,
                Optional.empty(),
                Set.of()));
  }

  @Test
  void celulaDaLocalizacao() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();
    localizacoes.consentirEGravar(conta, CELULA, relogio.instant());

    assertThat(perfis.celula(conta)).contains(CELULA);
  }

  @Test
  void contaSuspensaFicaDeFora() {
    ContaId conta = contas.criar(EstadoConta.SUSPENSA, NASCIMENTO).id();
    salvarPerfil(conta, Set.of(Intencao.AMIZADE), null);
    localizacoes.consentirEGravar(conta, CELULA, relogio.instant());

    assertThat(perfis.publico(conta)).isEmpty();
    assertThat(perfis.completo(conta)).isEmpty();
    assertThat(perfis.preferencias(conta)).isEmpty();
    assertThat(perfis.celula(conta)).isEmpty();
    assertThat(perfis.prontoParaFila(conta)).isFalse();
    assertThat(perfis.pendencias(conta)).isEmpty();
  }

  @Test
  void semPerfilNaoFicaProntoParaFila() {
    ContaId conta = contas.criarVerificada().id();

    assertThat(perfis.prontoParaFila(conta)).isFalse();
    assertThat(perfis.pendencias(conta)).containsExactly(Pendencia.PERFIL);
  }

  @Test
  void namoroSemDadosNaoFicaProntoParaFila() {
    ContaId conta = contas.criarAtiva().id();
    salvarPerfil(conta, Set.of(Intencao.NAMORO), null);

    assertThat(perfis.prontoParaFila(conta)).isFalse();
    assertThat(perfis.pendencias(conta)).containsExactly(Pendencia.DADOS_DE_NAMORO);
  }

  @Test
  void pausadaNaoFicaProntaParaFila() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();
    pausa.pausar(conta);

    assertThat(perfis.pausada(conta)).isTrue();
    assertThat(perfis.prontoParaFila(conta)).isFalse();
    assertThat(perfis.pendencias(conta)).isEmpty();
  }

  @Test
  void pausadaContinuaComPerfilPublico() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();
    pausa.pausar(conta);

    assertThat(perfis.publico(conta)).isPresent();
    assertThat(perfis.completo(conta)).isPresent();
  }

  @Test
  void semFotoFicaProntoParaFila() {
    ContaId conta = contas.criarAtiva().id();
    salvarPerfil(conta, Set.of(Intencao.AMIZADE), null);

    assertThat(perfis.prontoParaFila(conta)).isTrue();
  }

  @Test
  void perfisDeTesteDefineTudo() {
    ContaId conta = contas.criarVerificada().id();
    Preferencias desejadas =
        new Preferencias(
            EnumSet.of(Intencao.NAMORO),
            new FaixaEtaria(30, 45),
            Raio.ATE_50_KM,
            true,
            Optional.of(Genero.OUTRO),
            EnumSet.of(Genero.MULHER));

    perfisDeTeste.definir(conta, "Cris", desejadas, Optional.of(CELULA));

    assertThat(perfis.preferencias(conta)).contains(desejadas);
    assertThat(perfis.celula(conta)).contains(CELULA);
    assertThat(perfis.publico(conta).orElseThrow().apelido()).isEqualTo("Cris");
    assertThat(servicoDeNamoro.meus(conta).orElseThrow().descricao())
        .hasValueSatisfying(d -> assertThat(d.valor()).isEqualTo("teste"));
    assertThat(perfis.prontoParaFila(conta)).isTrue();
  }

  private void salvarPerfil(ContaId conta, Set<Intencao> intencoes, @Nullable Pronomes pronomes) {
    servicoDePerfil.salvar(
        conta,
        null,
        new EdicaoDoPerfil(
            Apelido.de("Bia"),
            pronomes == null ? Optional.empty() : PronomesEscolhidos.de(pronomes, null),
            new Intencoes(intencoes),
            Bio.de("oi")));
  }
}
