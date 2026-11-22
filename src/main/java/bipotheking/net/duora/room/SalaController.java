package bipotheking.net.duora.room;

import static java.util.Objects.requireNonNullElse;

import bipotheking.net.duora.game.CatalogoDeCenarios;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.Lugar;
import bipotheking.net.duora.game.Peca;
import bipotheking.net.duora.game.Terraco;
import java.util.HexFormat;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** API da sala. Converte a entrada, chama a sala e devolve a visão de quem pediu. */
@RestController
@RequestMapping("/api")
class SalaController {

  private static final int BYTES_ETAG = 16;

  private final Salas salas;
  private final CatalogoDeCenarios catalogo;
  private final JsonMapper json;

  SalaController(Salas salas, CatalogoDeCenarios catalogo, JsonMapper json) {
    this.salas = salas;
    this.catalogo = catalogo;
    this.json = json;
  }

  /** Erros na ordem: apelido, cenário (sem cenário, o terraço), limite de salas. */
  @PostMapping("/salas")
  ResponseEntity<Visao> criar(@RequestBody Pedidos.CriarSala pedido) {
    Apelido apelido = Apelido.de(pedido.apelido());
    Cenario cenario =
        catalogo
            .porId(requireNonNullElse(pedido.cenario(), Terraco.ID))
            .orElseThrow(() -> new ErroApi(Codigo.CENARIO_INVALIDO, "Cenário desconhecido."));
    Salas.Entrada entrada = salas.criar(apelido, cenario);
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(HttpHeaders.SET_COOKIE, CookieAssento.emitir(entrada.token()))
        .body(entrada.visao());
  }

  @PostMapping("/salas/entrar")
  ResponseEntity<Visao> entrar(@RequestBody Pedidos.Entrar pedido) {
    Apelido apelido = Apelido.de(pedido.apelido());
    Salas.Entrada entrada = salas.entrar(pedido.convite(), apelido);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, CookieAssento.emitir(entrada.token()))
        .body(entrada.visao());
  }

  /** O {@code ETag} acompanha o corpo; com {@code If-None-Match} igual, o Spring responde 304. */
  @GetMapping("/sala")
  ResponseEntity<Visao> consultar(@CookieValue(name = CookieAssento.NOME) Optional<String> token) {
    Salas.Assentado eu = salas.localizar(token);
    Visao visao = eu.sala().consultar(eu.assento());
    return ResponseEntity.ok().eTag(etag(visao)).body(visao);
  }

  @PostMapping("/sala/pecas")
  Visao colocar(
      @CookieValue(name = CookieAssento.NOME) Optional<String> token,
      @RequestBody Pedidos.Colocar pedido) {
    Salas.Assentado eu = salas.localizar(token);
    Cenario cenario = eu.sala().cenario();
    Peca peca = peca(cenario, pedido.peca());
    Optional<Lugar> destino = destino(cenario, pedido.lugar());
    long versao = obrigatorio(pedido.versaoEsperada());
    return eu.sala().colocar(eu.assento(), peca, destino, versao);
  }

  @PostMapping("/sala/sinal")
  Visao sinalizar(
      @CookieValue(name = CookieAssento.NOME) Optional<String> token,
      @RequestBody Pedidos.Sinalizar pedido) {
    Salas.Assentado eu = salas.localizar(token);
    Peca peca = peca(eu.sala().cenario(), pedido.peca());
    return eu.sala().sinalizar(eu.assento(), peca);
  }

  @PostMapping("/sala/pronto")
  Visao confirmar(
      @CookieValue(name = CookieAssento.NOME) Optional<String> token,
      @RequestBody Pedidos.Pronto pedido) {
    Salas.Assentado eu = salas.localizar(token);
    long versao = obrigatorio(pedido.versao());
    boolean pronto = obrigatorio(pedido.pronto());
    return eu.sala().confirmar(eu.assento(), pronto, versao);
  }

  @PostMapping("/sala/ajustar")
  Visao ajustar(@CookieValue(name = CookieAssento.NOME) Optional<String> token) {
    Salas.Assentado eu = salas.localizar(token);
    return eu.sala().ajustar(eu.assento());
  }

  @PostMapping("/sala/sair")
  ResponseEntity<Void> sair(@CookieValue(name = CookieAssento.NOME) Optional<String> token) {
    salas.localizar(token).sala().sair();
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, CookieAssento.expirar())
        .build();
  }

  private String etag(Visao visao) {
    byte[] resumo = Segredos.sha256(json.writeValueAsBytes(visao));
    return "\"" + HexFormat.of().formatHex(resumo, 0, BYTES_ETAG) + "\"";
  }

  /** Ids resolvidos pelo cenário da sala: peça de outro cenário é peça desconhecida. */
  private static Peca peca(Cenario cenario, @Nullable String id) {
    return Optional.ofNullable(id)
        .flatMap(cenario::peca)
        .orElseThrow(() -> new ErroApi(Codigo.PECA_INVALIDA, "Peça desconhecida."));
  }

  private static Optional<Lugar> destino(Cenario cenario, @Nullable String id) {
    if (id == null) {
      return Optional.empty();
    }
    return Optional.of(
        cenario
            .lugar(id)
            .orElseThrow(() -> new ErroApi(Codigo.LUGAR_INVALIDO, "Lugar desconhecido.")));
  }

  private static <T> T obrigatorio(@Nullable T valor) {
    if (valor == null) {
      throw new ErroApi(Codigo.CORPO_INVALIDO, "Falta um campo obrigatório.");
    }
    return valor;
  }
}
