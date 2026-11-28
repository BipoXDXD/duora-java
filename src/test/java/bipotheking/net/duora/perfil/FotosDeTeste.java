package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;
import bipotheking.net.duora.perfil.dominio.FotoDePerfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Fotos de perfil prontas para outros testes, pelo mesmo envio que a API usa. */
@Component
public class FotosDeTeste {

  /** JPEG 1200 × 800 com EXIF {@code Orientation=6}: depois do processamento, 800 × 1200. */
  public static final String RETRATO = "retrato-exif-orientacao-6.jpg";

  private final FotosDoPerfil fotos;
  private final RepositorioDeFotos repositorio;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  FotosDeTeste(
      FotosDoPerfil fotos,
      RepositorioDeFotos repositorio,
      TransactionTemplate transacao,
      Clock relogio) {
    this.fotos = fotos;
    this.repositorio = repositorio;
    this.transacao = transacao;
    this.relogio = relogio;
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

  /** Envia o retrato e o aprova no lugar da aprovada que houver. */
  public FotoId enviarAprovada(ContaId conta) {
    FotoId foto = enviar(conta);
    transacao.executeWithoutResult(_ -> {
      repositorio.travarConta(conta);
      repositorio.apagarDoEstado(conta, EstadoDaFoto.APROVADA);
      if (!repositorio.decidir(foto, EstadoDaFoto.APROVADA, relogio.instant())) {
        throw new IllegalStateException("A foto enviada deixou de estar pendente");
      }
    });
    return foto;
  }
}
