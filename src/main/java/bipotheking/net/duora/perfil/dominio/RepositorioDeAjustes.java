package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.util.Optional;

/**
 * Porta da tabela {@code preferencias}. Gravar um raio diferente de {@code SEM_LIMITE} exige a
 * localização ativa, conferida na própria instrução (com a linha da localização travada).
 */
public interface RepositorioDeAjustes {

  Optional<AjustesVersionados> buscar(ContaId conta);

  /**
   * Grava os primeiros ajustes na versão 1; falso se a conta já tinha ajustes ou se o raio pede a
   * localização e ela não está ativa (nada muda).
   */
  boolean criar(ContaId conta, AjustesDeBusca ajustes, Instant agora);

  /**
   * Troca os ajustes se a versão gravada ainda é a {@code versao}; devolve os ajustes com a versão
   * seguinte, ou vazio se a versão não bate ou se o raio pede a localização e ela não está ativa.
   */
  Optional<AjustesVersionados> atualizar(
      ContaId conta, long versao, AjustesDeBusca ajustes, Instant agora);

  /** Volta o raio para {@code SEM_LIMITE} (e sobe a versão), se ele não estava lá. */
  void voltarParaSemLimite(ContaId conta, Instant agora);
}
