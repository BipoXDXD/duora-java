package bipotheking.net.duora.perfil.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.FotoDoTitular;
import bipotheking.net.duora.perfil.FotosDoPerfil;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.Pendencia;
import bipotheking.net.duora.perfil.Perfis;
import bipotheking.net.duora.perfil.Perfis.Prontidao;
import bipotheking.net.duora.perfil.Pronomes;
import bipotheking.net.duora.perfil.ServicoDePerfil;
import bipotheking.net.duora.perfil.dominio.Apelido;
import bipotheking.net.duora.perfil.dominio.Bio;
import bipotheking.net.duora.perfil.dominio.DescricaoCurta;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Intencoes;
import bipotheking.net.duora.perfil.dominio.Perfil;
import bipotheking.net.duora.perfil.dominio.PronomesEscolhidos;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** O perfil da conta da sessão. O {@code Cache-Control: no-store} vem do filtro de cabeçalhos. */
@RestController
@RequestMapping("/api/perfil")
class PerfilController {

  private final ServicoDePerfil servico;
  private final Perfis perfis;
  private final FotosDoPerfil fotos;

  PerfilController(ServicoDePerfil servico, Perfis perfis, FotosDoPerfil fotos) {
    this.servico = servico;
    this.perfis = perfis;
    this.fotos = fotos;
  }

  /** Campo ausente chega nulo; o domínio recusa com o código do contrato. */
  record EdicaoDoPerfilJson(
      @Nullable Long versao,
      @Nullable String apelido,
      @Nullable Pronomes pronomes,
      @Nullable String descricaoPronomes,
      @Nullable List<Intencao> intencoes,
      @Nullable String bio) {}

  /**
   * O perfil como o front o lê. Sem perfil: nulos, {@code []}, {@code ""}, {@code false}, {@code
   * false} e {@code ["PERFIL"]}; a foto vem de qualquer jeito.
   */
  record PerfilJson(
      @Nullable Long versao,
      @Nullable String apelido,
      @Nullable Pronomes pronomes,
      @Nullable String descricaoPronomes,
      List<Intencao> intencoes,
      String bio,
      boolean pausada,
      boolean prontoParaFila,
      List<Pendencia> pendencias,
      FotoDoTitular foto) {

    static PerfilJson de(@Nullable Perfil perfil, Prontidao prontidao, FotoDoTitular foto) {
      List<Pendencia> pendencias = List.copyOf(prontidao.pendencias());
      if (perfil == null) {
        return new PerfilJson(
            null, null, null, null, List.of(), "", false, false, pendencias, foto);
      }
      EdicaoDoPerfil dados = perfil.dados();
      Optional<PronomesEscolhidos> pronomes = dados.pronomes();
      return new PerfilJson(
          perfil.versao(),
          dados.apelido().valor(),
          pronomes.map(PronomesEscolhidos::pronomes).orElse(null),
          pronomes.flatMap(PronomesEscolhidos::descricao).map(DescricaoCurta::valor).orElse(null),
          List.copyOf(dados.intencoes().valores()),
          dados.bio().valor(),
          prontidao.pausada(),
          prontidao.prontoParaFila(),
          pendencias,
          foto);
    }
  }

  @GetMapping
  PerfilJson consultar(@ContaAtual ContaId conta) {
    return corpo(conta, servico.meu(conta).orElse(null));
  }

  @PutMapping
  PerfilJson salvar(@ContaAtual ContaId conta, @RequestBody EdicaoDoPerfilJson corpo) {
    EdicaoDoPerfil dados =
        new EdicaoDoPerfil(
            Apelido.de(corpo.apelido()),
            PronomesEscolhidos.de(corpo.pronomes(), corpo.descricaoPronomes()),
            Intencoes.de(corpo.intencoes()),
            Bio.de(corpo.bio()));
    return corpo(conta, servico.salvar(conta, corpo.versao(), dados));
  }

  private PerfilJson corpo(ContaId conta, @Nullable Perfil perfil) {
    return PerfilJson.de(perfil, perfis.prontidao(conta), fotos.doTitular(conta));
  }
}
