package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.DocumentoLegal;
import bipotheking.net.duora.conta.VersaoDeDocumento;
import bipotheking.net.duora.conta.dominio.RepositorioDeAceites;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Aceites dos documentos legais no Postgres. */
@Component
class AceitesJdbc implements RepositorioDeAceites {

  private final JdbcClient jdbc;

  AceitesJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void gravar(
      ContaId conta, DocumentoLegal documento, VersaoDeDocumento versao, Instant agora) {
    jdbc.sql(
            """
            insert into aceite_documento (conta_id, documento, versao, resumo_sha256, aceito_em)
            values (:conta, :documento, :versao, :resumo, :agora)
            """)
        .param("conta", conta.valor())
        .param("documento", documento.name())
        .param("versao", versao.versao())
        .param("resumo", versao.resumoSha256())
        .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
        .update();
  }

  @Override
  public int apagarDaConta(ContaId conta) {
    return jdbc.sql("delete from aceite_documento where conta_id = :conta")
        .param("conta", conta.valor())
        .update();
  }
}
