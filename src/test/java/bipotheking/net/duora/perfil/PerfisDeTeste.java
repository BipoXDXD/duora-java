package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.AjustesDeBusca;
import bipotheking.net.duora.perfil.dominio.Apelido;
import bipotheking.net.duora.perfil.dominio.Bio;
import bipotheking.net.duora.perfil.dominio.DadosDeNamoro;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Intencoes;
import bipotheking.net.duora.perfil.dominio.Perfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeLocalizacao;
import java.time.Clock;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Perfis prontos para os testes das outras etapas, pelos mesmos serviços que a API usa. A célula
 * entra direto pela porta, sem passar por coordenada.
 */
@Component
public class PerfisDeTeste {

  /** A descrição usada quando o gênero próprio é {@link Genero#OUTRO}. */
  public static final String DESCRICAO_DO_GENERO = "teste";

  private final ServicoDePerfil perfis;
  private final ServicoDePreferencias preferencias;
  private final ServicoDeNamoro namoro;
  private final RepositorioDeLocalizacao localizacoes;
  private final Clock relogio;

  PerfisDeTeste(
      ServicoDePerfil perfis,
      ServicoDePreferencias preferencias,
      ServicoDeNamoro namoro,
      RepositorioDeLocalizacao localizacoes,
      Clock relogio) {
    this.perfis = perfis;
    this.preferencias = preferencias;
    this.namoro = namoro;
    this.localizacoes = localizacoes;
    this.relogio = relogio;
  }

  /**
   * Grava (ou troca) o perfil com as intenções de {@code desejadas}, a célula, se houver, os
   * ajustes e, com gênero próprio, os dados de namoro consentidos. A célula vem antes dos ajustes,
   * porque um raio pede a localização ativa; sem célula, a localização gravada fica como está.
   */
  public void definir(
      ContaId conta, String apelido, Preferencias desejadas, Optional<CelulaGeohash> celula) {
    EdicaoDoPerfil dados =
        new EdicaoDoPerfil(
            Apelido.de(apelido), Optional.empty(), new Intencoes(desejadas.intencao()), Bio.de(""));
    @Nullable Long versao = perfis.meu(conta).map(Perfil::versao).orElse(null);
    perfis.salvar(conta, versao, dados);
    celula.ifPresent(c -> localizacoes.consentirEGravar(conta, c, relogio.instant()));
    preferencias.salvar(
        conta,
        preferencias.ajustes(conta).versao(),
        new AjustesDeBusca(desejadas.faixaEtaria(), desejadas.raio(), desejadas.raioObrigatorio()));
    desejadas
        .generoProprio()
        .ifPresent(genero ->
            namoro.salvar(
                conta,
                DadosDeNamoro.de(
                    genero,
                    genero == Genero.OUTRO ? DESCRICAO_DO_GENERO : null,
                    desejadas.generosBuscados()),
                true));
  }
}
