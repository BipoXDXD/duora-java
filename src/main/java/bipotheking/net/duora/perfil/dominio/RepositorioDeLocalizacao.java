package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.CelulaGeohash;
import java.time.Instant;
import java.util.Optional;

/** Porta da tabela {@code localizacao}: uma célula por conta, nunca a coordenada. */
public interface RepositorioDeLocalizacao {

  /**
   * Grava a célula e o consentimento; se a conta já consentiu, troca só a célula e o {@code
   * atualizada_em} e mantém o {@code consentida_em} original.
   */
  void consentirEGravar(ContaId conta, CelulaGeohash celula, Instant agora);

  /** Troca a célula de quem já consentiu; falso se não há consentimento (nada muda). */
  boolean atualizar(ContaId conta, CelulaGeohash celula, Instant agora);

  Optional<CelulaGeohash> celula(ContaId conta);

  Optional<Instant> atualizadaEm(ContaId conta);

  /** Apaga a célula e o consentimento; idempotente. */
  void apagar(ContaId conta);
}
