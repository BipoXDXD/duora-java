package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Perfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDePerfis;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** O perfil da própria conta: leitura e gravação com controle otimista pela versão. */
@Service
public class ServicoDePerfil {

  private static final long PRIMEIRA_VERSAO = 1;

  private final RepositorioDePerfis perfis;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  ServicoDePerfil(RepositorioDePerfis perfis, TransactionTemplate transacao, Clock relogio) {
    this.perfis = perfis;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  public Optional<Perfil> meu(ContaId conta) {
    return perfis.buscar(conta);
  }

  /**
   * Sem versão, cria o perfil na versão 1; com a versão que a pessoa leu, troca os dados e sobe a
   * versão.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code VERSAO_OBSOLETA} se a versão não bate
   *     com a gravada: perfil já existente na criação, inexistente na edição ou editado por outra
   *     aba no meio do caminho
   */
  public Perfil salvar(ContaId conta, @Nullable Long versaoEsperada, EdicaoDoPerfil dados) {
    Instant agora = relogio.instant();
    return Objects.requireNonNull(
        transacao.execute(_ -> gravar(conta, versaoEsperada, dados, agora)));
  }

  private Perfil gravar(
      ContaId conta, @Nullable Long versaoEsperada, EdicaoDoPerfil dados, Instant agora) {
    if (versaoEsperada == null) {
      Perfil novo = new Perfil(conta, dados, PRIMEIRA_VERSAO);
      if (!perfis.criar(novo, agora)) {
        throw CodigoPerfil.VERSAO_OBSOLETA.erro();
      }
      return novo;
    }
    if (versaoEsperada < PRIMEIRA_VERSAO) {
      throw CodigoPerfil.VERSAO_OBSOLETA.erro();
    }
    return perfis
        .atualizar(new Perfil(conta, dados, versaoEsperada), agora)
        .orElseThrow(CodigoPerfil.VERSAO_OBSOLETA::erro);
  }
}
