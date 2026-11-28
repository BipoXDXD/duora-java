package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;

/** Porta da coluna {@code perfil.pausada_em}. Nenhum método mexe na versão do perfil. */
public interface RepositorioDePausa {

  /** Pausa em {@code agora}, ou mantém o instante da primeira pausa; falso se não há perfil. */
  boolean pausar(ContaId conta, Instant agora);

  /** Tira a pausa, se havia; falso se não há perfil. */
  boolean retomar(ContaId conta);

  /** Falso também sem perfil. */
  boolean pausada(ContaId conta);
}
