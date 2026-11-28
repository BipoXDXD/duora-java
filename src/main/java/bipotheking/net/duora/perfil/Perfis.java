package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.perfil.dominio.DadosDeNamoro;
import bipotheking.net.duora.perfil.dominio.DadosDeOrientacao;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.FichaDoPerfil;
import bipotheking.net.duora.perfil.dominio.LeituraDePerfis;
import bipotheking.net.duora.perfil.dominio.PronomesEscolhidos;
import bipotheking.net.duora.web.Variante;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * O perfil das contas em projeções, uma por audiência: {@link PerfilPublico} para quem encontra,
 * {@link PerfilCompleto} para uma conexão e {@link Preferencias} e célula só para o pareamento.
 * Cada projeção é montada de uma leitura só do perfil (mais a conta e, nas que mostram foto, a URL
 * da aprovada).
 *
 * <p>Só conta {@code ATIVA} aparece: as outras dão vazio ou falso. A pausa não esconde o perfil (a
 * conexão continua vendo); só tira a conta da fila.
 */
@Service
public class Perfis {

  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  private final ConsultaDeContas contas;
  private final LeituraDePerfis leitura;
  private final FotosDoPerfil fotos;
  private final Clock relogio;

  Perfis(ConsultaDeContas contas, LeituraDePerfis leitura, FotosDoPerfil fotos, Clock relogio) {
    this.contas = contas;
    this.leitura = leitura;
    this.fotos = fotos;
    this.relogio = relogio;
  }

  /**
   * A situação da conta diante da fila de encontros, numa leitura só: o que {@link #pausada},
   * {@link #prontoParaFila} e {@link #pendencias} dizem, juntos. Serve a quem precisa dos três de
   * uma vez, como o {@code GET /api/perfil}.
   */
  public record Prontidao(boolean pausada, boolean prontoParaFila, Set<Pendencia> pendencias) {

    private static final Prontidao FORA = new Prontidao(false, false, Set.of());

    public Prontidao {
      Objects.requireNonNull(pendencias, "pendencias");
      EnumSet<Pendencia> copia = EnumSet.noneOf(Pendencia.class);
      copia.addAll(pendencias);
      pendencias = Collections.unmodifiableSet(copia);
    }
  }

  /** O que se vê no encontro: apelido, foto borrada, pronomes e selo. */
  public Optional<PerfilPublico> publico(ContaId conta) {
    return ativa(conta)
        .map(ativa ->
            new PerfilPublico(
                conta,
                apelido(ativa.ficha()),
                fotos.urlDaAprovada(conta, Variante.BORRADA),
                pronomes(ativa.ficha()),
                ativa.resumo().verificada()));
  }

  /** O que uma conexão vê; a orientação só quando a pessoa marcou mostrá-la. */
  public Optional<PerfilCompleto> completo(ContaId conta) {
    return ativa(conta).map(ativa -> completo(conta, ativa));
  }

  /** O que o pareamento usa; nunca sai do servidor. */
  public Optional<Preferencias> preferencias(ContaId conta) {
    return ativa(conta).map(ativa -> preferencias(ativa.ficha()));
  }

  /** A célula geohash da conta, se ela ativou a localização. */
  public Optional<CelulaGeohash> celula(ContaId conta) {
    return ativa(conta).flatMap(ativa -> ativa.ficha().celula());
  }

  /**
   * Conta ativa, com perfil, sem pausa e, se quer namoro, com os dados de namoro. A foto não conta.
   */
  public boolean prontoParaFila(ContaId conta) {
    return prontidao(conta).prontoParaFila();
  }

  public boolean pausada(ContaId conta) {
    return prontidao(conta).pausada();
  }

  /** Sem perfil, {@code PERFIL}; com {@code NAMORO} sem os dados, {@code DADOS_DE_NAMORO}. */
  public Set<Pendencia> pendencias(ContaId conta) {
    return prontidao(conta).pendencias();
  }

  /** Pausa, prontidão e pendências numa leitura só. */
  public Prontidao prontidao(ContaId conta) {
    if (!estaAtiva(conta)) {
      return Prontidao.FORA;
    }
    Optional<FichaDoPerfil> ficha = leitura.ficha(conta);
    if (ficha.isEmpty()) {
      return new Prontidao(false, false, EnumSet.of(Pendencia.PERFIL));
    }
    FichaDoPerfil lida = ficha.get();
    boolean faltaNamoro = lida.perfil().dados().intencoes().querNamoro() && lida.namoro().isEmpty();
    Set<Pendencia> pendencias =
        faltaNamoro ? EnumSet.of(Pendencia.DADOS_DE_NAMORO) : EnumSet.noneOf(Pendencia.class);
    return new Prontidao(lida.pausada(), pendencias.isEmpty() && !lida.pausada(), pendencias);
  }

  private PerfilCompleto completo(ContaId conta, Ativa ativa) {
    FichaDoPerfil ficha = ativa.ficha();
    EdicaoDoPerfil dados = ficha.perfil().dados();
    LocalDate hoje = LocalDate.now(relogio.withZone(FUSO));
    int idade =
        ativa
            .resumo()
            .idadeEm(hoje)
            .orElseThrow(() -> new IllegalStateException("Conta ativa sem data de nascimento"));
    return new PerfilCompleto(
        conta,
        apelido(ficha),
        idade,
        dados.intencoes().valores(),
        dados.bio().valor(),
        fotos.urlDaAprovada(conta, Variante.NITIDA),
        pronomes(ficha),
        ficha.orientacao().filter(DadosDeOrientacao::mostrar).map(DadosDeOrientacao::paraExibir),
        ativa.resumo().verificada());
  }

  private static Preferencias preferencias(FichaDoPerfil ficha) {
    Optional<DadosDeNamoro> namoro = ficha.namoro();
    return new Preferencias(
        ficha.perfil().dados().intencoes().valores(),
        ficha.ajustes().faixa(),
        ficha.ajustes().raio(),
        ficha.ajustes().raioObrigatorio(),
        namoro.map(DadosDeNamoro::genero),
        namoro.map(DadosDeNamoro::interesses).orElse(Set.of()));
  }

  private static String apelido(FichaDoPerfil ficha) {
    return ficha.perfil().dados().apelido().valor();
  }

  private static Optional<String> pronomes(FichaDoPerfil ficha) {
    return ficha.perfil().dados().pronomes().map(PronomesEscolhidos::paraExibir);
  }

  private Optional<Ativa> ativa(ContaId conta) {
    return contas
        .porId(conta)
        .filter(resumo -> resumo.estado() == EstadoConta.ATIVA)
        .flatMap(resumo -> leitura.ficha(conta).map(ficha -> new Ativa(resumo, ficha)));
  }

  private boolean estaAtiva(ContaId conta) {
    return contas.porId(conta).map(resumo -> resumo.estado() == EstadoConta.ATIVA).orElse(false);
  }

  /** A conta ativa e o perfil dela, lidos juntos. */
  private record Ativa(ContaResumo resumo, FichaDoPerfil ficha) {}
}
