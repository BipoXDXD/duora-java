package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.ConferenciaDeDocumento;
import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.DecisaoDeIdade;
import bipotheking.net.duora.conta.dominio.FinalidadeDaTentativa;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.MetodoDeVerificacao;
import bipotheking.net.duora.conta.dominio.ProvaDeVida;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.conta.dominio.ResultadoDaProva;
import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import bipotheking.net.duora.conta.dominio.SituacaoDaProva;
import bipotheking.net.duora.conta.dominio.Tentativa;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.web.Idempotencia;
import bipotheking.net.duora.web.LimiteDeTaxa;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verificação de idade pelo documento na faixa de dúvida (D-B): frente, verso e a selfie nova da
 * sessão {@code DOCUMENTO} chegam num envio só. As imagens só circulam em memória durante a
 * requisição: nada as grava nem as registra no log. Nenhuma chamada a fornecedor acontece dentro de
 * transação: a prova de vida e a conferência rodam antes; a conclusão da tentativa, os efeitos na
 * conta e a resposta guardada vão juntos numa transação.
 */
@Service
public class VerificacaoPorDocumento {

  private static final String LIMITE_POR_CONTA = "verificacao-conta";
  private static final String ESCOPO_DO_DOCUMENTO = "verificacao-documento";
  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  private static final Logger LOG = LoggerFactory.getLogger(VerificacaoPorDocumento.class);

