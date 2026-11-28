package bipotheking.net.duora.perfil.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.FotoEnviada;
import bipotheking.net.duora.perfil.FotosDoPerfil;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Envio e remoção da foto de perfil da conta da sessão. O envio é {@code multipart/form-data} com a
 * parte {@code arquivo}. O multipart só é lido quando os parâmetros são resolvidos, depois de a
 * sessão ser conferida. Sem {@code consumes}: outro {@code Content-Type} daria 415 antes do 401 da
 * sessão.
 */
@RestController
@RequestMapping("/api/perfil/foto")
class FotoController {

  private static final String CHAVE_DE_IDEMPOTENCIA = "Idempotency-Key";

  private final FotosDoPerfil fotos;

  FotoController(FotosDoPerfil fotos) {
    this.fotos = fotos;
  }

  /** A foto entra pendente: 202, com a URL da nítida. */
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  FotoEnviada enviar(
      @ContaAtual ContaId conta,
      @RequestPart MultipartFile arquivo,
      @RequestHeader(name = CHAVE_DE_IDEMPOTENCIA, required = false) @Nullable String chave)
      throws IOException {
    return fotos.enviar(conta, arquivo.getBytes(), chave);
  }

  /** Apaga aprovada, pendente e recusa; 204 também sem foto. */
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remover(@ContaAtual ContaId conta) {
    fotos.removerTodas(conta);
  }
}
