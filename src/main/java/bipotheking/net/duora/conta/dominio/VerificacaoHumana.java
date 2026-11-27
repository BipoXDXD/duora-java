package bipotheking.net.duora.conta.dominio;

/**
 * Porta da verificação humana: confirma que o token do widget veio de uma pessoa. Falha fechada: na
 * dúvida, inclusive com o serviço fora do ar, devolve {@code false}.
 */
public interface VerificacaoHumana {

  boolean verificar(String token, String ip);
}
