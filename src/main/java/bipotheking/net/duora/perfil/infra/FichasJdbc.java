package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.FaixaEtaria;
import bipotheking.net.duora.perfil.Raio;
import bipotheking.net.duora.perfil.dominio.AjustesDeBusca;
import bipotheking.net.duora.perfil.dominio.FichaDoPerfil;
import bipotheking.net.duora.perfil.dominio.LeituraDePerfis;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O perfil inteiro de uma conta numa consulta: {@code perfil} com {@code preferencias}, {@code
 * namoro}, {@code orientacao} e {@code localizacao} por junção à esquerda. Cada tabela juntada tem
 * {@code conta_id} como chave primária, então sai no máximo uma linha. As linhas de cada tabela são
 * lidas pelos mesmos leitores dos repositórios dela.
 */
@Component
class FichasJdbc implements LeituraDePerfis {

  private final JdbcClient jdbc;

  FichasJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<FichaDoPerfil> ficha(ContaId conta) {
    return jdbc.sql(
            """
            select p.apelido, p.pronomes, p.descricao_pronomes, p.quer_amizade, p.quer_namoro,
                p.bio, p.versao, p.pausada_em is not null as pausada,
                pr.idade_minima, pr.idade_maxima, pr.raio, pr.raio_obrigatorio,
                n.genero, n.descricao_genero, n.interesses,
                o.orientacao, o.descricao, o.mostrar, o.consentida_em,
                l.celula
            from perfil p
            left join preferencias pr on pr.conta_id = p.conta_id
            left join namoro n on n.conta_id = p.conta_id
            left join orientacao o on o.conta_id = p.conta_id
            left join localizacao l on l.conta_id = p.conta_id
            where p.conta_id = :conta
            """)
        .param("conta", conta.valor())
        .query((rs, linha) -> ficha(conta, rs))
        .optional();
  }

  private static FichaDoPerfil ficha(ContaId conta, ResultSet rs) throws SQLException {
    String raio = rs.getString("raio");
    AjustesDeBusca ajustes =
        raio == null
            ? AjustesDeBusca.PADRAO
            : new AjustesDeBusca(
                new FaixaEtaria(rs.getInt("idade_minima"), rs.getInt("idade_maxima")),
                Raio.valueOf(raio),
                rs.getBoolean("raio_obrigatorio"));
    String celula = rs.getString("celula");
    return new FichaDoPerfil(
        PerfisJdbc.perfil(conta, rs),
        ajustes,
        rs.getString("genero") == null ? Optional.empty() : Optional.of(NamoroJdbc.dados(rs)),
        rs.getString("orientacao") == null
            ? Optional.empty()
            : Optional.of(OrientacaoJdbc.dados(rs)),
        Optional.ofNullable(celula).map(CelulaGeohash::new),
        rs.getBoolean("pausada"));
  }
}
