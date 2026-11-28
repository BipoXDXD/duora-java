package bipotheking.net.duora.push;

import bipotheking.net.duora.conta.ContaId;
import java.net.URI;
import java.util.UUID;

/**
 * Uma inscrição de Web Push de um navegador.
 *
 * @param endpoint a URL do serviço de push, validada por {@link EnderecoDePush}
 * @param p256dh a chave pública ECDH do navegador, em base64url
 * @param auth o segredo de autenticação do navegador, em base64url
 */
public record InscricaoDePush(UUID id, ContaId conta, URI endpoint, String p256dh, String auth) {

  /** Só o id: endpoint e chaves identificam o navegador e não vão para log. */
  @Override
  public String toString() {
    return "InscricaoDePush[id=" + id + "]";
  }
}
