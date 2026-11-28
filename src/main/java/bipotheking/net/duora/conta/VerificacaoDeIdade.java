package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.CredencialCurta;
import bipotheking.net.duora.conta.dominio.CredencialDoNavegador;
import bipotheking.net.duora.conta.dominio.DecisaoDeIdade;
import bipotheking.net.duora.conta.dominio.FinalidadeDaTentativa;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.MetodoDeVerificacao;
import bipotheking.net.duora.conta.dominio.ProvaDeVida;
import bipotheking.net.duora.conta.dominio.RepositorioDeAceites;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.conta.dominio.ReservaDeTentativa;
import bipotheking.net.duora.conta.dominio.ResultadoDaProva;
import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import bipotheking.net.duora.conta.dominio.SessaoDeProva;
import bipotheking.net.duora.conta.dominio.Tentativa;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.web.Idempotencia;
import bipotheking.net.duora.web.LimiteDeTaxa;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verificação de idade pela selfie: abre a sessão de prova de vida com a credencial do navegador,
 * decide pelo resultado e aplica a decisão na conta. Nenhuma chamada ao fornecedor acontece dentro
 * de transação de banco: a reserva da tentativa e a conclusão rodam cada uma numa transação curta,
 * antes e depois do fornecedor.
 */
@Service
public class VerificacaoDeIdade {

  private static final String LIMITE_POR_CONTA = "verificacao-conta";
  private static final String ESCOPO_DO_RESULTADO = "verificacao-resultado";

  /** A janela dos limites e da dúvida: 24 h corridas, a mesma do repositório. */
  static final Duration JANELA = Duration.parse("P1D");

  /** O {@code Retry-After} nunca sai zero nem negativo, mesmo com o relógio na borda da janela. */
  private static final Duration ESPERA_MINIMA = Duration.ofSeconds(1);

  private static final Logger LOG = LoggerFactory.getLogger(VerificacaoDeIdade.class);

