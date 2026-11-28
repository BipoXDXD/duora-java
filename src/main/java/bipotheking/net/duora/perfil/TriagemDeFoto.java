package bipotheking.net.duora.perfil;

import bipotheking.net.duora.web.ImagemProcessada;
import bipotheking.net.duora.web.ModeracaoDeImagem;
import bipotheking.net.duora.web.ModeracaoDeImagem.Veredito;
import bipotheking.net.duora.web.ProcessadorDeImagem;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Processa a foto enviada e passa a nítida pela moderação automática, fora de transação (os dois
 * podem levar segundos). Só {@link Veredito#LIBERADA} libera a foto; uma moderação que lança vale
 * como {@link Veredito#INDISPONIVEL}. Conta cada veredito em {@code duora.imagens.moderacao}, com a
 * tag {@code resultado}.
 */
@Component
class TriagemDeFoto {

  private static final Logger LOG = LoggerFactory.getLogger(TriagemDeFoto.class);

  private final ProcessadorDeImagem processador;
  private final ModeracaoDeImagem moderacao;
  private final MeterProvider<Counter> vereditos;

  TriagemDeFoto(
      ProcessadorDeImagem processador, ModeracaoDeImagem moderacao, MeterRegistry metricas) {
    this.processador = processador;
    this.moderacao = moderacao;
    this.vereditos =
        Counter.builder("duora.imagens.moderacao")
            .description("Vereditos da moderação automática das fotos enviadas")
            .withRegistry(metricas);
  }

  /**
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio as recusas do {@link ProcessadorDeImagem}
   */
  FotoTriada triar(byte[] arquivo) {
    ImagemProcessada imagem = processador.processar(new ByteArrayInputStream(arquivo));
    try {
      return new FotoTriada(imagem, moderar(imagem) == Veredito.LIBERADA);
    } catch (RuntimeException erro) {
      imagem.close();
      throw erro;
    }
  }

  private Veredito moderar(ImagemProcessada imagem) {
    Veredito veredito;
    try {
      veredito = moderacao.avaliar(imagem.paraModeracao());
    } catch (RuntimeException erro) {
      LOG.warn(
          "A moderação de uma foto falhou ({}): fica para a revisão humana",
          erro.getClass().getSimpleName());
      veredito = Veredito.INDISPONIVEL;
    }
    vereditos.withTags("resultado", veredito.name().toLowerCase(Locale.ROOT)).increment();
    return veredito;
  }

  /**
   * A foto processada e o veredito da moderação. Fechar apaga os temporários da imagem.
   *
   * @param liberada se a moderação automática a liberou (só então ela entra aprovada)
   */
  record FotoTriada(ImagemProcessada imagem, boolean liberada) implements AutoCloseable {

    @Override
    public void close() {
      imagem.close();
    }
  }
}
