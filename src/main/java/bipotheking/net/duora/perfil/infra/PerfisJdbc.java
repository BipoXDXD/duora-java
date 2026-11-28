package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.Pronomes;
import bipotheking.net.duora.perfil.dominio.Apelido;
import bipotheking.net.duora.perfil.dominio.Bio;
import bipotheking.net.duora.perfil.dominio.DescricaoCurta;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Intencoes;
import bipotheking.net.duora.perfil.dominio.Perfil;
import bipotheking.net.duora.perfil.dominio.PronomesEscolhidos;
import bipotheking.net.duora.perfil.dominio.RepositorioDePerfis;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** A tabela {@code perfil} no Postgres. */
@Component
class PerfisJdbc implements RepositorioDePerfis {

  private final JdbcClient jdbc;

  PerfisJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Perfil> buscar(ContaId conta) {
    return jdbc.sql(
            """
            select apelido, pronomes, descricao_pronomes, quer_amizade, quer_namoro, bio, versao
            from perfil where conta_id = :conta
            """)
        .param("conta", conta.valor())
        .query((rs, linha) -> perfil(conta, rs))
        .optional();
  }

  @Override
  public boolean criar(Perfil perfil, Instant agora) {
    EdicaoDoPerfil dados = perfil.dados();
    OffsetDateTime instante = OffsetDateTime.ofInstant(agora, ZoneOffset.UTC);
    return jdbc.sql(
                """
                insert into perfil (conta_id, apelido, pronomes, descricao_pronomes, quer_amizade,
                    quer_namoro, bio, versao, criado_em, atualizado_em)
                values (:conta, :apelido, :pronomes, :descricao, :amizade, :namoro, :bio, :versao,
                    :agora, :agora)
                on conflict (conta_id) do nothing
                """)
            .param("conta", perfil.conta().valor())
            .param("apelido", dados.apelido().valor())
            .param("pronomes", pronomes(dados))
            .param("descricao", descricao(dados))
            .param("amizade", dados.intencoes().querAmizade())
            .param("namoro", dados.intencoes().querNamoro())
            .param("bio", dados.bio().valor())
            .param("versao", perfil.versao())
            .param("agora", instante)
            .update()
        == 1;
  }

  /**
   * Em {@code READ COMMITTED}, uma edição concorrente espera a primeira e reavalia o {@code where}
   * com a versão nova: sobe só uma das duas. Tirar o namoro com dados de namoro gravados no meio do
   * caminho viola {@code namoro_exige_intencao_fk}: também é outra edição que chegou antes.
   */
  @Override
  public Optional<Perfil> atualizar(Perfil perfil, Instant agora) {
    try {
      return trocar(perfil, agora);
    } catch (DataIntegrityViolationException violacao) {
      if (NamoroJdbc.violouIntencao(violacao)) {
        return Optional.empty();
      }
      throw violacao;
    }
  }

  private Optional<Perfil> trocar(Perfil perfil, Instant agora) {
    EdicaoDoPerfil dados = perfil.dados();
    int linhas =
        jdbc.sql(
                """
                update perfil set apelido = :apelido, pronomes = :pronomes,
                    descricao_pronomes = :descricao, quer_amizade = :amizade,
                    quer_namoro = :namoro, bio = :bio, versao = versao + 1,
                    atualizado_em = :agora
                where conta_id = :conta and versao = :versao
                """)
            .param("conta", perfil.conta().valor())
            .param("apelido", dados.apelido().valor())
            .param("pronomes", pronomes(dados))
            .param("descricao", descricao(dados))
            .param("amizade", dados.intencoes().querAmizade())
            .param("namoro", dados.intencoes().querNamoro())
            .param("bio", dados.bio().valor())
            .param("versao", perfil.versao())
            .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
            .update();
    if (linhas == 0) {
      return Optional.empty();
    }
    return Optional.of(new Perfil(perfil.conta(), dados, perfil.versao() + 1));
  }

  @Override
  public boolean existe(ContaId conta) {
    return jdbc.sql("select exists (select 1 from perfil where conta_id = :conta)")
        .param("conta", conta.valor())
        .query(Boolean.class)
        .single();
  }

  @Override
  public void trocarIntencoesPorAmizade(ContaId conta, Instant agora) {
    jdbc.sql(
            """
            update perfil set quer_amizade = true, quer_namoro = false, versao = versao + 1,
                atualizado_em = :agora
            where conta_id = :conta and quer_namoro
            """)
        .param("conta", conta.valor())
        .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
        .update();
  }

  private static Perfil perfil(ContaId conta, ResultSet rs) throws SQLException {
    String pronomes = rs.getString("pronomes");
    String descricao = rs.getString("descricao_pronomes");
    Optional<PronomesEscolhidos> escolhidos =
        pronomes == null
            ? Optional.empty()
            : Optional.of(
                new PronomesEscolhidos(
                    Pronomes.valueOf(pronomes),
                    Optional.ofNullable(descricao).map(DescricaoCurta::new)));
    Set<Intencao> intencoes = EnumSet.noneOf(Intencao.class);
    if (rs.getBoolean("quer_amizade")) {
      intencoes.add(Intencao.AMIZADE);
    }
    if (rs.getBoolean("quer_namoro")) {
      intencoes.add(Intencao.NAMORO);
    }
    EdicaoDoPerfil dados =
        new EdicaoDoPerfil(
            new Apelido(rs.getString("apelido")),
            escolhidos,
            new Intencoes(intencoes),
            new Bio(rs.getString("bio")));
    return new Perfil(conta, dados, rs.getLong("versao"));
  }

  private static @Nullable String pronomes(EdicaoDoPerfil dados) {
    return dados.pronomes().map(p -> p.pronomes().name()).orElse(null);
  }

  private static @Nullable String descricao(EdicaoDoPerfil dados) {
    return dados
        .pronomes()
        .flatMap(PronomesEscolhidos::descricao)
        .map(DescricaoCurta::valor)
        .orElse(null);
  }
}
