package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.EstadoConta;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta das sessões no banco. Toda sessão é achada pelo resumo (SHA-256) do token, nunca por ele.
 */
public interface RepositorioDeSessoes {

  /** Sessão gravada e o estado atual da conta dona dela. */
  record SessaoGravada(
      UUID id, ContaId conta, EstadoConta estado, Instant expiraEm, Instant renovadaEm) {}

  /** Grava a sessão aberta em {@code criadaEm}, que também conta como a última renovação. */
  void gravar(UUID id, ContaId conta, byte[] resumo, Instant criadaEm, Instant expiraEm);

  Optional<SessaoGravada> porResumo(byte[] resumo);

  /**
   * Compare-and-set: grava {@code renovadaEm = agora} e o novo vencimento só se a última renovação
   * for anterior a {@code renovadaAntesDe}. Entre renovações simultâneas, só uma devolve {@code
   * true}.
   */
  boolean renovar(UUID id, Instant agora, Instant expiraEm, Instant renovadaAntesDe);

  void apagar(byte[] resumo);

  void apagarDaConta(ContaId conta);
}
