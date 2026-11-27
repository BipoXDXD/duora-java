package bipotheking.net.duora.conta.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Em produção, derruba o boot se a prova de vida ou a conferência de documento for a falsa: a falsa
 * aprova quem o e-mail mandar, e o perfil {@code verificacao-falsa} não pode chegar ao ar.
 */
@Component
@Profile("prod")
class GuardaDaVerificacaoReal {

  private static final String FALSO = "falso";

  GuardaDaVerificacaoReal(
      @Value("${duora.verificacao.provedor}") String provedor,
      @Value("${duora.verificacao.documento.provedor}") String provedorDoDocumento) {
    if (FALSO.equals(provedor) || FALSO.equals(provedorDoDocumento)) {
      throw new IllegalStateException("A verificação falsa não roda em produção");
    }
  }
}
