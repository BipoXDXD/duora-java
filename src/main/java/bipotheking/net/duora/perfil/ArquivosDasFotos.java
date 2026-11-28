package bipotheking.net.duora.perfil;

import bipotheking.net.duora.web.ArmazemDeImagens;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Os arquivos das fotos de perfil no {@link ArmazemDeImagens}. Apagar nunca falha para quem chama:
 * o arquivo que resiste vira um WARN com o id da foto, e a retenção de fotos órfãs o varre depois.
 */
final class ArquivosDasFotos {

  private static final Logger LOG = LoggerFactory.getLogger(ArquivosDasFotos.class);

  private final ArmazemDeImagens armazem;

  ArquivosDasFotos(ArmazemDeImagens armazem) {
    this.armazem = armazem;
  }

  /** Apaga já as duas variantes de cada foto. */
  void apagar(List<FotoId> ids) {
    for (FotoId id : ids) {
      try {
        armazem.apagar(FotosDoPerfil.CATEGORIA, id.valor());
      } catch (RuntimeException erro) {
        LOG.warn("Não foi possível apagar os arquivos da foto {}", id.valor(), erro);
      }
    }
  }

  /**
   * Apaga as variantes depois do commit da transação aberta; desfeita a transação, os arquivos
   * ficam, como as linhas. Exige transação aberta.
   */
  void apagarDepoisDoCommit(List<FotoId> ids) {
    if (ids.isEmpty()) {
      return;
    }
    List<FotoId> copia = List.copyOf(ids);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            apagar(copia);
          }
        });
  }
}
