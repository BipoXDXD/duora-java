package bipotheking.net.duora.perfil.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.CodigoFoto;
import bipotheking.net.duora.perfil.FotoId;
import bipotheking.net.duora.perfil.FotosDoPerfil;
import bipotheking.net.duora.web.Variante;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * As variantes das fotos de perfil. Toda recusa é o mesmo 404 {@code FOTO_INEXISTENTE}: a foto que
 * não existe, a sem permissão, o id malformado e a variante desconhecida (por isso os dois chegam
 * como texto: a conversão do Spring daria 400).
 *
 * <p>O filtro de segurança já pôs {@code Cache-Control: no-store}, que vale para a nítida. A
 * borrada troca o valor por cache privado curto, separado por sessão. Os dois cabeçalhos entram por
 * {@code setHeader}, que substitui: a resposta sai com um só {@code Cache-Control}.
 */
@RestController
class FotosController {

  static final MediaType WEBP = MediaType.parseMediaType("image/webp");
  static final String CACHE_DA_BORRADA = "private, max-age=300";

  private final FotosDoPerfil fotos;

  FotosController(FotosDoPerfil fotos) {
    this.fotos = fotos;
  }

  @GetMapping("/api/fotos/{fotoId}/{variante}")
  ResponseEntity<Resource> abrir(
      @ContaAtual ContaId conta,
      @PathVariable String fotoId,
      @PathVariable String variante,
      HttpServletResponse resposta) {
    Variante pedida = variante(variante).orElseThrow(CodigoFoto.FOTO_INEXISTENTE::erro);
    FotoId id = id(fotoId).orElseThrow(CodigoFoto.FOTO_INEXISTENTE::erro);
    Resource imagem = fotos.abrir(conta, id, pedida).orElseThrow(CodigoFoto.FOTO_INEXISTENTE::erro);
    if (pedida == Variante.BORRADA) {
      resposta.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_DA_BORRADA);
      resposta.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
    }
    return ResponseEntity.ok().contentType(WEBP).body(imagem);
  }

  private static Optional<FotoId> id(String texto) {
    try {
      return Optional.of(new FotoId(UUID.fromString(texto)));
    } catch (IllegalArgumentException malformado) {
      return Optional.empty();
    }
  }

  private static Optional<Variante> variante(String sufixo) {
    return Arrays.stream(Variante.values())
        .filter(candidata -> candidata.sufixo().equals(sufixo))
        .findFirst();
  }
}
