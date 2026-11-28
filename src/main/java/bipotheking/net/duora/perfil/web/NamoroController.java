package bipotheking.net.duora.perfil.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.Genero;
import bipotheking.net.duora.perfil.ServicoDeNamoro;
import bipotheking.net.duora.perfil.dominio.DadosDeNamoro;
import bipotheking.net.duora.perfil.dominio.DescricaoCurta;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * Gênero e interesses da conta da sessão: dados sensíveis, só da própria conta. Os {@code record}s
 * não geram {@code toString} com os valores, para nenhum log carregar o gênero.
 */
@RestController
@RequestMapping("/api/perfil/namoro")
class NamoroController {

  private static final NamoroJson SEM_DADOS = new NamoroJson(null, null, List.of(), null);

  private final ServicoDeNamoro servico;

  NamoroController(ServicoDeNamoro servico) {
    this.servico = servico;
  }

  /** Consentimento ausente conta como recusado. */
  record DadosDeNamoroJson(
      @Nullable Genero genero,
      @Nullable String descricaoGenero,
      @Nullable List<Genero> interesses,
      @Nullable Boolean consentimento) {

    @Override
    public String toString() {
      return "DadosDeNamoroJson[***]";
    }
  }

  record NamoroJson(
      @Nullable Genero genero,
      @Nullable String descricaoGenero,
      List<Genero> interesses,
      @Nullable String consentidoEm) {

    @Override
    public String toString() {
      return "NamoroJson[***]";
    }

    static NamoroJson de(DadosDeNamoro dados, Optional<Instant> consentidoEm) {
      return new NamoroJson(
          dados.genero(),
          dados.descricao().map(DescricaoCurta::valor).orElse(null),
          List.copyOf(dados.interesses()),
          consentidoEm.map(Instant::toString).orElse(null));
    }
  }

  @GetMapping
  NamoroJson consultar(@ContaAtual ContaId conta) {
    return servico
        .meus(conta)
        .map(dados -> NamoroJson.de(dados, servico.consentidoEm(conta)))
        .orElse(SEM_DADOS);
  }

  /** Recusa na ordem do contrato: perfil, intenção, consentimento e, por último, os dados. */
  @PutMapping
  NamoroJson salvar(@ContaAtual ContaId conta, @RequestBody DadosDeNamoroJson corpo) {
    boolean consentimento = Boolean.TRUE.equals(corpo.consentimento());
    servico.exigirCondicoes(conta, consentimento);
    DadosDeNamoro dados =
        DadosDeNamoro.de(corpo.genero(), corpo.descricaoGenero(), corpo.interesses());
    DadosDeNamoro gravados = servico.salvar(conta, dados, consentimento);
    return NamoroJson.de(gravados, servico.consentidoEm(conta));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revogar(@ContaAtual ContaId conta) {
    servico.revogar(conta);
  }
}
