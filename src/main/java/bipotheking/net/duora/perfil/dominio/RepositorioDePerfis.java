package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.util.Optional;

/** Porta da tabela {@code perfil}. */
public interface RepositorioDePerfis {

  Optional<Perfil> buscar(ContaId conta);

  /** Grava o perfil novo; falso se a conta já tinha um (nada muda). */
  boolean criar(Perfil perfil, Instant agora);

  /**
   * Troca os dados se a versão gravada ainda é a do {@code perfil}; devolve o perfil com a versão
   * seguinte, ou vazio se outra edição chegou antes (ou se não há perfil).
   */
  Optional<Perfil> atualizar(Perfil perfil, Instant agora);

  boolean existe(ContaId conta);
}
