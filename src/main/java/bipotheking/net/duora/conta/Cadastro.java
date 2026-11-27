package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeAceites;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.ResultadoDoCadastro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.web.LimiteDeTaxa;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cadastro da conta pendente: data de nascimento declarada e aceite das versões publicadas dos
 * termos e da privacidade. Maior de idade vai a {@code AGUARDANDO_VERIFICACAO}; menor tem a conta
 * excluída e o e-mail bloqueado, sem deixar dado pessoal para trás. Os efeitos de cada desfecho
 * acontecem numa transação.
 */
@Service
public class Cadastro {

  private static final String LIMITE_POR_CONTA = "cadastro-conta";
  private static final String MOTIVO_DO_BLOQUEIO = "MENOR";
  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  private static final Logger LOG = LoggerFactory.getLogger(Cadastro.class);

  private final LimiteDeTaxa limiteDeTaxa;
  private final DocumentosLegais documentos;
  private final RepositorioDeContas contas;
  private final RepositorioDeAceites aceites;
  private final LimpezaDeConta limpeza;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  Cadastro(
      LimiteDeTaxa limiteDeTaxa,
      DocumentosLegais documentos,
      RepositorioDeContas contas,
      RepositorioDeAceites aceites,
      LimpezaDeConta limpeza,
      TransactionTemplate transacao,
      Clock relogio) {
    this.limiteDeTaxa = limiteDeTaxa;
    this.documentos = documentos;
    this.contas = contas;
    this.aceites = aceites;
    this.limpeza = limpeza;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  /**
   * Conclui o cadastro. Confere, nesta ordem, o limite de taxa, o aceite, as versões aceitas, o
   * estado da conta e a data; a idade é contada no dia corrente de São Paulo. Versão ausente conta
   * como desatualizada.
   *
   * @throws ErroDeNegocio {@code LIMITE_DE_TAXA}, {@code ACEITE_NECESSARIO}, {@code
   *     TERMOS_DESATUALIZADOS}, {@code DATA_INVALIDA} ou {@code CADASTRO_JA_CONCLUIDO}
   */
  public ResultadoDoCadastro concluir(
      ContaId conta,
      @Nullable LocalDate nascimento,
      @Nullable String versaoTermos,
      @Nullable String versaoPrivacidade,
      boolean aceito) {
    limiteDeTaxa.consumir(LIMITE_POR_CONTA, conta.toString());
    if (!aceito) {
      throw CodigoCadastro.ACEITE_NECESSARIO.erro();
    }
    VersaoDeDocumento termos = documentos.atual(DocumentoLegal.TERMOS);
    VersaoDeDocumento privacidade = documentos.atual(DocumentoLegal.PRIVACIDADE);
    if (!termos.versao().equals(versaoTermos) || !privacidade.versao().equals(versaoPrivacidade)) {
      throw CodigoCadastro.TERMOS_DESATUALIZADOS.erro();
    }
    ResultadoDoCadastro resultado =
        transacao.execute(status -> efetuar(conta, nascimento, termos, privacidade));
    if (resultado == null) {
      throw new IllegalStateException("A transação do cadastro não devolveu resultado");
    }
    return resultado;
  }

  private ResultadoDoCadastro efetuar(
      ContaId id,
      @Nullable LocalDate nascimento,
      VersaoDeDocumento termos,
      VersaoDeDocumento privacidade) {
    Instant agora = relogio.instant();
    Conta conta =
        contas
            .porId(id)
            .orElseThrow(() -> new IllegalStateException("Sessão de conta inexistente: " + id));
    ResultadoDoCadastro resultado =
        conta.concluirCadastro(nascimento, LocalDate.ofInstant(agora, FUSO));
    switch (resultado) {
      case ResultadoDoCadastro.Concluido _ -> {
        if (!contas.marcarAguardandoVerificacao(
            id, Objects.requireNonNull(nascimento, "Concluído sem data"))) {
          throw CodigoCadastro.CADASTRO_JA_CONCLUIDO.erro();
        }
        aceites.gravar(id, DocumentoLegal.TERMOS, termos, agora);
        aceites.gravar(id, DocumentoLegal.PRIVACIDADE, privacidade, agora);
        LOG.info("Cadastro concluído para a conta {}", id);
      }
      case ResultadoDoCadastro.RecusadoPorIdade _ -> {
        limpeza.excluirEBloquear(id, emailDe(conta), MOTIVO_DO_BLOQUEIO, agora);
        LOG.info("Cadastro recusado por idade; conta {} excluída", id);
      }
    }
    return resultado;
  }

  /** A conta pendente sempre tem e-mail: a invariante da {@link Conta} garante. */
  private static Email emailDe(Conta conta) {
    return Objects.requireNonNull(conta.email(), "Conta pendente sem e-mail");
  }
}
