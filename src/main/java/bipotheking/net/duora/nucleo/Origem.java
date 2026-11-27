package bipotheking.net.duora.nucleo;

/**
 * De onde veio uma requisição: IP e porta de origem, sem depender de servlet. O IP é dado pessoal:
 * o {@code toString} não o mostra.
 */
public record Origem(String ip, int porta) {

  @Override
  public String toString() {
    return "Origem[ip=***, porta=" + porta + "]";
  }
}
