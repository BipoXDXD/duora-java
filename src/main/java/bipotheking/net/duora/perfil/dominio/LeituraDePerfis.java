package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.util.Optional;

/** Porta de leitura do perfil inteiro de uma conta, numa consulta só. */
public interface LeituraDePerfis {

  /** Vazio sem perfil; sem ajustes gravados, os padrões. */
  Optional<FichaDoPerfil> ficha(ContaId conta);
}