  private final LimiteDeTaxa limiteDeTaxa;
  private final RepositorioDeContas contas;
  private final RepositorioDeVerificacoes verificacoes;
  private final ProvaDeVida provaDeVida;
  private final ConferenciaDeDocumento conferencia;
  private final LimitesDaVerificacao limites;
  private final EfeitosDaVerificacao efeitos;
  private final Idempotencia idempotencia;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  VerificacaoPorDocumento(
      LimiteDeTaxa limiteDeTaxa,
      RepositorioDeContas contas,
      RepositorioDeVerificacoes verificacoes,
      ProvaDeVida provaDeVida,
      ConferenciaDeDocumento conferencia,
      LimitesDaVerificacao limites,
      EfeitosDaVerificacao efeitos,
      Idempotencia idempotencia,
      TransactionTemplate transacao,
      Clock relogio) {
    this.limiteDeTaxa = limiteDeTaxa;
    this.contas = contas;
    this.verificacoes = verificacoes;
    this.provaDeVida = provaDeVida;
    this.conferencia = conferencia;
    this.limites = limites;
    this.efeitos = efeitos;
    this.idempotencia = idempotencia;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  /** Frente e verso já lidos como imagem, para não circularem como bytes soltos. */
  private record Documento(Imagem frente, Imagem verso) {}

  /**
   * Decide a tentativa {@code DOCUMENTO} da sessão e aplica a decisão. As imagens são conferidas
   * antes de qualquer consulta ou chamada. A chave de idempotência é o id da sessão: a repetição
   * devolve o mesmo desfecho sem chamar os fornecedores, e um envio concorrente recebe {@code
   * REQUISICAO_EM_ANDAMENTO}. Prova de vida vencida ou ruim, fornecedor fora do ar ou qualquer
   * falha antes do commit libera a chave e não conclui nada. Com o fornecedor fora do ar, o mesmo
   * id de sessão pode reenviar; com a prova expirada, a tentativa é descartada e o reenvio recebe
   * {@code SESSAO_DE_VERIFICACAO_INVALIDA}; com a prova ruim, o reenvio recebe o mesmo {@code
   * REFAZER_PROVA_DE_VIDA}.
   *
   * @throws ErroDeNegocio {@code LIMITE_DE_TAXA}, {@code DOCUMENTO_INVALIDO}, {@code
   *     SESSAO_DE_VERIFICACAO_INVALIDA} (sessão inexistente, de outra conta, de selfie ou já
   *     concluída), {@code REQUISICAO_EM_ANDAMENTO}, {@code VERIFICACAO_FORA_DE_ORDEM}, {@code
   *     REFAZER_PROVA_DE_VIDA}, {@code PROVA_DE_VIDA_INDISPONIVEL} ou {@code
   *     CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL}
   */
  public DesfechoDaVerificacao concluir(
      ContaId conta, String sessaoId, byte[] frente, byte[] verso) {
    limiteDeTaxa.consumir(LIMITE_POR_CONTA, conta.toString());
    Documento documento = new Documento(ler(frente), ler(verso));
    Tentativa tentativa =
        verificacoes
            .porSessao(sessaoId)
            .filter(
                t -> t.conta().equals(conta) && t.finalidade() == FinalidadeDaTentativa.DOCUMENTO)
            .orElseThrow(CodigoVerificacao.SESSAO_DE_VERIFICACAO_INVALIDA::erro);
    Idempotencia.Reserva<DesfechoDaVerificacao> reserva =
        idempotencia.reservar(
            ESCOPO_DO_DOCUMENTO, sessaoId, conta.toString(), DesfechoDaVerificacao.class);
    return switch (reserva) {
      case Idempotencia.Reserva.JaRespondida<DesfechoDaVerificacao> respondida ->
          respondida.resposta();
      case Idempotencia.Reserva.Tua<DesfechoDaVerificacao> tua ->
          decidir(tentativa, sessaoId, documento, tua.dono());
    };
  }

  private static Imagem ler(byte[] bytes) {
    return Imagem.ler(bytes).orElseThrow(CodigoVerificacao.DOCUMENTO_INVALIDO::erro);
  }

  private DesfechoDaVerificacao decidir(
      Tentativa tentativa, String sessaoId, Documento documento, UUID dono) {
    boolean concluida = false;
    try {
      VerificacaoDeIdade.exigirNaoConcluida(tentativa);
      Conta conta = conta(tentativa.conta());
      VerificacaoDeIdade.exigirAguardando(conta);
      Imagem referencia = referenciaDaProva(tentativa, sessaoId, conta);
      ResultadoDoDocumento lido = conferir(documento, referencia, conta);
      LocalDate hoje = LocalDate.ofInstant(relogio.instant(), FUSO);
      ResultadoDaVerificacao resultado =
          DecisaoDeIdade.peloDocumento(nascimentoDe(conta), lido, hoje);
      DesfechoDaVerificacao desfecho =
          transacao.execute(status -> {
            Instant agora = relogio.instant();
            if (!verificacoes.concluir(tentativa.id(), resultado, agora)) {
              throw CodigoVerificacao.SESSAO_DE_VERIFICACAO_INVALIDA.erro();
            }
            efeitos.aplicar(conta, resultado, MetodoDeVerificacao.DOCUMENTO, agora);
            DesfechoDaVerificacao novo = desfecho(conta.id(), resultado, agora);
            idempotencia.concluir(ESCOPO_DO_DOCUMENTO, sessaoId, dono, novo);
            return novo;
          });
      if (desfecho == null) {
        throw new IllegalStateException("A transação da verificação não devolveu desfecho");
      }
      concluida = true;
      LOG.info(
          "Verificação de idade da conta {}: {} por {}",
          conta.id(),
          resultado,
          MetodoDeVerificacao.DOCUMENTO);
      return desfecho;
    } finally {
      if (!concluida) {
        idempotencia.liberar(ESCOPO_DO_DOCUMENTO, sessaoId, dono);
      }
    }
  }

  /**
   * A referência da selfie nova da sessão. Prova vencida, ruim ou sem referência pede outra selfie
   * sem concluir nada, e o front mantém as fotos do documento e reenvia com uma sessão nova. Só a
   * prova {@code EXPIRADA} descarta a tentativa e devolve o envio do dia (D14-bis): o upload lento
   * num 4G não é culpa da pessoa. Falha, confiança baixa ou prova ainda criada (envio sem selfie)
   * gastam o envio.
   */
  private Imagem referenciaDaProva(Tentativa tentativa, String sessaoId, Conta conta) {
    ResultadoDaProva prova;
    try {
      prova = provaDeVida.resultado(sessaoId);
    } catch (FornecedorIndisponivel e) {
      LOG.warn(
          "Prova de vida indisponível no documento da conta {}: {}", conta.id(), e.getMessage());
      throw CodigoVerificacao.PROVA_DE_VIDA_INDISPONIVEL.erro();
    }
    if (!DecisaoDeIdade.provaServeParaDocumento(prova)) {
      LOG.info(
          "Prova de vida da sessão de documento da conta {} não serve: {}",
          conta.id(),
          prova.situacao());
      if (prova.situacao() == SituacaoDaProva.EXPIRADA) {
        descartar(tentativa, conta);
      }
      throw CodigoVerificacao.REFAZER_PROVA_DE_VIDA.erro();
    }
    return prova.referencia().orElseThrow();
  }

  /**
   * A falha do descarte não troca o 409: a pessoa ainda precisa de uma selfie nova. Ela sai no log
   * de erro, porque a tentativa que ficou gasta um envio do dia.
   */
  private void descartar(Tentativa tentativa, Conta conta) {
    try {
      verificacoes.descartar(tentativa.id());
    } catch (RuntimeException falha) {
      LOG.error("Tentativa {} da conta {} não foi descartada", tentativa.id(), conta.id(), falha);
    }
  }

  private ResultadoDoDocumento conferir(Documento documento, Imagem referencia, Conta conta) {
    try {
      return conferencia.conferir(documento.frente(), documento.verso(), referencia);
    } catch (FornecedorIndisponivel e) {
      LOG.warn(
          "Conferência de documento indisponível para a conta {}: {}", conta.id(), e.getMessage());
      throw CodigoVerificacao.CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL.erro();
    }
  }

  /**
   * No envio do documento, {@code pedeDocumento} sai sempre {@code false} (contrato REST): o que
   * ainda cabe hoje vai nas contagens.
   */
  private DesfechoDaVerificacao desfecho(
      ContaId conta, ResultadoDaVerificacao resultado, Instant agora) {
    return new DesfechoDaVerificacao(
        resultado,
        false,
        restantes(conta, FinalidadeDaTentativa.SELFIE, agora),
        restantes(conta, FinalidadeDaTentativa.DOCUMENTO, agora));
  }

  private int restantes(ContaId conta, FinalidadeDaTentativa finalidade, Instant agora) {
    int usadas =
        verificacoes.contarDesde(conta, finalidade, agora.minus(VerificacaoDeIdade.JANELA));
    return Math.max(0, limites.limite(finalidade) - usadas);
  }

  private Conta conta(ContaId id) {
    return contas
        .porId(id)
        .orElseThrow(() -> new IllegalStateException("Sessão de conta inexistente: " + id));
  }

  /** A conta aguardando verificação sempre tem data: a invariante da {@link Conta} garante. */
  private static LocalDate nascimentoDe(Conta conta) {
    return Objects.requireNonNull(
        conta.dataDeNascimento(), "Conta aguardando verificação sem data de nascimento");
  }
}
