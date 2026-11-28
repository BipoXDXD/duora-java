package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Porta de leitura do perfil inteiro de uma conta, numa consulta só. */
public interface LeituraDePerfis {

  /** Vazio sem perfil; sem ajustes gravados, os padrões. */
  Optional<FichaDoPerfil> ficha(ContaId conta);

  /** As fichas das contas pedidas que têm perfil, numa consulta só; as sem perfil ficam de fora. */
  Map<ContaId, FichaDoPerfil> fichas(Collection<ContaId> contas);
}
