package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;
import bipotheking.net.duora.perfil.dominio.FotoDePerfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Fotos de perfil prontas para outros testes, pelo mesmo envio que a API usa. */
@Component
public class FotosDeTeste {

  /** JPEG 1200 × 800 com EXIF {@code Orientation=6}: depois do processamento, 800 × 1200. */
  public static final String RETRATO = "retrato-exif-orientacao-6.jpg";

  private final FotosDoPerfil fotos;
  private final FilaDeFotos fila;
  private final RepositorioDeFotos repositorio;

  FotosDeTeste(FotosDoPerfil fotos, FilaDeFotos fila, RepositorioDeFotos repositorio) {
    this.fotos = fotos;
    this.fila = fila;
    this.repositorio = repositorio;
  }

  /** Os bytes de uma fixture de {@code src/test/resources/imagens}. */
  public static byte[] fixture(String nome) {
    try (InputStream entrada = FotosDeTeste.class.getResourceAsStream("/imagens/" + nome)) {
      return Objects.requireNonNull(entrada, nome).readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Envia o retrato como foto pendente da conta. */
  public FotoId enviar(ContaId conta) {
    fotos.enviar(conta, fixture(RETRATO), null);
    return repositorio.daConta(conta).stream()
        .filter(foto -> foto.estado() == EstadoDaFoto.PENDENTE)
        .map(FotoDePerfil::id)
        .findFirst()
        .orElseThrow();
  }

  /** Envia o retrato e o aprova pela fila, no lugar da aprovada que houver. */
  public FotoId enviarAprovada(ContaId conta) {
    FotoId foto = enviar(conta);
    fila.aprovar(foto);
    return foto;
  }
}
