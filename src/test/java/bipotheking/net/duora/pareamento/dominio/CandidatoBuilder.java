package bipotheking.net.duora.pareamento.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.Candidato;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.FaixaEtaria;
import bipotheking.net.duora.perfil.Genero;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.Preferencias;
import bipotheking.net.duora.perfil.Raio;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Monta {@link Candidato} para os testes do pareamento. O padrão é uma conta real de 28 anos, só
 * amizade, qualquer idade, sem limite de distância, sem célula e na fila desde {@link #T0}.
 */
public final class CandidatoBuilder {

  public static final Instant T0 = Instant.parse("2026-11-28T20:00:00Z");

  private final ContaId conta;
  private Instant entrouEm = T0;
  private int idade = 28;
  private Set<Intencao> intencoes = EnumSet.of(Intencao.AMIZADE);
  private FaixaEtaria faixaEtaria = FaixaEtaria.TODAS;
  private Raio raio = Raio.SEM_LIMITE;
  private boolean raioObrigatorio;
  private @Nullable Genero generoProprio;
  private Set<Genero> generosBuscados = EnumSet.noneOf(Genero.class);
  private @Nullable CelulaGeohash celula;
  private boolean deTeste;

  private CandidatoBuilder(ContaId conta) {
    this.conta = conta;
  }

  /** A conta {@code 00000000-0000-0000-0000-<n>}: números menores ordenam antes. */
  public static CandidatoBuilder candidato(long n) {
    return new CandidatoBuilder(conta(n));
  }

  public static ContaId conta(long n) {
    return new ContaId(new UUID(0L, n));
  }

  public CandidatoBuilder entrouEm(Instant valor) {
    entrouEm = valor;
    return this;
  }

  public CandidatoBuilder entrouSegundosDepois(long segundos) {
    return entrouEm(T0.plusSeconds(segundos));
  }

  public CandidatoBuilder idade(int valor) {
    idade = valor;
    return this;
  }

  public CandidatoBuilder intencoes(Intencao primeira, Intencao... outras) {
    intencoes = EnumSet.of(primeira, outras);
    return this;
  }

  public CandidatoBuilder faixa(int minima, int maxima) {
    faixaEtaria = new FaixaEtaria(minima, maxima);
    return this;
  }

  public CandidatoBuilder raio(Raio valor, boolean obrigatorio) {
    raio = valor;
    raioObrigatorio = obrigatorio;
    return this;
  }

  public CandidatoBuilder genero(Genero valor) {
    generoProprio = valor;
    return this;
  }

  public CandidatoBuilder buscando(Genero primeiro, Genero... outros) {
    generosBuscados = EnumSet.of(primeiro, outros);
    return this;
  }

  public CandidatoBuilder celula(String valor) {
    celula = new CelulaGeohash(valor);
    return this;
  }

  public CandidatoBuilder deTeste() {
    deTeste = true;
    return this;
  }

  public Candidato build() {
    Preferencias preferencias =
        new Preferencias(
            intencoes,
            faixaEtaria,
            raio,
            raioObrigatorio,
            Optional.ofNullable(generoProprio),
            generosBuscados);
    return new Candidato(
        conta, entrouEm, idade, preferencias, Optional.ofNullable(celula), deTeste);
  }
}
