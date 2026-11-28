package bipotheking.net.duora.perfil.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.AcessoAFoto;
import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.NivelDeAcesso;
import bipotheking.net.duora.perfil.PerfilPublico;
import bipotheking.net.duora.perfil.Perfis;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * O perfil público de outra conta. Quem pode ver decide o mesmo {@link AcessoAFoto} das fotos; toda
 * recusa é o mesmo 404 {@code PERFIL_INEXISTENTE}: a conta que não existe, a que não está ativa, a
 * sem perfil e a que o acesso esconde. O id malformado cai na conversão do Spring: 400 {@code
 * REQUISICAO_INVALIDA}. O {@code Cache-Control: no-store} vem do filtro de cabeçalhos.
 */
@RestController
class PerfisController {

  private final Perfis perfis;
  private final AcessoAFoto acesso;

  PerfisController(Perfis perfis, AcessoAFoto acesso) {
    this.perfis = perfis;
    this.acesso = acesso;
  }

  /** Exatamente as quatro chaves do contrato; nada de bio, idade, intenções ou dados sensíveis. */
  record PerfilPublicoJson(
      String apelido, @Nullable String foto, @Nullable String pronomes, boolean verificado) {

    static PerfilPublicoJson de(PerfilPublico perfil) {
      return new PerfilPublicoJson(
          perfil.apelido(),
          perfil.fotoBorradaUrl().orElse(null),
          perfil.pronomes().orElse(null),
          perfil.verificado());
    }
  }

  @GetMapping("/api/perfis/{contaId}")
  PerfilPublicoJson ver(@ContaAtual ContaId quemVe, @PathVariable UUID contaId) {
    ContaId titular = new ContaId(contaId);
    if (acesso.nivel(quemVe, titular) == NivelDeAcesso.NENHUM) {
      throw CodigoPerfil.PERFIL_INEXISTENTE.erro();
    }
    return perfis
        .publico(titular)
        .map(PerfilPublicoJson::de)
        .orElseThrow(CodigoPerfil.PERFIL_INEXISTENTE::erro);
  }
}
