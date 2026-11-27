package bipotheking.net.duora.conta.dominio;

import java.util.Optional;

/**
 * Imagem JPEG ou PNG de até {@link #TAMANHO_MAXIMO} bytes, só em memória. Guarda e devolve cópias
 * do conteúdo; o {@code toString} mostra só o tipo e o tamanho. Sem {@code equals} por conteúdo:
 * duas imagens só são iguais se forem o mesmo objeto.
 */
public final class Imagem {

  public static final int TAMANHO_MAXIMO = 5_242_880;

  private static final byte[] MAGICOS_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] MAGICOS_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

  private final byte[] conteudo;
  private final TipoDeImagem tipo;

  private Imagem(byte[] conteudo, TipoDeImagem tipo) {
    this.conteudo = conteudo;
    this.tipo = tipo;
  }

  /**
   * Lê a imagem pelos bytes mágicos, sem confiar em extensão ou {@code Content-Type}.
   *
   * @return vazio se {@code bytes} estiver vazio, passar de {@link #TAMANHO_MAXIMO} ou não começar
   *     pelos mágicos de JPEG ({@code FF D8 FF}) ou PNG ({@code 89 50 4E 47 0D 0A 1A 0A})
   */
  public static Optional<Imagem> ler(byte[] bytes) {
    if (bytes.length == 0 || bytes.length > TAMANHO_MAXIMO) {
      return Optional.empty();
    }
    if (comecaCom(bytes, MAGICOS_JPEG)) {
      return Optional.of(new Imagem(bytes.clone(), TipoDeImagem.JPEG));
    }
    if (comecaCom(bytes, MAGICOS_PNG)) {
      return Optional.of(new Imagem(bytes.clone(), TipoDeImagem.PNG));
    }
    return Optional.empty();
  }

  /** Cópia do conteúdo: alterá-la não altera a imagem. */
  public byte[] bytes() {
    return conteudo.clone();
  }

  public TipoDeImagem tipo() {
    return tipo;
  }

  @Override
  public String toString() {
    return "Imagem[" + tipo + ", " + conteudo.length + " bytes]";
  }

  private static boolean comecaCom(byte[] bytes, byte[] prefixo) {
    if (bytes.length < prefixo.length) {
      return false;
    }
    for (int i = 0; i < prefixo.length; i++) {
      if (bytes[i] != prefixo[i]) {
        return false;
      }
    }
    return true;
  }
}
