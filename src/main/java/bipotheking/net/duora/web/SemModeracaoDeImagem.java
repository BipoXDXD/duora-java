package bipotheking.net.duora.web;

import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sem fornecedor de moderação ({@code duora.imagens.moderacao.provedor} ausente ou {@code nenhum}):
 * toda imagem espera a revisão humana. É o padrão de dev e dos testes, e o lado seguro se a
 * propriedade faltar em produção.
 */
@Component
@ConditionalOnProperty(
    name = "duora.imagens.moderacao.provedor",
    havingValue = "nenhum",
    matchIfMissing = true)
final class SemModeracaoDeImagem implements ModeracaoDeImagem {

  @Override
  public Veredito avaliar(Path jpeg) {
    return Veredito.INDISPONIVEL;
  }
}
