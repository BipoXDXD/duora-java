package bipotheking.net.duora.pareamento.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.MotivoVeto;
import bipotheking.net.duora.pareamento.ParDeContas;
import bipotheking.net.duora.pareamento.dominio.RepositorioDeVetos;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@link RepositorioDeVetos} sobre a tabela {@code par_vetado} (V51). */
@Component
class VetosJdbc implements RepositorioDeVetos {

  private final JdbcClient jdbc;

  VetosJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void vetar(ParDeContas par, MotivoVeto motivo, Instant agora) {
    jdbc.sql(
            """
            insert into par_vetado (conta_menor, conta_maior, motivo, vetado_em)
            values (:menor, :maior, :motivo, :agora)
            on conflict (conta_menor, conta_maior) do update
                set motivo = excluded.motivo, vetado_em = excluded.vetado_em
                where par_vetado.motivo = 'CONECTADOS' and excluded.motivo <> 'CONECTADOS'
            """)
        .param("menor", par.menor().valor())
        .param("maior", par.maior().valor())
        .param("motivo", motivo.name())
        .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
        .update();
  }

  @Override
  public Optional<MotivoVeto> motivo(ParDeContas par) {
    return jdbc.sql(
            "select motivo from par_vetado where conta_menor = :menor and conta_maior = :maior")
        .param("menor", par.menor().valor())
        .param("maior", par.maior().valor())
        .query((rs, linha) -> MotivoVeto.valueOf(rs.getString("motivo")))
        .optional();
  }

  @Override
  public Set<ParDeContas> vetadosEntre(Collection<ContaId> contas) {
    UUID[] ids = contas.stream().map(ContaId::valor).toArray(UUID[]::new);
    return new HashSet<>(
        jdbc.sql(
                """
                select conta_menor, conta_maior from par_vetado
                where conta_menor = any(:contas) and conta_maior = any(:contas)
                """)
            .param("contas", ids)
            .query((rs, linha) ->
                new ParDeContas(
                    new ContaId(rs.getObject("conta_menor", UUID.class)),
                    new ContaId(rs.getObject("conta_maior", UUID.class))))
            .list());
  }
}
