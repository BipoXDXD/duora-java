package bipotheking.net.duora.conta.infra;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Em produção, derruba o boot se a prova de vida ou a conferência de documento falsa foi
 * registrada: a falsa aprova quem o e-mail mandar, e o perfil {@code verificacao-falsa} não pode
 * chegar ao ar. Olha os beans, não o valor das propriedades: a condição que os liga ignora
 * maiúsculas ({@code FALSO} também liga), e repetir a regra aqui deixaria passar uma grafia.
 */
@Component
@Profile("prod")
class GuardaDaVerificacaoReal {

  GuardaDaVerificacaoReal(ListableBeanFactory beans) {
    if (registrado(beans, ProvaDeVidaFalsa.class)
        || registrado(beans, ConferenciaDeDocumentoFalsa.class)) {
      throw new IllegalStateException("A verificação falsa não roda em produção");
    }
  }

  private static boolean registrado(ListableBeanFactory beans, Class<?> tipo) {
    return beans.getBeanNamesForType(tipo, true, false).length > 0;
  }
}
