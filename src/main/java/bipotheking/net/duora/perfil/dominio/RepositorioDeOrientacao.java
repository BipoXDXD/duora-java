package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.util.Optional;

/** Porta da tabela {@code orientacao}: dado sensível. */
public interface RepositorioDeOrientacao {

  Optional<DadosDeOrientacao> buscar(ContaId conta);

  /**
   * Grava ou substitui os dados.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code PERFIL_NECESSARIO} se o perfil não
   *     existe (a chave estrangeira recusa)
   */
  void substituir(ContaId conta, DadosDeOrientacao dados);

  /** Apaga os dados; idempotente. */
  void apagar(ContaId conta);
}
