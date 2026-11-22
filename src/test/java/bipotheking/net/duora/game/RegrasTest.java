package bipotheking.net.duora.game;

import static bipotheking.net.duora.game.Assento.A;
import static bipotheking.net.duora.game.Assento.B;
import static bipotheking.net.duora.game.Terraco.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class RegrasTest {

  private static final Instant AGORA = Instant.parse("2026-11-20T21:00:00Z");

  @Test
  void parceiroEntrandoPassaParaMontagemSemMudarVersao() {
    Estado estado = aceito(Regras.parceiroEntrou(CENARIO, Estado.inicial(), AGORA));

    assertThat(estado.fase()).isInstanceOf(Fase.Montando.class);
    assertThat(estado.versao()).isZero();
  }

  @Test
  void segundoParceiroNaoEntra() {
    assertThat(Regras.parceiroEntrou(CENARIO, montando(), AGORA))
        .isEqualTo(recusa(Recusa.FASE_INVALIDA));
  }

  @ParameterizedTest
  @CsvSource({
    "TELESCOPIO, L1",
    "TELESCOPIO, R2",
    "BANCO, L2",
    "BANCO, R1",
    "MANTA, C1",
    "RADIO, C2",
    "LANTERNA, C1",
    "GARRAFA, C2"
  })
  void pecaForaDaSuaZonaEhRecusada(
      @ConvertWith(PecaDoTerraco.class) Peca peca, @ConvertWith(LugarDoTerraco.class) Lugar lugar) {
    Decisao decisao = Regras.colocar(CENARIO, montando(), peca.dono(), peca, Optional.of(lugar), 0);

    assertThat(decisao).isEqualTo(recusa(Recusa.LUGAR_INVALIDO));
  }

  @ParameterizedTest
  @CsvSource({"TELESCOPIO, C1", "TELESCOPIO, C2", "BANCO, C2", "MANTA, L1", "GARRAFA, R2"})
  void primeiraPecaNaSuaZonaEhAceitaEIncrementaVersao(
      @ConvertWith(PecaDoTerraco.class) Peca peca, @ConvertWith(LugarDoTerraco.class) Lugar lugar) {
    Estado estado =
        aceito(Regras.colocar(CENARIO, montando(), peca.dono(), peca, Optional.of(lugar), 0));

    assertThat(estado.composicao().lugarDe(peca)).contains(lugar);
    assertThat(estado.versao()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({"A, BANCO", "A, LANTERNA", "B, TELESCOPIO", "B, RADIO"})
  void pecaDoParceiroNaoPodeSerMexida(Assento quem, @ConvertWith(PecaDoTerraco.class) Peca peca) {
    Lugar lugar = CENARIO.da(peca.zona()).get(0);

    Decisao decisao = Regras.colocar(CENARIO, montando(), quem, peca, Optional.of(lugar), 0);

    assertThat(decisao).isEqualTo(recusa(Recusa.PECA_ALHEIA));
  }

  @Test
  void lugarDoParceiroEstaOcupadoENaoHaDeslocamento() {
    Estado estado = colocar(montando(), BANCO, C1);

    Decisao decisao =
        Regras.colocar(CENARIO, estado, A, TELESCOPIO, Optional.of(C1), estado.versao());

    assertThat(decisao).isEqualTo(recusa(Recusa.LUGAR_OCUPADO));
  }

  @Test
  void lugarOcupadoTemPrioridadeSobreVersaoObsoleta() {
    Estado estado = colocar(montando(), BANCO, C1);

    Decisao decisao = Regras.colocar(CENARIO, estado, A, TELESCOPIO, Optional.of(C1), 0);

    assertThat(decisao).isEqualTo(recusa(Recusa.LUGAR_OCUPADO));
  }

  @Test
  void versaoObsoletaRecusaMudanca() {
    Estado estado = colocar(montando(), BANCO, C1);

    Decisao decisao = Regras.colocar(CENARIO, estado, A, TELESCOPIO, Optional.of(C2), 0);

    assertThat(decisao).isEqualTo(recusa(Recusa.VERSAO_OBSOLETA));
  }

  @Test
  void moverPecaPropriaParaOutroLugarLivre() {
    Estado estado = colocar(montando(), TELESCOPIO, C1);

    Estado movido = aceito(Regras.colocar(CENARIO, estado, A, TELESCOPIO, Optional.of(C2), 1));

    assertThat(movido.composicao().lugarDe(TELESCOPIO)).contains(C2);
    assertThat(movido.composicao().ocupante(C1)).isEmpty();
    assertThat(movido.versao()).isEqualTo(2);
  }

  @Test
  void outraSecundariaTrocaAAtualAtomicamente() {
    Estado estado = colocar(colocar(montando(), MANTA, L1), LANTERNA, R1);

    Estado trocado = aceito(Regras.colocar(CENARIO, estado, A, RADIO, Optional.of(L2), 2));

    assertThat(trocado.composicao().lugarDe(RADIO)).contains(L2);
    assertThat(trocado.composicao().colocada(MANTA)).isFalse();
    assertThat(trocado.versao()).isEqualTo(3);
  }

  @Test
  void outraSecundariaPodeOcuparOLugarDaQueSai() {
    Estado estado = colocar(colocar(montando(), MANTA, L1), LANTERNA, R1);

    Estado trocado = aceito(Regras.colocar(CENARIO, estado, A, RADIO, Optional.of(L1), 2));

    assertThat(trocado.composicao().ocupante(L1)).contains(RADIO);
    assertThat(trocado.composicao().colocada(MANTA)).isFalse();
  }

  @Test
  void colocarPreservaAInstanciaDaFase() {
    Estado antes = montando();

    Estado depois = colocar(antes, TELESCOPIO, C1);

    assertThat(depois.fase()).isSameAs(antes.fase());
  }

  @Test
  void retirarDevolvePecaAoInventario() {
    Estado estado = colocar(montando(), TELESCOPIO, C1);

    Estado retirado = aceito(Regras.colocar(CENARIO, estado, A, TELESCOPIO, Optional.empty(), 1));

    assertThat(retirado.composicao().colocada(TELESCOPIO)).isFalse();
    assertThat(retirado.versao()).isEqualTo(2);
  }

  @ParameterizedTest
  @MethodSource("fasesForaDaMontagem")
  void soSeColocaDuranteAMontagem(Fase fase) {
    Estado estado = new Estado(fase, 4, completa().composicao(), Set.of(), Optional.<Sinal>empty());

    Decisao decisao = Regras.colocar(CENARIO, estado, A, TELESCOPIO, Optional.of(C2), 4);

    assertThat(decisao).isEqualTo(recusa(Recusa.FASE_INVALIDA));
  }

  @Test
  void retirarEmRevelacaoEhRecusado() {
    Estado revelado = revelado();

    Decisao decisao =
        Regras.colocar(CENARIO, revelado, B, LANTERNA, Optional.empty(), revelado.versao());

    assertThat(decisao).isEqualTo(recusa(Recusa.FASE_INVALIDA));
  }

  @Test
  void pedirOLugarAtualNaoMudaNadaMesmoComVersaoAntiga() {
    Estado estado = colocar(colocar(montando(), TELESCOPIO, C1), BANCO, C2);

    Decisao decisao = Regras.colocar(CENARIO, estado, A, TELESCOPIO, Optional.of(C1), 0);

    assertThat(decisao).isEqualTo(new Decisao.Aceita(estado));
  }

  @Test
  void retirarPecaQueJaEstaNoInventarioNaoMudaNada() {
    Estado estado = montando();

    assertThat(Regras.colocar(CENARIO, estado, A, MANTA, Optional.empty(), 9))
        .isEqualTo(new Decisao.Aceita(estado));
  }

  @Test
  void repetirOProntoAtualNaoMudaNadaMesmoComVersaoAntiga() {
    Estado estado = confirmar(completa(), A);

    assertThat(Regras.confirmar(CENARIO, estado, A, true, 0)).isEqualTo(new Decisao.Aceita(estado));
  }

  @Test
  void pedirOMesmoLugarDuranteARevelacaoEhAceitoSemMudanca() {
    Estado revelado = revelado();

    assertThat(Regras.colocar(CENARIO, revelado, A, TELESCOPIO, Optional.of(C1), 0))
        .isEqualTo(new Decisao.Aceita(revelado));
  }

  @Test
  void segundaPecaAntesDoParceiroColocarAPrimeiraEhRecusada() {
    Estado estado = colocar(montando(), TELESCOPIO, C1);

    Decisao decisao = Regras.colocar(CENARIO, estado, A, MANTA, Optional.of(L1), 1);

    assertThat(decisao).isEqualTo(recusa(Recusa.ESPERE_PRIMEIRA_PECA));
  }

  @Test
  void segundaPecaLiberadaQuandoOParceiroColocaAPrimeira() {
    Estado estado = colocar(colocar(montando(), TELESCOPIO, C1), BANCO, C2);

    Estado comManta = aceito(Regras.colocar(CENARIO, estado, A, MANTA, Optional.of(L1), 2));

    assertThat(comManta.composicao().colocadas(A)).isEqualTo(2);
  }

  @Test
  void moverAUnicaPecaAntesDoParceiroEhPermitido() {
    Estado estado = colocar(montando(), TELESCOPIO, C1);

    assertThat(Regras.colocar(CENARIO, estado, A, TELESCOPIO, Optional.of(C2), 1))
        .isInstanceOf(Decisao.Aceita.class);
  }

  @Test
  void moverPecaJaColocadaEhPermitidoMesmoSeOParceiroRetirouTudo() {
    Estado estado = colocar(colocar(colocar(montando(), TELESCOPIO, C1), BANCO, C2), MANTA, L1);
    Estado semBanco = aceito(Regras.colocar(CENARIO, estado, B, BANCO, Optional.empty(), 3));

    Decisao decisao = Regras.colocar(CENARIO, semBanco, A, MANTA, Optional.of(R2), 4);

    assertThat(decisao).isInstanceOf(Decisao.Aceita.class);
  }

  @Test
  void bloqueioAvisaSoAPecaQueAindaNaoFoiColocada() {
    Estado estado = colocar(montando(), TELESCOPIO, C1);

    assertThat(Regras.bloqueadaPeloPortao(CENARIO, estado, MANTA)).isTrue();
    assertThat(Regras.bloqueadaPeloPortao(CENARIO, estado, TELESCOPIO)).isFalse();
    assertThat(Regras.bloqueadaPeloPortao(CENARIO, estado, BANCO)).isFalse();
  }

  @Test
  void composicaoVaziaFaltaQuatro() {
    assertThat(CENARIO.faltam(Composicao.vazia())).isEqualTo(4);
    assertThat(CENARIO.completa(Composicao.vazia())).isFalse();
  }

  @Test
  void faltaUmaQuandoSoUmaSecundariaEstaFora() {
    Estado estado = colocar(colocar(colocar(montando(), TELESCOPIO, C1), BANCO, C2), MANTA, L1);

    assertThat(CENARIO.faltam(estado.composicao())).isEqualTo(1);
    assertThat(CENARIO.completa(estado.composicao())).isFalse();
  }

  @Test
  void obrigatoriasEUmaSecundariaDeCadaCompletam() {
    assertThat(CENARIO.faltam(completa().composicao())).isZero();
    assertThat(CENARIO.completa(completa().composicao())).isTrue();
  }

  @Test
  void confirmarIncompletaEhRecusado() {
    Estado estado = colocar(colocar(montando(), TELESCOPIO, C1), BANCO, C2);

    assertThat(Regras.confirmar(CENARIO, estado, A, true, 2)).isEqualTo(recusa(Recusa.INCOMPLETA));
  }

  @Test
  void confirmacaoFicaVinculadaAVersao() {
    Estado estado = completa();

    Decisao decisao = Regras.confirmar(CENARIO, estado, A, true, estado.versao() - 1);

    assertThat(decisao).isEqualTo(recusa(Recusa.VERSAO_OBSOLETA));
  }

  @Test
  void umaConfirmacaoNaoRevelaNemMudaVersao() {
    Estado estado = completa();

    Estado confirmado = confirmar(estado, A);

    assertThat(confirmado.fase()).isInstanceOf(Fase.Montando.class);
    assertThat(confirmado.prontos()).containsExactly(A);
    assertThat(confirmado.versao()).isEqualTo(estado.versao());
  }

  @Test
  void confirmarSemRevelarPreservaAInstanciaDaFase() {
    Estado antes = completa();

    Estado depois = confirmar(antes, A);

    assertThat(depois.fase()).isSameAs(antes.fase());
  }

  @Test
  void duasConfirmacoesNaMesmaVersaoRevelam() {
    Estado estado = confirmar(confirmar(completa(), A), B);

    assertThat(estado.fase()).isInstanceOf(Fase.Revelado.class);
    assertThat(estado.prontos()).containsExactlyInAnyOrder(A, B);
  }

  @Test
  void mudancaAceitaZeraAsConfirmacoes() {
    Estado confirmado = confirmar(completa(), A);

    Estado movido = colocar(confirmado, MANTA, L2);

    assertThat(movido.prontos()).isEmpty();
    assertThat(movido.versao()).isEqualTo(confirmado.versao() + 1);
  }

  @Test
  void confirmacaoAnteriorAMudancaNaoValeMais() {
    Estado estado = completa();
    long versaoConfirmada = estado.versao();
    Estado movido = colocar(confirmar(estado, A), MANTA, L2);

    Decisao decisao = Regras.confirmar(CENARIO, movido, B, true, versaoConfirmada);

    assertThat(decisao).isEqualTo(recusa(Recusa.VERSAO_OBSOLETA));
  }

  @Test
  void desfazerConfirmacao() {
    Estado confirmado = confirmar(completa(), A);

    Estado desfeito = aceito(Regras.confirmar(CENARIO, confirmado, A, false, confirmado.versao()));

    assertThat(desfeito.prontos()).isEmpty();
    assertThat(desfeito.fase()).isInstanceOf(Fase.Montando.class);
  }

  @Test
  void desfazerConfirmacaoNaRevelacaoEhRecusado() {
    Estado revelado = revelado();

    assertThat(Regras.confirmar(CENARIO, revelado, A, false, revelado.versao()))
        .isEqualTo(recusa(Recusa.FASE_INVALIDA));
  }

  @Test
  void reveladoCongelaAComposicao() {
    Estado revelado = revelado();

    Decisao decisao =
        Regras.colocar(CENARIO, revelado, A, TELESCOPIO, Optional.of(C2), revelado.versao());

    assertThat(decisao).isEqualTo(recusa(Recusa.FASE_INVALIDA));
  }

  @Test
  void resultadoTrazSecundariasELadoDoTelescopio() {
    assertThat(Regras.resultado(CENARIO, revelado()).orElseThrow().valores())
        .containsExactly(
            entry("secundariaA", "manta"),
            entry("secundariaB", "lanterna"),
            entry("ladoTelescopio", "esquerdo"));
  }

  @Test
  void telescopioEmC2ApontaParaADireita() {
    Estado estado = colocar(colocar(colocar(montando(), BANCO, C1), TELESCOPIO, C2), MANTA, L1);
    estado = colocar(estado, GARRAFA, R2);
    Estado revelado = confirmar(confirmar(estado, A), B);

    assertThat(Regras.resultado(CENARIO, revelado).orElseThrow().valores())
        .containsExactly(
            entry("secundariaA", "manta"),
            entry("secundariaB", "garrafa"),
            entry("ladoTelescopio", "direito"));
  }

  @Test
  void semResultadoForaDaRevelacao() {
    assertThat(Regras.resultado(CENARIO, completa())).isEmpty();
  }

  @Test
  void pedirAjusteGravaOPedidoSemMudarAFase() {
    Estado revelado = revelado();

    Estado pedido = aceito(Regras.pedirAjuste(CENARIO, revelado, A));

    assertThat(pedido.fase()).isEqualTo(new Fase.Revelado(Optional.of(A)));
    assertThat(pedido.versao()).isEqualTo(revelado.versao());
    assertThat(pedido.prontos()).containsExactlyInAnyOrder(A, B);
  }

  @Test
  void pedirDeNovoNaoMudaNada() {
    Estado pedido = aceito(Regras.pedirAjuste(CENARIO, revelado(), A));

    assertThat(Regras.pedirAjuste(CENARIO, pedido, A)).isEqualTo(new Decisao.Aceita(pedido));
  }

  @Test
  void aceiteDoOutroVoltaAMontagemComVersaoNova() {
    Estado pedido = aceito(Regras.pedirAjuste(CENARIO, revelado(), A));

    Estado ajustado = aceito(Regras.pedirAjuste(CENARIO, pedido, B));

    assertThat(ajustado.fase()).isInstanceOf(Fase.Montando.class);
    assertThat(ajustado.versao()).isEqualTo(pedido.versao() + 1);
    assertThat(ajustado.prontos()).isEmpty();
    assertThat(ajustado.composicao()).isEqualTo(pedido.composicao());
  }

  @Test
  void aceiteDoAjusteLimpaOSinal() {
    Estado sinalizado = aceito(Regras.sinalizar(CENARIO, completa(), A, LANTERNA, R2, AGORA));
    Estado revelado = confirmar(confirmar(sinalizado, A), B);
    Estado pedido = aceito(Regras.pedirAjuste(CENARIO, revelado, B));

    Estado ajustado = aceito(Regras.pedirAjuste(CENARIO, pedido, A));

    assertThat(revelado.sinal()).isPresent();
    assertThat(ajustado.sinal()).isEmpty();
  }

  @Test
  void recusarLimpaOPedido() {
    Estado pedido = aceito(Regras.pedirAjuste(CENARIO, revelado(), A));

    Estado recusado = aceito(Regras.recusarAjuste(CENARIO, pedido, B));

    assertThat(recusado.fase()).isEqualTo(new Fase.Revelado(Optional.empty()));
    assertThat(recusado.versao()).isEqualTo(pedido.versao());
    assertThat(recusado.prontos()).containsExactlyInAnyOrder(A, B);
  }

  @Test
  void cancelarOProprioPedidoLimpa() {
    Estado pedido = aceito(Regras.pedirAjuste(CENARIO, revelado(), A));

    Estado cancelado = aceito(Regras.recusarAjuste(CENARIO, pedido, A));

    assertThat(cancelado.fase()).isEqualTo(new Fase.Revelado(Optional.empty()));
  }

  @Test
  void recusarSemPedidoNaoMudaNada() {
    Estado revelado = revelado();

    assertThat(Regras.recusarAjuste(CENARIO, revelado, B)).isEqualTo(new Decisao.Aceita(revelado));
  }

  @Test
  void pedirAjusteForaDaRevelacaoERecusado() {
    assertThat(Regras.pedirAjuste(CENARIO, completa(), A)).isEqualTo(recusa(Recusa.FASE_INVALIDA));
  }

  @Test
  void recusarAjusteForaDaRevelacaoERecusado() {
    assertThat(Regras.recusarAjuste(CENARIO, completa(), A))
        .isEqualTo(recusa(Recusa.FASE_INVALIDA));
  }

  @Test
  void sinalComDestinoLivreEAceito() {
    Estado estado = colocar(montando(), LANTERNA, R2);

    Estado sinalizado = aceito(Regras.sinalizar(CENARIO, estado, A, LANTERNA, R1, AGORA));

    assertThat(sinalizado.sinal())
        .isEqualTo(Optional.of(new Sinal(LANTERNA, A, R1, AGORA.plusSeconds(20))));
    assertThat(sinalizado.versao()).isEqualTo(estado.versao());
  }

  @Test
  void sinalVale20Segundos() {
    Estado sinalizado =
        aceito(
            Regras.sinalizar(CENARIO, colocar(montando(), LANTERNA, R2), A, LANTERNA, R1, AGORA));

    assertThat(sinalizado.sinalAtivoEm(AGORA.plusSeconds(19))).isPresent();
    assertThat(sinalizado.sinalAtivoEm(AGORA.plusSeconds(20))).isEmpty();
  }

  @Test
  void sinalSobrePecaPropriaEhRecusado() {
    Estado estado = colocar(montando(), TELESCOPIO, C1);

    assertThat(Regras.sinalizar(CENARIO, estado, A, TELESCOPIO, C2, AGORA))
        .isEqualTo(recusa(Recusa.PECA_ALHEIA));
  }

  @Test
  void sinalSobrePecaNaoColocadaEhRecusado() {
    assertThat(Regras.sinalizar(CENARIO, montando(), A, BANCO, C1, AGORA))
        .isEqualTo(recusa(Recusa.FASE_INVALIDA));
  }

  @Test
  void sinalParaLugarOcupadoERecusado() {
    Estado estado = colocar(colocar(montando(), LANTERNA, R2), MANTA, L2);

    assertThat(Regras.sinalizar(CENARIO, estado, A, LANTERNA, L2, AGORA))
        .isEqualTo(recusa(Recusa.LUGAR_OCUPADO));
  }

  @Test
  void sinalParaOutraZonaERecusado() {
    Estado estado = colocar(montando(), LANTERNA, R2);

    assertThat(Regras.sinalizar(CENARIO, estado, A, LANTERNA, C1, AGORA))
        .isEqualTo(recusa(Recusa.LUGAR_INVALIDO));
  }

  @Test
  void sinalParaOLugarAtualERecusado() {
    Estado estado = colocar(montando(), LANTERNA, R2);

    assertThat(Regras.sinalizar(CENARIO, estado, A, LANTERNA, R2, AGORA))
        .isEqualTo(recusa(Recusa.LUGAR_INVALIDO));
  }

  @Test
  void sinalSomeQuandoODonoMoveAPeca() {
    Estado estado = colocar(colocar(montando(), LANTERNA, R2), TELESCOPIO, C1);
    Estado sinalizado = aceito(Regras.sinalizar(CENARIO, estado, A, LANTERNA, R1, AGORA));

    Estado movido = aceito(Regras.colocar(CENARIO, sinalizado, B, LANTERNA, Optional.empty(), 2));

    assertThat(movido.sinal()).isEmpty();
  }

  @Test
  void sinalContinuaQuandoOutraPecaSeMove() {
    Estado estado = colocar(colocar(montando(), LANTERNA, R2), TELESCOPIO, C1);
    Estado sinalizado = aceito(Regras.sinalizar(CENARIO, estado, A, LANTERNA, R1, AGORA));

    Estado movido = aceito(Regras.colocar(CENARIO, sinalizado, B, BANCO, Optional.of(C2), 2));

    assertThat(movido.sinal()).isEqualTo(sinalizado.sinal());
  }

  @Test
  void sinalCaiQuandoODestinoEOcupado() {
    Estado estado = colocar(colocar(montando(), LANTERNA, R2), TELESCOPIO, C1);
    Estado sinalizado = aceito(Regras.sinalizar(CENARIO, estado, A, LANTERNA, L2, AGORA));

    Estado ocupado = aceito(Regras.colocar(CENARIO, sinalizado, A, MANTA, Optional.of(L2), 2));

    assertThat(ocupado.sinal()).isEmpty();
  }

  @Test
  void destinosSugeriveisSaoOsLivresDaZonaSemOAtual() {
    Estado estado = completa();

    assertThat(Regras.destinosSugeriveis(CENARIO, estado, LANTERNA)).containsExactly(L2, R2);
    assertThat(Regras.destinosSugeriveis(CENARIO, estado, MANTA)).containsExactly(L2, R2);
    assertThat(Regras.destinosSugeriveis(CENARIO, estado, BANCO)).isEmpty();
  }

  @Test
  void destinosSugeriveisVaziosParaPecaForaDaCena() {
    assertThat(Regras.destinosSugeriveis(CENARIO, completa(), GARRAFA)).isEmpty();
  }

  @Test
  void destinosSugeriveisVaziosForaDaMontagem() {
    assertThat(Regras.destinosSugeriveis(CENARIO, revelado(), LANTERNA)).isEmpty();
  }

  @Test
  void zonaLivreExcetoOLugarAtual() {
    Estado estado = colocar(colocar(montando(), TELESCOPIO, C1), BANCO, C2);
    estado = colocar(estado, MANTA, L1);
    estado = colocar(estado, LANTERNA, R1);

    assertThat(Regras.lugaresPermitidos(CENARIO, estado, MANTA)).containsExactly(L2, R2);
    assertThat(Regras.lugaresPermitidos(CENARIO, estado, RADIO)).containsExactly(L1, L2, R2);
    assertThat(Regras.lugaresPermitidos(CENARIO, estado, TELESCOPIO)).isEmpty();
  }

  @Test
  void nenhumForaDaMontagem() {
    assertThat(Regras.lugaresPermitidos(CENARIO, Estado.inicial(), TELESCOPIO))
        .isEqualTo(List.of());
  }

  private static Stream<Fase> fasesForaDaMontagem() {
    return Stream.of(new Fase.AguardandoPar(), new Fase.Revelado());
  }

  /** O par entrou e os dois acenderam as lanternas juntos: a montagem está livre. */
  private static Estado montando() {
    Estado estado = aceito(Regras.parceiroEntrou(CENARIO, Estado.inicial(), AGORA));
    estado = aceito(Regras.tocarAbertura(CENARIO, estado, A, AGORA));
    return aceito(Regras.tocarAbertura(CENARIO, estado, B, AGORA));
  }

  /** Telescópio C1, banco C2, manta L1, lanterna R1: versão 4. */
  private static Estado completa() {
    Estado estado = colocar(colocar(montando(), TELESCOPIO, C1), BANCO, C2);
    return colocar(colocar(estado, MANTA, L1), LANTERNA, R1);
  }

  private static Estado revelado() {
    return confirmar(confirmar(completa(), A), B);
  }

  private static Estado colocar(Estado estado, Peca peca, Lugar lugar) {
    return aceito(
        Regras.colocar(CENARIO, estado, peca.dono(), peca, Optional.of(lugar), estado.versao()));
  }

  private static Estado confirmar(Estado estado, Assento quem) {
    return aceito(Regras.confirmar(CENARIO, estado, quem, true, estado.versao()));
  }

  private static Estado aceito(Decisao decisao) {
    assertThat(decisao).isInstanceOf(Decisao.Aceita.class);
    return ((Decisao.Aceita) decisao).estado();
  }

  private static Decisao recusa(Recusa recusa) {
    return new Decisao.Recusada(recusa);
  }
}
