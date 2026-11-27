package bipotheking.net.duora.retencao;

import bipotheking.net.duora.nucleo.Correlacao;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Política em memória: guarda os limites e o correlation ID que viu, devolve o que foi configurado
 * e detecta duas execuções ao mesmo tempo.
 */
final class PoliticaFalsa implements PoliticaDeRetencao {

  private final String nome;
  private final Duration prazo;
  private final List<Instant> limites = new ArrayList<>();
  private final List<@Nullable String> correlationIds = new ArrayList<>();
  private final AtomicInteger dentro = new AtomicInteger();
  private final AtomicBoolean viuConcorrencia = new AtomicBoolean();
  private int removidos;
  private long tamanho;
  private @Nullable RuntimeException erro;
  private @Nullable CountDownLatch entrou;
  private @Nullable CountDownLatch liberar;
  private @Nullable List<String> ordem;

  PoliticaFalsa(String nome, Duration prazo) {
    this.nome = nome;
    this.prazo = prazo;
  }

  PoliticaFalsa(String nome) {
    this(nome, Duration.ofDays(30));
  }

  PoliticaFalsa removendo(int quantos) {
    this.removidos = quantos;
    return this;
  }

  PoliticaFalsa comTamanho(long quantos) {
    this.tamanho = quantos;
    return this;
  }

  PoliticaFalsa falhandoCom(RuntimeException erro) {
    this.erro = erro;
    return this;
  }

  /** Anota o próprio nome em {@code ordem} a cada execução. */
  PoliticaFalsa anotandoEm(List<String> ordem) {
    this.ordem = ordem;
    return this;
  }

  /** A primeira chamada avisa em {@code entrou} e espera {@code liberar} antes de devolver. */
  PoliticaFalsa pausando(CountDownLatch entrou, CountDownLatch liberar) {
    this.entrou = entrou;
    this.liberar = liberar;
    return this;
  }

  @Override
  public String nome() {
    return nome;
  }

  @Override
  public Duration prazo() {
    return prazo;
  }

  @Override
  public int expirar(Instant limite) {
    if (dentro.incrementAndGet() > 1) {
      viuConcorrencia.set(true);
    }
    try {
      synchronized (this) {
        limites.add(limite);
        correlationIds.add(MDC.get(Correlacao.CHAVE_MDC));
      }
      if (ordem != null) {
        ordem.add(nome);
      }
      pausarSePedido();
      if (erro != null) {
        throw erro;
      }
      return removidos;
    } finally {
      dentro.decrementAndGet();
    }
  }

  private void pausarSePedido() {
    CountDownLatch aviso = entrou;
    CountDownLatch espera = liberar;
    if (aviso == null || espera == null || aviso.getCount() == 0) {
      return;
    }
    aviso.countDown();
    try {
      if (!espera.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("O teste não liberou a política");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Override
  public long tamanho() {
    return tamanho;
  }

  synchronized List<Instant> limites() {
    return List.copyOf(limites);
  }

  synchronized List<@Nullable String> correlationIds() {
    return new ArrayList<>(correlationIds);
  }

  boolean viuConcorrencia() {
    return viuConcorrencia.get();
  }
}
