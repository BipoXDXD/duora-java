package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.nucleo.TipoDeErro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CodigoPerfilTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          APELIDO_INVALIDO         | REGRA_DE_NEGOCIO | O apelido precisa ter de 1 a 20 caracteres.
          BIO_INVALIDA             | REGRA_DE_NEGOCIO | A bio pode ter até 280 caracteres.
          INTENCAO_INVALIDA        | REGRA_DE_NEGOCIO | Escolha amizade, namoro ou os dois.
          PERFIL_NECESSARIO        | REGRA_DE_NEGOCIO | Salve seu perfil antes de continuar.
          FAIXA_ETARIA_INVALIDA    | REGRA_DE_NEGOCIO | Escolha idades de 18 a 99, com a primeira até a segunda.
          LOCALIZACAO_NECESSARIA   | REGRA_DE_NEGOCIO | Ative a localização para usar um raio.
          INTENCAO_SEM_NAMORO      | REGRA_DE_NEGOCIO | Marque Namoro em Como você quer usar o Duora para preencher estes dados.
          CONSENTIMENTO_NECESSARIO | REGRA_DE_NEGOCIO | Marque a caixa de consentimento para salvar estes dados.
          GENERO_INVALIDO          | REGRA_DE_NEGOCIO | Escolha seu gênero e ao menos uma opção de quem conhecer. Em Prefiro descrever, use de 1 a 40 caracteres.
          IDENTIDADE_INVALIDA      | REGRA_DE_NEGOCIO | Em Prefiro descrever, use de 1 a 40 caracteres na orientação e de 1 a 20 nos pronomes.
          VERSAO_OBSOLETA          | CONFLITO         | Seu perfil mudou enquanto você editava. Confira e salve de novo.
          PERFIL_INEXISTENTE       | NAO_ENCONTRADO   | Não encontramos este perfil.
          """)
  void codigoTemTipoEMensagemDoContrato(CodigoPerfil codigo, TipoDeErro tipo, String mensagem) {
    assertThat(codigo.tipo()).isEqualTo(tipo);
    assertThat(codigo.mensagem()).isEqualTo(mensagem);
  }

  @Test
  void contratoTemDozeCodigos() {
    assertThat(CodigoPerfil.values()).hasSize(12);
  }
}
