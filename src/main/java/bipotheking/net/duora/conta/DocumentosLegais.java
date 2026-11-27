package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.Resumos;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Termos e privacidade publicados: lê os Markdown de {@code <diretorio>/<caminho>.md} uma vez, na
 * construção, troca os marcadores {@code {{contato}}}, {@code {{encarregado}}} (e-mail) e {@code
 * {{nome-do-encarregado}}} e guarda a versão do front matter, o SHA-256 do texto já trocado e o
 * HTML. Qualquer arquivo inválido derruba o boot, porque um aceite precisa apontar para um texto
 * exato.
 */
public class DocumentosLegais {

  private static final Pattern VERSAO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final Pattern MARCADOR = Pattern.compile("\\{\\{([^}]*)}}");
  private static final List<Extension> EXTENSOES =
      List.of(YamlFrontMatterExtension.create(), TablesExtension.create());

  private final String contato;
  private final EncarregadoDeDados encarregado;
  private final Map<DocumentoLegal, Publicado> publicados = new EnumMap<>(DocumentoLegal.class);

  public DocumentosLegais(
      ResourceLoader carregador, String diretorio, String contato, EncarregadoDeDados encarregado) {
    this.contato = contato;
    this.encarregado = encarregado;
    Map<String, String> marcadores =
        Map.of(
            "contato", contato,
            "encarregado", encarregado.email(),
            "nome-do-encarregado", encarregado.nome());
    Parser parser = Parser.builder().extensions(EXTENSOES).build();
    HtmlRenderer renderer =
        HtmlRenderer.builder().extensions(EXTENSOES).escapeHtml(true).sanitizeUrls(true).build();
    String base = diretorio.endsWith("/") ? diretorio : diretorio + "/";
    for (DocumentoLegal documento : DocumentoLegal.values()) {
      String arquivo = base + documento.caminho() + ".md";
      String texto =
          trocarMarcadores(ler(carregador.getResource(arquivo), arquivo), marcadores, arquivo);
      Node arvore = parser.parse(texto);
      String versao = versao(arvore, arquivo);
      publicados.put(
          documento,
          new Publicado(
              new VersaoDeDocumento(versao, Resumos.sha256Hex(texto)), renderer.render(arvore)));
    }
  }

  public VersaoDeDocumento atual(DocumentoLegal documento) {
    return publicados.get(documento).versao();
  }

  /** HTML com o HTML bruto do Markdown escapado e os links perigosos saneados. */
  public String html(DocumentoLegal documento) {
    return publicados.get(documento).html();
  }

  /** E-mail de contato e de pedidos de privacidade. */
  public String contato() {
    return contato;
  }

  public EncarregadoDeDados encarregado() {
    return encarregado;
  }

  private static String ler(Resource recurso, String arquivo) {
    if (!recurso.exists()) {
      throw new IllegalStateException("Documento legal ausente: " + arquivo);
    }
    try (InputStream entrada = recurso.getInputStream()) {
      return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Não foi possível ler o documento legal " + arquivo, e);
    }
  }

  private static String trocarMarcadores(
      String texto, Map<String, String> marcadores, String arquivo) {
    return MARCADOR
        .matcher(texto)
        .replaceAll(encontrado -> {
          String valor = marcadores.get(encontrado.group(1));
          if (valor == null) {
            throw new IllegalStateException(
                "Marcador desconhecido " + encontrado.group() + " em " + arquivo);
          }
          return Matcher.quoteReplacement(valor);
        });
  }

  private static String versao(Node arvore, String arquivo) {
    Map<String, List<String>> frontMatter = YamlFrontMatterVisitor.readData(arvore);
    if (frontMatter.isEmpty()) {
      throw new IllegalStateException("Documento legal sem front matter: " + arquivo);
    }
    List<String> valores = frontMatter.getOrDefault("versao", List.of());
    if (valores.size() != 1 || !VERSAO.matcher(valores.getFirst()).matches()) {
      throw new IllegalStateException(
          "Documento legal sem versao AAAA-MM-DD no front matter: " + arquivo);
    }
    return valores.getFirst();
  }

  private record Publicado(VersaoDeDocumento versao, String html) {}
}
