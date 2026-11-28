package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.CredencialCurta;
import bipotheking.net.duora.conta.dominio.CredencialDoNavegador;
import bipotheking.net.duora.conta.dominio.FaixaEstimada;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ModoDeCaptura;
import bipotheking.net.duora.conta.dominio.ProvaDeVida;
import bipotheking.net.duora.conta.dominio.ResultadoDaProva;
import bipotheking.net.duora.conta.dominio.SessaoDeProva;
import bipotheking.net.duora.conta.dominio.SituacaoDaProva;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Dublê programável da prova de vida e da credencial do navegador, sem marcador de e-mail. Não é
 * {@code @Component}: só os testes que importam {@link DublesDeVerificacao} o recebem, e o e2e
 * segue com os falsos da V4. Por padrão, toda sessão conclui com confiança 99,0, faixa 30–40 e uma
 * referência JPEG de mentira; a credencial vence 15 min depois do relógio e carrega {@link
 * #SEGREDO} no segredo e no token, para os testes de log procurarem.
 */
public class ProvaDeVidaDeTeste implements ProvaDeVida, CredencialDoNavegador {

  /** Segredo da credencial: nunca pode aparecer no log. */
  public static final String SEGREDO = "SEGREDO-CANARIO-NAO-LOGAR";

  /** Conteúdo da referência depois dos mágicos de JPEG: nunca pode aparecer no log. */
  public static final String CONTEUDO_DA_REFERENCIA = "selfie-de-teste";

  private static final String REGIAO = "us-east-1";
  private static final Duration VALIDADE_DA_CREDENCIAL = Duration.ofMinutes(15);

  /** Teto da retenção: um teste que esquece de liberar não trava a suíte. */
  private static final Duration RETENCAO_MAXIMA = Duration.ofSeconds(10);

  private final Clock relogio;
  private final AtomicReference<ResultadoDaProva> padrao = new AtomicReference<>(concluida());
  private final Map<String, ResultadoDaProva> porSessao = new ConcurrentHashMap<>();
  private final AtomicBoolean falhaNaCriacao = new AtomicBoolean();
  private final AtomicBoolean falhaNoResultado = new AtomicBoolean();
  private final AtomicInteger chamadasDeResultado = new AtomicInteger();
  private final List<Boolean> transacaoAtiva = new CopyOnWriteArrayList<>();
  private final AtomicReference<@Nullable Retencao> retencao = new AtomicReference<>();

  public ProvaDeVidaDeTeste(Clock relogio) {
    this.relogio = relogio;
  }

  /** O resultado padrão: concluída, 99,0, faixa 30–40 e a referência de mentira. */
  public static ResultadoDaProva concluida() {
    return concluida(99.0, new FaixaEstimada(30, 40));
  }

  /** Uma prova concluída com a confiança e a faixa dadas, com a referência de mentira. */
  public static ResultadoDaProva concluida(double confianca, FaixaEstimada faixa) {
    return new ResultadoDaProva(
        SituacaoDaProva.CONCLUIDA, confianca, Optional.of(faixa), Optional.of(referencia()));
  }

  /** {@code FF D8 FF E0} seguido de {@link #CONTEUDO_DA_REFERENCIA}. */
  public static Imagem referencia() {
    byte[] conteudo = CONTEUDO_DA_REFERENCIA.getBytes(StandardCharsets.UTF_8);
    byte[] bytes = new byte[4 + conteudo.length];
    bytes[0] = (byte) 0xFF;
    bytes[1] = (byte) 0xD8;
    bytes[2] = (byte) 0xFF;
    bytes[3] = (byte) 0xE0;
    System.arraycopy(conteudo, 0, bytes, 4, conteudo.length);
    return Imagem.ler(bytes).orElseThrow();
  }

  @Override
  public ModoDeCaptura modo() {
    return ModoDeCaptura.FALSO;
  }

  @Override
  public SessaoDeProva criarSessao(UUID tentativa) {
    registrarTransacao();
    if (falhaNaCriacao.get()) {
      throw new FornecedorIndisponivel("prova de vida fora do ar (dublê)", null);
    }
    return new SessaoDeProva("teste-" + tentativa, REGIAO);
  }

  @Override
  public CredencialCurta emitir(UUID tentativa) {
    registrarTransacao();
    return new CredencialCurta(
        "ASIATESTE", SEGREDO, SEGREDO, relogio.instant().plus(VALIDADE_DA_CREDENCIAL));
  }

  @Override
  public ResultadoDaProva resultado(String sessaoId) {
    registrarTransacao();
    chamadasDeResultado.incrementAndGet();
    reter();
    if (falhaNoResultado.get()) {
      throw new FornecedorIndisponivel("prova de vida fora do ar (dublê)", null);
    }
    return porSessao.getOrDefault(sessaoId, padrao.get());
  }

  /** O resultado de toda sessão sem programação própria. */
  public void programar(ResultadoDaProva resultado) {
    padrao.set(resultado);
  }

  /** O resultado só desta sessão. */
  public void programarPorSessao(String sessaoId, ResultadoDaProva resultado) {
    porSessao.put(sessaoId, resultado);
  }

  /** As criações de sessão seguintes lançam {@link FornecedorIndisponivel}. */
  public void falharNaCriacao() {
    falhaNaCriacao.set(true);
  }

  /** As leituras de resultado seguintes lançam {@link FornecedorIndisponivel}. */
  public void falharNoResultado() {
    falhaNoResultado.set(true);
  }

  /** Quantas leituras de resultado chegaram desde o último {@link #zerar()}. */
  public int chamadasDeResultado() {
    return chamadasDeResultado.get();
  }

  /** Para cada chamada (criação, credencial e resultado), se havia transação de banco ativa. */
  public List<Boolean> transacaoAtivaNasChamadas() {
    return List.copyOf(transacaoAtiva);
  }

  /**
   * Faz as leituras de resultado seguintes pararem dentro do dublê até {@link #liberar()}: garante
   * que duas requisições se sobrepõem sem depender de tempo.
   */
  public void segurar() {
    retencao.set(new Retencao(new CountDownLatch(1), new CountDownLatch(1)));
  }

  /** Espera uma leitura de resultado chegar ao dublê depois de {@link #segurar()}. */
  public boolean aguardarChamada(Duration limite) throws InterruptedException {
    Retencao atual = retencao.get();
    return atual != null && atual.chegou().await(limite.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** Solta as leituras seguradas por {@link #segurar()}. */
  public void liberar() {
    Retencao atual = retencao.get();
    if (atual != null) {
      atual.liberacao().countDown();
    }
  }

  /** Volta ao padrão: sem programação, sem falhas, sem retenção e sem chamadas registradas. */
  public void zerar() {
    liberar();
    retencao.set(null);
    padrao.set(concluida());
    porSessao.clear();
    falhaNaCriacao.set(false);
    falhaNoResultado.set(false);
    chamadasDeResultado.set(0);
    transacaoAtiva.clear();
  }

  private void registrarTransacao() {
    transacaoAtiva.add(TransactionSynchronizationManager.isActualTransactionActive());
  }

  private void reter() {
    Retencao atual = retencao.get();
    if (atual == null) {
      return;
    }
    atual.chegou().countDown();
    try {
      atual.liberacao().await(RETENCAO_MAXIMA.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Avisa que uma chamada chegou e a segura até a liberação. */
  private record Retencao(CountDownLatch chegou, CountDownLatch liberacao) {}
}