  private final LimiteDeTaxa limiteDeTaxa;
  private final RepositorioDeContas contas;
  private final RepositorioDeVerificacoes verificacoes;
  private final ProvaDeVida provaDeVida;
  private final CredencialDoNavegador credencialDoNavegador;
  private final LimitesDaVerificacao limites;
  private final EfeitosDaVerificacao efeitos;
  private final Idempotencia idempotencia;
  private final RepositorioDeAceites aceites;
  private final DocumentosLegais documentos;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  VerificacaoDeIdade(
      LimiteDeTaxa limiteDeTaxa,
      RepositorioDeContas contas,
      RepositorioDeVerificacoes verificacoes,
      ProvaDeVida provaDeVida,
      CredencialDoNavegador credencialDoNavegador,
      LimitesDaVerificacao limites,
      EfeitosDaVerificacao efeitos,
      Idempotencia idempotencia,
      RepositorioDeAceites aceites,
      DocumentosLegais documentos,
      TransactionTemplate transacao,
      Clock relogio) {
    this.limiteDeTaxa = limiteDeTaxa;
    this.contas = contas;
    this.verificacoes = verificacoes;
    this.provaDeVida = provaDeVida;
    this.credencialDoNavegador = credencialDoNavegador;
    this.limites = limites;
    this.efeitos = efeitos;
    this.idempotencia = idempotencia;
    this.aceites = aceites;
    this.documentos = documentos;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  /**
   * Abre uma sessão de prova de vida. Confere, nesta ordem, o limite de taxa, o consentimento, a
   * versão do texto que a pessoa viu, o estado da conta e, para o documento, a dúvida de selfie nas
   * últimas 24 h; então reserva a tentativa numa transação curta (a trava da conta serializa as
   * reservas concorrentes), que, só com a reserva feita, também grava o aceite dessa versão do
   * consentimento biométrico (uma vez por versão), e, já fora dela, abre a sessão e emite a
   * credencial. Se o fornecedor falhar, a tentativa é descartada e não conta no limite do dia.
   *
   * @param versaoConsentimento a versão do texto exibido; ausente conta como desatualizada
   * @throws ErroDeNegocio {@code LIMITE_DE_TAXA}, {@code CONSENTIMENTO_BIOMETRICO_NECESSARIO},
   *     {@code CONSENTIMENTO_DESATUALIZADO}, {@code VERIFICACAO_FORA_DE_ORDEM}, {@link
   *     LimiteDeVerificacaoExcedido} ou {@code PROVA_DE_VIDA_INDISPONIVEL}
   */
  public SessaoDeCaptura iniciar(
      ContaId conta,
      FinalidadeDaTentativa finalidade,
      boolean consentimento,
      @Nullable String versaoConsentimento) {
    limiteDeTaxa.consumir(LIMITE_POR_CONTA, conta.toString());
    if (!consentimento) {
      throw CodigoVerificacao.CONSENTIMENTO_BIOMETRICO_NECESSARIO.erro();
    }
    VersaoDeDocumento versao = documentos.atual(DocumentoLegal.BIOMETRIA);
    if (!versao.versao().equals(versaoConsentimento)) {
      throw CodigoVerificacao.CONSENTIMENTO_DESATUALIZADO.erro();
    }
    exigirAguardando(conta(conta));
    Instant agora = relogio.instant();
    if (finalidade == FinalidadeDaTentativa.DOCUMENTO
        && !verificacoes.duvidaDesde(conta, FinalidadeDaTentativa.SELFIE, agora.minus(JANELA))) {
      throw CodigoVerificacao.VERIFICACAO_FORA_DE_ORDEM.erro();
    }
    UUID tentativa = reservar(conta, finalidade, versao, agora);
    try {
      SessaoDeProva sessao = provaDeVida.criarSessao(tentativa);
      CredencialCurta credencial = credencialDoNavegador.emitir(tentativa);
      verificacoes.associarSessao(tentativa, sessao.id());
      return new SessaoDeCaptura(sessao.id(), sessao.regiao(), provaDeVida.modo(), credencial);
    } catch (FornecedorIndisponivel e) {
      descartar(tentativa, conta, e);
      LOG.warn("Prova de vida indisponível ao abrir sessão da conta {}: {}", conta, e.getMessage());
      throw CodigoVerificacao.PROVA_DE_VIDA_INDISPONIVEL.erro();
    } catch (RuntimeException e) {
      descartar(tentativa, conta, e);
      throw e;
    }
  }

  /**
   * Descarta a tentativa depois de uma falha sem trocar a exceção original. Se o descarte também
   * falhar, a falha sai sempre no log de erro (uma {@link ErroDeNegocio} não guarda suprimidas) e
   * vai como suprimida na original.
   */
  private void descartar(UUID tentativa, ContaId conta, RuntimeException original) {
    try {
      verificacoes.descartar(tentativa);
    } catch (RuntimeException falha) {
      LOG.error("Tentativa {} da conta {} não foi descartada", tentativa, conta, falha);
      original.addSuppressed(falha);
    }
  }

  /**
   * Decide a tentativa {@code SELFIE} da sessão e aplica a decisão. A chave de idempotência é o id
   * da sessão: a repetição devolve o mesmo desfecho sem chamar o fornecedor, e uma chamada
   * concorrente recebe {@code REQUISICAO_EM_ANDAMENTO}. O resultado é lido fora de transação; a
   * conclusão da tentativa, os efeitos na conta e a resposta guardada vão juntos numa transação.
   * Qualquer falha antes do commit libera a chave, e o mesmo id de sessão pode tentar de novo.
   *
   * @throws ErroDeNegocio {@code LIMITE_DE_TAXA}, {@code SESSAO_DE_VERIFICACAO_INVALIDA} (sessão
   *     inexistente, de outra conta, de documento ou já concluída), {@code
   *     REQUISICAO_EM_ANDAMENTO}, {@code VERIFICACAO_FORA_DE_ORDEM} ou {@code
   *     PROVA_DE_VIDA_INDISPONIVEL}
   */
  public DesfechoDaVerificacao concluirSelfie(ContaId conta, String sessaoId) {
    limiteDeTaxa.consumir(LIMITE_POR_CONTA, conta.toString());
    Tentativa tentativa =
        verificacoes
            .porSessao(sessaoId)
            .filter(t -> t.conta().equals(conta) && t.finalidade() == FinalidadeDaTentativa.SELFIE)
            .orElseThrow(CodigoVerificacao.SESSAO_DE_VERIFICACAO_INVALIDA::erro);
    Idempotencia.Reserva<DesfechoDaVerificacao> reserva =
        idempotencia.reservar(
            ESCOPO_DO_RESULTADO, sessaoId, conta.toString(), DesfechoDaVerificacao.class);
    return switch (reserva) {
      case Idempotencia.Reserva.JaRespondida<DesfechoDaVerificacao> respondida ->
          respondida.resposta();
      case Idempotencia.Reserva.Tua<DesfechoDaVerificacao> tua ->
          decidirSelfie(tentativa, sessaoId, tua.dono());
    };
  }

  /** A situação da verificação da conta: estado, o que ainda cabe hoje e quem atende a captura. */
  public SituacaoDaVerificacao situacao(ContaId id) {
    Conta conta = conta(id);
    Instant agora = relogio.instant();
    EstadoDaVerificacao estado =
        switch (conta.estado()) {
          case ATIVA -> EstadoDaVerificacao.APROVADA;
          case AGUARDANDO_VERIFICACAO -> aguardando(id, agora);
          default -> throw CodigoVerificacao.VERIFICACAO_FORA_DE_ORDEM.erro();
        };
    return new SituacaoDaVerificacao(
        estado,
        restantes(id, FinalidadeDaTentativa.SELFIE, agora),
        restantes(id, FinalidadeDaTentativa.DOCUMENTO, agora),
        provaDeVida.modo());
  }

  /**
   * A dúvida do documento vale sobre a da selfie: o envio que voltou em dúvida já mostrou a
   * análise, e recarregar a página não deve reabri-lo.
   */
  private EstadoDaVerificacao aguardando(ContaId id, Instant agora) {
    Instant desde = agora.minus(JANELA);
    if (verificacoes.duvidaDesde(id, FinalidadeDaTentativa.DOCUMENTO, desde)) {
      return EstadoDaVerificacao.ANALISE;
    }
    return verificacoes.duvidaDesde(id, FinalidadeDaTentativa.SELFIE, desde)
        ? EstadoDaVerificacao.DUVIDA
        : EstadoDaVerificacao.AGUARDANDO;
  }

  private UUID reservar(
      ContaId conta,
      FinalidadeDaTentativa finalidade,
      VersaoDeDocumento consentimento,
      Instant agora) {
    ReservaDeTentativa reserva =
        transacao.execute(status -> {
          ReservaDeTentativa feita =
              verificacoes.reservar(conta, finalidade, limites.limite(finalidade), agora);
          // A checagem de iniciar é só atalho: a conta pode ter mudado enquanto a reserva esperava
          // a trava. Relida já com a trava, a recusa desfaz a tentativa inserida.
          exigirAguardando(conta(conta));
          // O 429 do limite não abre sessão: sem reserva, o consentimento não foi usado.
          if (feita instanceof ReservaDeTentativa.Feita) {
            aceites.gravar(conta, DocumentoLegal.BIOMETRIA, consentimento, agora);
          }
          return feita;
        });
    return switch (reserva) {
      case ReservaDeTentativa.Feita feita -> feita.tentativa();
      case ReservaDeTentativa.Esgotada esgotada ->
          throw new LimiteDeVerificacaoExcedido(ateLiberar(agora, esgotada.liberaEm()));
      case null -> throw new IllegalStateException("A reserva da tentativa não devolveu nada");
    };
  }

  private DesfechoDaVerificacao decidirSelfie(Tentativa tentativa, String sessaoId, UUID dono) {
    boolean concluida = false;
    try {
      exigirNaoConcluida(tentativa);
      Conta conta = conta(tentativa.conta());
      exigirAguardando(conta);
      ResultadoDaVerificacao resultado = DecisaoDeIdade.pelaSelfie(lerResultado(sessaoId, conta));
      DesfechoDaVerificacao desfecho =
          transacao.execute(status -> {
            Instant agora = relogio.instant();
            efeitos.concluir(
                tentativa, conta, resultado, MetodoDeVerificacao.ESTIMATIVA_FACIAL, agora);
            DesfechoDaVerificacao novo = desfecho(conta.id(), resultado, agora);
            idempotencia.concluir(ESCOPO_DO_RESULTADO, sessaoId, dono, novo);
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
          MetodoDeVerificacao.ESTIMATIVA_FACIAL);
      return desfecho;
    } finally {
      if (!concluida) {
        idempotencia.liberar(ESCOPO_DO_RESULTADO, sessaoId, dono);
      }
    }
  }

  private ResultadoDaProva lerResultado(String sessaoId, Conta conta) {
    try {
      return provaDeVida.resultado(sessaoId);
    } catch (FornecedorIndisponivel e) {
      LOG.warn(
          "Prova de vida indisponível no resultado da conta {}: {}", conta.id(), e.getMessage());
      throw CodigoVerificacao.PROVA_DE_VIDA_INDISPONIVEL.erro();
    }
  }

  private DesfechoDaVerificacao desfecho(
      ContaId conta, ResultadoDaVerificacao resultado, Instant agora) {
    int envios = restantes(conta, FinalidadeDaTentativa.DOCUMENTO, agora);
    return new DesfechoDaVerificacao(
        resultado,
        resultado == ResultadoDaVerificacao.DUVIDA && envios > 0,
        restantes(conta, FinalidadeDaTentativa.SELFIE, agora),
        envios);
  }

  private int restantes(ContaId conta, FinalidadeDaTentativa finalidade, Instant agora) {
    int usadas = verificacoes.contarDesde(conta, finalidade, agora.minus(JANELA));
    return Math.max(0, limites.limite(finalidade) - usadas);
  }

  private Conta conta(ContaId id) {
    return contaDaSessao(contas, id);
  }

  /**
   * A conta da sessão. Se ela sumiu depois da checagem da sessão (excluída numa requisição
   * paralela), a recusa é a mesma da sessão, e o front recarrega.
   *
   * @throws ErroDeNegocio {@code CONTA_INDISPONIVEL}
   */
  static Conta contaDaSessao(RepositorioDeContas contas, ContaId id) {
    return contas
        .porId(id)
        .orElseThrow(() ->
            new ErroDeNegocio(
                CodigoSessao.CONTA_INDISPONIVEL, CodigoSessao.CONTA_INDISPONIVEL.mensagem()));
  }

  /**
   * Tentativa já concluída não chama fornecedor de novo. A repetição dentro do prazo da
   * idempotência devolve a resposta guardada antes daqui; depois dele, a sessão não serve mais.
   */
  static void exigirNaoConcluida(Tentativa tentativa) {
    if (tentativa.resultado() != null) {
      throw CodigoVerificacao.SESSAO_DE_VERIFICACAO_INVALIDA.erro();
    }
  }

  /** Só a conta aguardando verificação abre sessão, conclui selfie ou envia documento. */
  static void exigirAguardando(Conta conta) {
    if (conta.estado() != EstadoConta.AGUARDANDO_VERIFICACAO) {
      throw CodigoVerificacao.VERIFICACAO_FORA_DE_ORDEM.erro();
    }
  }

  private static Duration ateLiberar(Instant agora, Instant liberaEm) {
    Duration espera = Duration.between(agora, liberaEm);
    return espera.compareTo(ESPERA_MINIMA) < 0 ? ESPERA_MINIMA : espera;
  }
}
