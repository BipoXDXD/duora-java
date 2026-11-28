package bipotheking.net.duora.perfil.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.Localizacoes;
import bipotheking.net.duora.web.CodigoWeb;
import java.time.Instant;
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
 * A localização da conta da sessão. Recebe a coordenada e devolve só se ela está ativa: a célula
 * nunca sai daqui, e nenhum log, exceção ou corpo de resposta carrega os números recebidos.
 */
@RestController
@RequestMapping("/api/perfil/localizacao")
class LocalizacaoController {

  private final Localizacoes localizacoes;

  LocalizacaoController(Localizacoes localizacoes) {
    this.localizacoes = localizacoes;
  }

  /**
   * O ponto enviado pelo aparelho. Sem {@code toString} gerado: o do record imprimiria os números.
   */
  record CoordenadaJson(@Nullable Double latitude, @Nullable Double longitude) {

    @Override
    public String toString() {
      return "CoordenadaJson[***]";
    }
  }

  record SituacaoJson(boolean consentida, @Nullable String atualizadaEm) {}

  @GetMapping
  SituacaoJson consultar(@ContaAtual ContaId conta) {
    @Nullable Instant atualizadaEm = localizacoes.atualizadaEm(conta).orElse(null);
    return new SituacaoJson(
        atualizadaEm != null, atualizadaEm == null ? null : atualizadaEm.toString());
  }

  /** Campo ausente ou nulo é corpo inválido (400); fora do globo, {@code COORDENADA_INVALIDA}. */
  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void gravar(@ContaAtual ContaId conta, @RequestBody CoordenadaJson corpo) {
    Double latitude = corpo.latitude();
    Double longitude = corpo.longitude();
    if (latitude == null || longitude == null) {
      throw new ErroDeNegocio(
          CodigoWeb.CORPO_INVALIDO, "O corpo da requisição não está no formato esperado.");
    }
    localizacoes.consentirEAtualizar(conta, latitude, longitude);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revogar(@ContaAtual ContaId conta) {
    localizacoes.revogar(conta);
  }
}
