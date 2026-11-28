package bipotheking.net.duora.perfil.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.Orientacao;
import bipotheking.net.duora.perfil.ServicoDeOrientacao;
import bipotheking.net.duora.perfil.dominio.DadosDeOrientacao;
import bipotheking.net.duora.perfil.dominio.DescricaoCurta;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Orientação da conta da sessão: dado sensível, só da própria conta. Os {@code record}s não geram
 * {@code toString} com os valores, para nenhum log carregar a orientação.
 */
@RestController
@RequestMapping("/api/perfil/orientacao")
class OrientacaoController {

  private static final OrientacaoJson SEM_DADOS = new OrientacaoJson(null, null, false, null);

  private final ServicoDeOrientacao servico;

  OrientacaoController(ServicoDeOrientacao servico) {
    this.servico = servico;
  }

  /** Consentimento e {@code mostrarOrientacao} ausentes contam como falsos. */
  record DadosDeOrientacaoJson(
      @Nullable Orientacao orientacao,
      @Nullable String descricaoOrientacao,
      @Nullable Boolean mostrarOrientacao,
      @Nullable Boolean consentimento) {

    @Override
    public String toString() {
      return "DadosDeOrientacaoJson[***]";
    }
  }

  record OrientacaoJson(
      @Nullable Orientacao orientacao,
      @Nullable String descricaoOrientacao,
      boolean mostrarOrientacao,
      @Nullable String consentidaEm) {

    @Override
    public String toString() {
      return "OrientacaoJson[***]";
    }

    static OrientacaoJson de(DadosDeOrientacao dados) {
      return new OrientacaoJson(
          dados.orientacao(),
          dados.descricao().map(DescricaoCurta::valor).orElse(null),
          dados.mostrar(),
          dados.consentidaEm().toString());
    }
  }

  @GetMapping
  OrientacaoJson consultar(@ContaAtual ContaId conta) {
    return servico.minha(conta).map(OrientacaoJson::de).orElse(SEM_DADOS);
  }

  /** Recusa na ordem do contrato: perfil, consentimento e, por último, os dados. */
  @PutMapping
  OrientacaoJson salvar(@ContaAtual ContaId conta, @RequestBody DadosDeOrientacaoJson corpo) {
    return OrientacaoJson.de(
        servico.salvar(
            conta,
            corpo.orientacao(),
            corpo.descricaoOrientacao(),
            Boolean.TRUE.equals(corpo.mostrarOrientacao()),
            Boolean.TRUE.equals(corpo.consentimento())));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void apagar(@ContaAtual ContaId conta) {
    servico.apagar(conta);
  }
}
