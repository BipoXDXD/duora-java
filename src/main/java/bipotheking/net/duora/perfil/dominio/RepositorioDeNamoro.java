package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.util.Optional;

/** Porta da tabela {@code namoro}: gênero e interesses, dados sensíveis. */
public interface RepositorioDeNamoro {

  Optional<DadosDeNamoro> buscar(ContaId conta);

  Optional<Instant> consentidoEm(ContaId conta);

  /**
   * Grava ou substitui os dados, com o consentimento em {@code agora}.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code INTENCAO_SEM_NAMORO} se o perfil não
   *     tem a intenção de namoro (a chave estrangeira recusa)
   */
  void substituir(ContaId conta, DadosDeNamoro dados, Instant agora);

  /** Apaga os dados; idempotente. */
  void apagar(ContaId conta);
}
