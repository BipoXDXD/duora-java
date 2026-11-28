package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.ConferenciaDeDocumento;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Dublê programável da conferência de documento. Não é {@code @Component}: só os testes que
 * importam {@link DublesDeVerificacao} o recebem. Por padrão aceita o documento com a data de
 * nascimento padrão do {@link ContasDeTeste} (1990-05-10) e rosto que confere.
 */
public class ConferenciaDeDocumentoDeTeste implements ConferenciaDeDocumento {

  /** A data padrão lida no documento: a mesma do {@link ContasDeTeste#criarAtiva()}. */
  public static final LocalDate NASCIMENTO_PADRAO = LocalDate.of(1990, 5, 10);

  private final AtomicReference<ResultadoDoDocumento> resultado = new AtomicReference<>(aceito());
  private final AtomicBoolean falha = new AtomicBoolean();
  private final AtomicInteger chamadas = new AtomicInteger();
  private final AtomicReference<@Nullable Chamada> ultimaChamada = new AtomicReference<>();
  private final List<Boolean> transacaoAtiva = new CopyOnWriteArrayList<>();
  private final RetencaoDeTeste retencao = new RetencaoDeTeste();

  /** As três imagens que uma conferência recebeu. */
  public record Chamada(Imagem frente, Imagem verso, Imagem selfie) {}

  private static ResultadoDoDocumento aceito() {
    return new ResultadoDoDocumento(true, Optional.of(NASCIMENTO_PADRAO), true);
  }

  @Override
  public ResultadoDoDocumento conferir(Imagem frente, Imagem verso, Imagem selfie) {
    transacaoAtiva.add(TransactionSynchronizationManager.isActualTransactionActive());
    chamadas.incrementAndGet();
    ultimaChamada.set(new Chamada(frente, verso, selfie));
    retencao.reter();
    if (falha.get()) {
      throw new FornecedorIndisponivel("conferência de documento fora do ar (dublê)", null);
    }
    return resultado.get();
  }

  /** O resultado das conferências seguintes. */
  public void programar(ResultadoDoDocumento novo) {
    resultado.set(novo);
  }

  /** As conferências seguintes lançam {@link FornecedorIndisponivel}. */
  public void falhar() {
    falha.set(true);
  }

  /** Quantas conferências chegaram desde o último {@link #zerar()}. */
  public int chamadas() {
    return chamadas.get();
  }

  /** As imagens da última conferência; vazio se nenhuma chegou. */
  public Optional<Chamada> ultimaChamada() {
    return Optional.ofNullable(ultimaChamada.get());
  }

  /** Para cada conferência, se havia transação de banco ativa. */
  public List<Boolean> transacaoAtivaNasChamadas() {
    return List.copyOf(transacaoAtiva);
  }

  /** Faz as conferências seguintes pararem dentro do dublê até {@link #liberar()}. */
  public void segurar() {
    retencao.segurar();
  }

  /** Espera uma conferência chegar ao dublê depois de {@link #segurar()}. */
  public boolean aguardarChamada(Duration limite) throws InterruptedException {
    return retencao.aguardarChamada(limite);
  }

  /** Solta as conferências seguradas por {@link #segurar()}. */
  public void liberar() {
    retencao.liberar();
  }

  /** Volta ao padrão: documento aceito, sem falha, sem retenção e sem chamadas registradas. */
  public void zerar() {
    retencao.zerar();
    resultado.set(aceito());
    falha.set(false);
    chamadas.set(0);
    ultimaChamada.set(null);
    transacaoAtiva.clear();
  }
}
