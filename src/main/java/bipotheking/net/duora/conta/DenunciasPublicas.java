package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.DenunciaPublica;
import bipotheking.net.duora.conta.dominio.RepositorioDeDenunciasPublicas;
import bipotheking.net.duora.conta.dominio.VerificacaoHumana;
import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.notificacao.Outbox;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.web.LimiteDeTaxa;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Canal público de denúncia, sem conta. O Turnstile roda fora da transação; a denúncia e o aviso ao
 * contato do Duora entram juntos, numa transação. O aviso leva só o motivo e o link do painel: a
 * descrição, a referência e o e-mail de contato ficam no banco, longe do e-mail e do log.
 */
@Service
public class DenunciasPublicas {

  private static final String LIMITE_POR_IP = "denuncia-publica-ip";
  private static final String TITULAR = "denuncia:";
  private static final String ASSUNTO = "Duora: denúncia pública recebida";
  private static final String CAMINHO_DO_PAINEL = "/admin";
  private static final RespostaDeDenuncia RESPOSTA =
      new RespostaDeDenuncia(
          "Recebemos sua denúncia. Se você deixou um e-mail, respondemos por lá.");

  private static final Logger LOG = LoggerFactory.getLogger(DenunciasPublicas.class);

  private final LimiteDeTaxa limiteDeTaxa;
  private final VerificacaoHumana verificacaoHumana;
  private final RepositorioDeDenunciasPublicas denuncias;
  private final Outbox outbox;
  private final SitePublico site;
  private final String contato;
  private final TransactionTemplate transacao;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;

  DenunciasPublicas(
      LimiteDeTaxa limiteDeTaxa,
      VerificacaoHumana verificacaoHumana,
      RepositorioDeDenunciasPublicas denuncias,
      Outbox outbox,
      SitePublico site,
      @Value("${duora.contato.email}") String contato,
      TransactionTemplate transacao,
      Clock relogio,
      GeradorDeIds geradorDeIds) {
    this.limiteDeTaxa = limiteDeTaxa;
    this.verificacaoHumana = verificacaoHumana;
    this.denuncias = denuncias;
    this.outbox = outbox;
    this.site = site;
    this.contato = contato;
    this.transacao = transacao;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
  }

  /** O que o front mostra depois da denúncia, igual para toda denúncia aceita. */
  public record RespostaDeDenuncia(String mensagem) {}

  /**
   * Registra a denúncia e avisa o contato do Duora pela outbox.
   *
   * @throws ErroDeNegocio {@code LIMITE_DE_TAXA}, {@code DENUNCIA_PUBLICA_INVALIDA} ou {@code
   *     VERIFICACAO_HUMANA_FALHOU}
   */
  public RespostaDeDenuncia registrar(
      @Nullable String motivo,
      @Nullable String descricao,
      @Nullable String referencia,
      @Nullable String emailContato,
      @Nullable String tokenVerificacao,
      String ip) {
    limiteDeTaxa.consumir(LIMITE_POR_IP, ip);
    DenunciaPublica denuncia = DenunciaPublica.de(motivo, descricao, referencia, emailContato);
    if (!verificacaoHumana.verificar(tokenVerificacao, ip)) {
      throw new ErroDeNegocio(
          CodigoVerificacaoHumana.VERIFICACAO_HUMANA_FALHOU,
          "Não foi possível confirmar que você é uma pessoa. Tente de novo.");
    }
    UUID id = geradorDeIds.novo();
    transacao.executeWithoutResult(status -> {
      denuncias.gravar(id, denuncia, relogio.instant());
      outbox.registrar(Outbox.EMAIL, TITULAR + id, aviso(denuncia));
    });
    LOG.info("Denúncia pública {} registrada: {}", id, denuncia);
    return RESPOSTA;
  }

  /** O motivo é um nome de enum e o endereço do site não tem consulta: o HTML dispensa escape. */
  private MensagemDeEmail aviso(DenunciaPublica denuncia) {
    String motivo = "Motivo: " + denuncia.motivo().name();
    String painel = site.endereco(CAMINHO_DO_PAINEL);
    String texto = motivo + "\n\n" + painel;
    String html = "<p>" + motivo + "</p>\n<p><a href=\"" + painel + "\">" + painel + "</a></p>";
    return new MensagemDeEmail(contato, ASSUNTO, texto, html);
  }
}
