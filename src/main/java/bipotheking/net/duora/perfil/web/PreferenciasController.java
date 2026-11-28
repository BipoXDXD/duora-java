package bipotheking.net.duora.perfil.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.FaixaEtaria;
import bipotheking.net.duora.perfil.Raio;
import bipotheking.net.duora.perfil.ServicoDePreferencias;
import bipotheking.net.duora.perfil.dominio.AjustesDeBusca;
import bipotheking.net.duora.perfil.dominio.AjustesVersionados;
import bipotheking.net.duora.web.CodigoWeb;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Os ajustes de busca da conta da sessão: faixa etária e raio. */
@RestController
@RequestMapping("/api/perfil/preferencias")
class PreferenciasController {

  private final ServicoDePreferencias servico;

  PreferenciasController(ServicoDePreferencias servico) {
    this.servico = servico;
  }

  /** Idade ausente é faixa inválida; raio ou obrigatoriedade ausentes, corpo inválido. */
  record AjustesJson(
      @Nullable Long versao,
      @Nullable Integer idadeMinima,
      @Nullable Integer idadeMaxima,
      @Nullable Raio raio,
      @Nullable Boolean raioObrigatorio) {}

  record PreferenciasJson(
      @Nullable Long versao, int idadeMinima, int idadeMaxima, Raio raio, boolean raioObrigatorio) {

    static PreferenciasJson de(AjustesVersionados versionados) {
      AjustesDeBusca ajustes = versionados.ajustes();
      return new PreferenciasJson(
          versionados.versao(),
          ajustes.faixa().minima(),
          ajustes.faixa().maxima(),
          ajustes.raio(),
          ajustes.raioObrigatorio());
    }
  }

  @GetMapping
  PreferenciasJson consultar(@ContaAtual ContaId conta) {
    return PreferenciasJson.de(servico.ajustes(conta));
  }

  @PutMapping
  PreferenciasJson salvar(@ContaAtual ContaId conta, @RequestBody AjustesJson corpo) {
    Integer minima = corpo.idadeMinima();
    Integer maxima = corpo.idadeMaxima();
    Raio raio = corpo.raio();
    Boolean obrigatorio = corpo.raioObrigatorio();
    if (raio == null || obrigatorio == null) {
      throw new ErroDeNegocio(
          CodigoWeb.CORPO_INVALIDO, "O corpo da requisição não está no formato esperado.");
    }
    if (minima == null || maxima == null) {
      throw CodigoPerfil.FAIXA_ETARIA_INVALIDA.erro();
    }
    AjustesDeBusca ajustes = new AjustesDeBusca(new FaixaEtaria(minima, maxima), raio, obrigatorio);
    return PreferenciasJson.de(servico.salvar(conta, corpo.versao(), ajustes));
  }
}
