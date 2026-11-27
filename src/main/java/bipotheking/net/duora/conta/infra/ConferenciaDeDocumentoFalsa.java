package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.ConferenciaDeDocumento;
import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Conferência de documento de desenvolvimento e e2e, sem fornecedor: lê o id da sessão na
 * referência da {@link ProvaDeVidaFalsa} e responde pelos marcadores do e-mail da conta. {@code
 * docmenor} lê uma data de 15 anos atrás, {@code docoutro} acha outro rosto e {@code docilegivel}
 * não aceita o documento; sem marcador, devolve a data declarada com o rosto conferido. Selfie que
 * não veio do falso não é aceita. A {@link GuardaDaVerificacaoReal} a barra em produção.
 */
@Component
@ConditionalOnProperty(name = "duora.verificacao.documento.provedor", havingValue = "falso")
class ConferenciaDeDocumentoFalsa implements ConferenciaDeDocumento {

  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
  private static final int IDADE_DO_MENOR = 15;
  private static final ResultadoDoDocumento NAO_ACEITO =
      new ResultadoDoDocumento(false, Optional.empty(), false);

  private final RepositorioDeVerificacoes verificacoes;
  private final RepositorioDeContas contas;
  private final Clock relogio;

  ConferenciaDeDocumentoFalsa(
      RepositorioDeVerificacoes verificacoes, RepositorioDeContas contas, Clock relogio) {
    this.verificacoes = verificacoes;
    this.contas = contas;
    this.relogio = relogio;
  }

  @Override
  public ResultadoDoDocumento conferir(Imagem frente, Imagem verso, Imagem selfie) {
    Optional<Conta> conta =
        ProvaDeVidaFalsa.sessaoDaReferencia(selfie)
            .flatMap(sessao -> ProvaDeVidaFalsa.contaDaSessao(verificacoes, contas, sessao));
    if (conta.isEmpty()) {
      return NAO_ACEITO;
    }
    Set<String> marcadores = ProvaDeVidaFalsa.marcadores(conta.get());
    if (marcadores.contains("docilegivel")) {
      return NAO_ACEITO;
    }
    Optional<LocalDate> data =
        marcadores.contains("docmenor")
            ? Optional.of(LocalDate.now(relogio.withZone(FUSO)).minusYears(IDADE_DO_MENOR))
            : Optional.ofNullable(conta.get().dataDeNascimento());
    return new ResultadoDoDocumento(true, data, !marcadores.contains("docoutro"));
  }
}
