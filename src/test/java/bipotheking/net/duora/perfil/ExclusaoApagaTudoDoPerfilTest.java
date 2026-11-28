package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import bipotheking.net.duora.perfil.dominio.Apelido;
import bipotheking.net.duora.perfil.dominio.Bio;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Intencoes;
import bipotheking.net.duora.perfil.dominio.Perfil;
import bipotheking.net.duora.perfil.dominio.PronomesEscolhidos;
import bipotheking.net.duora.web.ArmazemDeImagens;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.Variante;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Guarda da exclusão (foco 2 da etapa): a conta vira lápide e a linha dela não sai, então nenhum
 * {@code on delete cascade} a partir de {@code conta} dispara. Cada tabela do perfil precisa sair
 * por um gancho, e os arquivos das fotos também. As tabelas vêm do {@code information_schema}: uma
 * tabela nova com {@code conta_id} entra no teste sozinha, até alguém a declarar de outro módulo.
 */
class ExclusaoApagaTudoDoPerfilTest extends BancoDeTeste {

  private static final String CATEGORIA = "perfil";

  /** Tabelas com {@code conta_id} de outros módulos, cada um com a sua guarda de exclusão. */
  private static final Set<String> DE_OUTROS_MODULOS =
      Set.of(
          "sessao",
          "aceite_documento",
          "registro_acesso",
          "uso_conta_nova",
          "verificacao_idade",
          "tentativa_verificacao",
          "evento_conta",
          "cenario_jogado");

  @Autowired private ExclusaoDeConta exclusao;
  @Autowired private ContasDeTeste contas;
  @Autowired private PerfisDeTeste perfisDeTeste;
  @Autowired private ServicoDePerfil perfis;
  @Autowired private ServicoDeOrientacao orientacoes;
  @Autowired private FotosDeTeste fotosDeTeste;
  @Autowired private ArmazemDeImagens armazem;

  @Test
  void nenhumaLinhaNemArquivoSobra() {
    ContaId conta = contas.criarAtiva().id();
    perfisDeTeste.definir(
        conta,
        "Lia",
        new Preferencias(
            EnumSet.of(Intencao.AMIZADE, Intencao.NAMORO),
            new FaixaEtaria(25, 40),
            Raio.ATE_20_KM,
            true,
            Optional.of(Genero.MULHER),
            EnumSet.of(Genero.HOMEM)),
        Optional.of(CelulaGeohash.de(-23.55, -46.63)));
    perfis.salvar(
        conta,
        perfis.meu(conta).map(Perfil::versao).orElseThrow(),
        new EdicaoDoPerfil(
            Apelido.de("Lia"),
            PronomesEscolhidos.de(Pronomes.ELU_DELU, null),
            new Intencoes(EnumSet.of(Intencao.AMIZADE, Intencao.NAMORO)),
            Bio.de("")));
    orientacoes.salvar(conta, Orientacao.BISSEXUAL, null, true, true);
    FotoId aprovada = fotosDeTeste.enviarAprovada(conta);
    FotoId pendente = fotosDeTeste.enviar(conta);
    assertThat(linhasPorTabela(conta))
        .as("antes da exclusão")
        .allSatisfy((tabela, linhas) -> assertThat(linhas).as(tabela).isPositive());

    exclusao.excluir(conta, true);

    assertThat(linhasPorTabela(conta))
        .allSatisfy((tabela, linhas) -> assertThat(linhas).as(tabela).isZero());
    for (FotoId foto : Set.of(aprovada, pendente)) {
      for (Variante variante : Variante.values()) {
        assertThat(armazem.ler(CATEGORIA, foto.valor(), variante))
            .as("%s %s", foto, variante)
            .isEmpty();
      }
    }
    assertThat(contaExiste(conta)).as("lápide").isTrue();
  }

  @Test
  void descobreAsTabelasDoPerfil() {
    assertThat(tabelasDoPerfil())
        .contains("perfil", "preferencias", "namoro", "orientacao", "localizacao", "foto");
  }

  private Map<String, Long> linhasPorTabela(ContaId conta) {
    return tabelasDoPerfil().stream()
        .collect(Collectors.toMap(tabela -> tabela, tabela -> linhas(tabela, conta)));
  }

  /** Toda tabela do esquema com {@code conta_id}, menos as de outros módulos. */
  private List<String> tabelasDoPerfil() {
    return jdbc
        .sql(
            """
            select table_name from information_schema.columns
            where table_schema = current_schema() and column_name = 'conta_id'
            order by table_name
            """)
        .query(String.class)
        .list()
        .stream()
        .filter(tabela -> !DE_OUTROS_MODULOS.contains(tabela))
        .toList();
  }

  /** O nome da tabela vem do catálogo do próprio banco, nunca de fora. */
  private long linhas(String tabela, ContaId conta) {
    return jdbc.sql("select count(*) from " + tabela + " where conta_id = :conta")
        .param("conta", conta.valor())
        .query(Long.class)
        .single();
  }

  private boolean contaExiste(ContaId conta) {
    return jdbc.sql("select count(*) from conta where id = :conta")
            .param("conta", conta.valor())
            .query(Long.class)
            .single()
        == 1;
  }
}
