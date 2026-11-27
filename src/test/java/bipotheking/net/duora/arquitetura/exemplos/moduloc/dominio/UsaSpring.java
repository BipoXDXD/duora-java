package bipotheking.net.duora.arquitetura.exemplos.moduloc.dominio;

import org.springframework.util.StringUtils;

/**
 * Viola o domínio em Java puro: usa uma classe do Spring. Não usa {@code @Component} de propósito:
 * o pacote fica sob o component scan da aplicação nos testes Spring.
 */
class UsaSpring {

  boolean temTexto(String valor) {
    return StringUtils.hasText(valor);
  }
}
