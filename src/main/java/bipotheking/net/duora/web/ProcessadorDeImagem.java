package bipotheking.net.duora.web;

import java.io.InputStream;

/**
 * Converte uma imagem enviada nas variantes nítida e borrada. Recusa com {@link
 * bipotheking.net.duora.nucleo.ErroDeNegocio}: 413 {@link CodigoWeb#IMAGEM_GRANDE_DEMAIS}, 415
 * {@link CodigoWeb#FORMATO_NAO_SUPORTADO}, 422 {@link CodigoWeb#IMAGEM_INVALIDA} e 503 {@link
 * CodigoWeb#PROCESSAMENTO_INDISPONIVEL}.
 *
 * <p>Pode demorar segundos: chame fora de transação.
 */
public interface ProcessadorDeImagem {

  ImagemProcessada processar(InputStream arquivo);
}
