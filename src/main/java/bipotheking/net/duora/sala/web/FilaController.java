package bipotheking.net.duora.sala.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.CodigoLocalizacao;
import bipotheking.net.duora.sala.CodigoSala;
import bipotheking.net.duora.sala.Coordenadas;
import bipotheking.net.duora.sala.EstadoNaFila;
import bipotheking.net.duora.sala.Fila;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A fila da conta da sessão. A coordenada, se vier, só passa adiante: nenhum log, exceção ou corpo
 * de resposta carrega os números.
 */
@RestController
@RequestMapping("/api/fila")
class FilaController {

  private final Fila fila;

  FilaController(Fila fila) {
    this.fila = fila;
  }

  /**
   * {@code {}} ou latitude e longitude juntas. Sem {@code toString} gerado, que imprimiria os
   * números.
   */
  record EntradaJson(@Nullable Double latitude, @Nullable Double longitude) {

    @Override
    public String toString() {
      return "EntradaJson[***]";
    }
  }

  /** Só uma das duas é {@code COORDENADA_INVALIDA} (422), como a coordenada fora do globo. */
  @PostMapping
  EstadoNaFila entrar(@ContaAtual ContaId conta, @RequestBody EntradaJson corpo) {
    Double latitude = corpo.latitude();
    Double longitude = corpo.longitude();
    if ((latitude == null) != (longitude == null)) {
      throw CodigoLocalizacao.COORDENADA_INVALIDA.erro();
    }
    Optional<Coordenadas> coordenadas =
        latitude == null || longitude == null
            ? Optional.empty()
            : Optional.of(new Coordenadas(latitude, longitude));
    return fila.entrar(conta, coordenadas);
  }

  @GetMapping
  EstadoNaFila estado(@ContaAtual ContaId conta) {
    return fila.estado(conta).orElseThrow(CodigoSala.FORA_DA_FILA::erro);
  }

  /** Idempotente: fora da fila também é 204. */
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void desistir(@ContaAtual ContaId conta) {
    fila.desistir(conta);
  }
}
