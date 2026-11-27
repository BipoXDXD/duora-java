package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta das tentativas e da aprovação da verificação de idade. A janela do limite é de 24 h
 * corridas: conta a tentativa com {@code criadaEm} estritamente depois de {@code agora − 1 dia},
 * com ou sem sessão. Tudo roda na transação do chamador.
 */
public interface RepositorioDeVerificacoes {

  /**
   * Trava a linha da conta ({@code for update}) e conta as tentativas da finalidade nas últimas 24
   * h; abaixo de {@code limitePorDia}, grava uma tentativa nova, sem sessão, criada em {@code
   * agora}. A trava serializa as reservas da mesma conta até o commit do chamador.
   *
   * @param limitePorDia pelo menos 1
   * @return {@link ReservaDeTentativa.Feita} com o id gravado; {@link ReservaDeTentativa.Esgotada}
   *     com a mais antiga da janela mais 1 dia, se já está no limite
   * @throws IllegalArgumentException se {@code limitePorDia} é menor que 1
   */
  ReservaDeTentativa reservar(
      ContaId conta, FinalidadeDaTentativa finalidade, int limitePorDia, Instant agora);

  /**
   * Grava a sessão do fornecedor na tentativa reservada.
   *
   * @throws IllegalStateException se a tentativa não existe ou já tem sessão
   */
  void associarSessao(UUID tentativa, String sessaoId);

  /**
   * Apaga a tentativa ainda não concluída (a sessão do fornecedor falhou e a tentativa não conta);
   * a concluída fica.
   */
  void descartar(UUID tentativa);

  Optional<Tentativa> porSessao(String sessaoId);

  /**
   * Grava o resultado se a tentativa ainda não foi concluída. A tentativa precisa ter sessão.
   *
   * @return se esta chamada concluiu; {@code false} se já estava concluída ou não existe
   */
  boolean concluir(UUID tentativa, ResultadoDaVerificacao resultado, Instant agora);

  /** Quantas tentativas da finalidade a conta criou depois de {@code desde} (estrito). */
  int contarDesde(ContaId conta, FinalidadeDaTentativa finalidade, Instant desde);

  /** Se a conta tem tentativa {@code SELFIE} com {@code DUVIDA} criada depois de {@code desde}. */
  boolean duvidaDeSelfieDesde(ContaId conta, Instant desde);

  /** Grava a aprovação da conta; se ela já tem uma, a primeira fica. */
  void gravarAprovacao(ContaId conta, MetodoDeVerificacao metodo, Instant agora);

  boolean aprovada(ContaId conta);

  /** Apaga as tentativas e a aprovação da conta (exclusão). */
  void apagarDaConta(ContaId conta);
}
