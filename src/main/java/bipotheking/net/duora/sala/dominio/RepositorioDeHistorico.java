package bipotheking.net.duora.sala.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.util.Set;

/** Os cenários que cada conta já jogou (tabela {@code cenario_jogado}, V52). */
public interface RepositorioDeHistorico {

  /** Grava a primeira vez; se a conta já jogou o cenário, fica o que já estava. */
  void registrar(ContaId conta, String cenarioId, Instant agora);

  Set<String> jogados(ContaId conta);
}
