package bipotheking.net.duora.pareamento.dominio;

import static bipotheking.net.duora.pareamento.dominio.CandidatoBuilder.T0;
import static bipotheking.net.duora.perfil.Genero.HOMEM;
import static bipotheking.net.duora.perfil.Genero.MULHER;
import static bipotheking.net.duora.perfil.Genero.OUTRO;
import static bipotheking.net.duora.perfil.Genero.PESSOA_NAO_BINARIA;
import static bipotheking.net.duora.perfil.Intencao.AMIZADE;
import static bipotheking.net.duora.perfil.Intencao.NAMORO;
import static bipotheking.net.duora.perfil.Raio.ATE_20_KM;
import static bipotheking.net.duora.perfil.Raio.ATE_50_KM;
import static bipotheking.net.duora.perfil.Raio.ATE_5_KM;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.Candidato;
import bipotheking.net.duora.pareamento.ParDeContas;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Trinta pessoas fictícias para provar a spec do pareamento num ciclo só ({@link #AGORA}, ciclo de
 * {@link #CICLO}, 10 vagas, os 5 {@link #vetados()}). A pessoa {@code n} tem a conta {@code
 * 00000000-0000-4000-8000-0000000000nn} e entrou na fila em {@code T0 + 5·(n−1)} s, salvo a 11, que
 * entrou em {@code T0 + 52} s e divide o ciclo de espera com a 12. Cada grupo de idade tem faixa
 * etária só dentro dele, então grupos diferentes nunca se combinam.
 *
 * <p>Células e distâncias entre os centros: {@code 6gyf4}–{@code 6gycf} 4,9 km; {@code 6gyf4}–
 * {@code 6gyft} 16,6 km; {@code 6gycf}–{@code 6gyft} 19,9 km; {@code 6gysh}–{@code 6gyf4} 47,5 km;
 * {@code 6gysh}–{@code 6gyft} 49,9 km; {@code 6gxp9} fica a mais de 50 km de todas; {@code 75cm9}
 * (Rio), a mais de 340 km. A = amizade, N = namoro, AN = as duas; "M→H" = mulher que busca homem;
 * NB = pessoa não binária.
 *
 * <pre>
 * Nº  Perfil                                         Desfecho esperado
 * --- Grupo 18–22 (faixa 18–22) ---------------------------------------------------------------
 * 01  A, 19, 6gyf4, TESTE                            par com 29 (só conta de teste com conta de teste)
 * 04  A, 18, 6gyf4                                   par com 12 (11 e 12 no mesmo ciclo; 12 mais perto)
 * 11  A, 20, 6gyft                                   sem par: 04 preferiu 12; o resto é conta de teste
 * 12  A, 21, 6gycf                                   par com 04
 * 29  AN M→H, 22, 6gycf, TESTE                       par com 01, só amizade
 * --- Grupo 23–27 (faixa 23–27) ---------------------------------------------------------------
 * 02  N M→H, 23, 6gyf4                               par com 17, namoro
 * 09  N M→M, 24, 6gycf                               sem par: 23 busca 25–27; 28 é homem
 * 17  N H→M, 25, 6gyf4                               par com 02
 * 23  N M→M, 27, 6gyft, faixa 25–27                  sem par: 09 tem 24, fora da faixa dela
 * 28  N H→H, 26, 6gxp9                               sem par: nenhum outro homem busca homem
 * --- Grupo 28–32 (faixa 28–32) ---------------------------------------------------------------
 * 03  AN NB→OUTRO, 28, 6gyf4                         par com 10, amizade e namoro
 * 10  AN OUTRO→NB, 30, 6gyft                         par com 03
 * 18  A, 31, 6gycf                                   sem par: 24 busca 28–30
 * 24  AN H→M, 32, sem célula, faixa 28–30            sem par: 18 tem 31, fora da faixa dele
 * --- Grupo 33–37 (faixa 33–37) ---------------------------------------------------------------
 * 05  AN M→H, 33, 6gyf4                              par com 25, só amizade (vetou 13 e 19)
 * 13  AN H→M, 34, 6gycf                              par com 19 (sem os vetos, seria 05 com namoro)
 * 19  A, 35, 6gyft                                   par com 13
 * 25  AN H→H, 36, 6gyf4                              par com 05, só amizade (namoro não casa)
 * --- Grupo 38–42 (faixa 38–42) ---------------------------------------------------------------
 * 06  AN H→M, 42, sem célula                         sem par: vetado com 14 e com 20
 * 14  A, 38, 6gyf4                                   par com 20 (sem os vetos, 06 levaria 14)
 * 20  A, 40, 6gycf                                   par com 14
 * --- Grupo 43–47 (faixa 43–47) ---------------------------------------------------------------
 * 07  A, 43, 6gyf4, raio 5 km obrigatório            par com 15, a 4,9 km
 * 15  AN H→M, 45, 6gycf, raio 20 km obrigatório      par com 07
 * 21  AN M→H/M, 46, 75cm9, raio 50 km obrigatório    sem par: todo o grupo está a mais de 340 km
 * 26  A, 47, sem célula, raio 20 km obrigatório      sem par: raio obrigatório sem célula conhecida
 * --- Grupo 53–60 (faixa 53–60) ---------------------------------------------------------------
 * 08  N H→M, 53, 6gysh, raio 50 km obrigatório       par com 22, a 49,9 km (vetou 16)
 * 16  N M→H, 55, 6gyf4                               sem par: vetada com 08; 27 exige 5 km e está a 16,6
 * 22  N M→H, 56, 6gyft                               par com 08
 * 27  N H→M, 60, 6gyft, raio 5 km obrigatório        sem par: 16 está a 16,6 km; 30 não é mulher
 * 30  N NB→M, 58, sem célula                         sem par: a única mulher livre (16) só busca homem
 * </pre>
 *
 * <p>Vetos: 05–13 (não), 05–19 (saída), 06–14 (bloqueio), 06–20 (denúncia), 08–16 (já conectados).
 * Resultado: 9 pares, 12 sem par, nenhum par possível entre quem sobrou.
 */
public final class PerfisFicticios {

  /** O instante do ciclo: a pessoa 01 espera há 30 ciclos, a 30 há 1. */
  public static final Instant AGORA = T0.plusSeconds(150);

  public static final Duration CICLO = Duration.ofSeconds(5);

  private record Ficha(Candidato candidato, String desfecho) {}

  private static final List<Ficha> FICHAS =
      List.of(
          ficha(
              "00000000-0000-4000-8000-000000000001",
              entrou(1).idade(19).faixa(18, 22).celula("6gyf4").deTeste(),
              "par com 29: conta de teste só com conta de teste"),
          ficha(
              "00000000-0000-4000-8000-000000000002",
              entrou(2)
                  .intencoes(NAMORO)
                  .idade(23)
                  .faixa(23, 27)
                  .genero(MULHER)
                  .buscando(HOMEM)
                  .celula("6gyf4"),
              "par com 17, namoro"),
          ficha(
              "00000000-0000-4000-8000-000000000003",
              entrou(3)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(28)
                  .faixa(28, 32)
                  .genero(PESSOA_NAO_BINARIA)
                  .buscando(OUTRO)
                  .celula("6gyf4"),
              "par com 10, amizade e namoro"),
          ficha(
              "00000000-0000-4000-8000-000000000004",
              entrou(4).idade(18).faixa(18, 22).celula("6gyf4"),
              "par com 12: 11 e 12 no mesmo ciclo de espera, 12 mais perto"),
          ficha(
              "00000000-0000-4000-8000-000000000005",
              entrou(5)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(33)
                  .faixa(33, 37)
                  .genero(MULHER)
                  .buscando(HOMEM)
                  .celula("6gyf4"),
              "par com 25, só amizade: vetou 13 e 19"),
          ficha(
              "00000000-0000-4000-8000-000000000006",
              entrou(6)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(42)
                  .faixa(38, 42)
                  .genero(HOMEM)
                  .buscando(MULHER),
              "sem par: vetado com 14 e com 20, os únicos do grupo"),
          ficha(
              "00000000-0000-4000-8000-000000000007",
              entrou(7).idade(43).faixa(43, 47).raio(ATE_5_KM, true).celula("6gyf4"),
              "par com 15, a 4,9 km, dentro do raio de 5 km"),
          ficha(
              "00000000-0000-4000-8000-000000000008",
              entrou(8)
                  .intencoes(NAMORO)
                  .idade(53)
                  .faixa(53, 60)
                  .genero(HOMEM)
                  .buscando(MULHER)
                  .raio(ATE_50_KM, true)
                  .celula("6gysh"),
              "par com 22, a 49,9 km: vetou 16"),
          ficha(
              "00000000-0000-4000-8000-000000000009",
              entrou(9)
                  .intencoes(NAMORO)
                  .idade(24)
                  .faixa(23, 27)
                  .genero(MULHER)
                  .buscando(MULHER)
                  .celula("6gycf"),
              "sem par: 23 busca 25 a 27 anos; 28 é homem"),
          ficha(
              "00000000-0000-4000-8000-000000000010",
              entrou(10)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(30)
                  .faixa(28, 32)
                  .genero(OUTRO)
                  .buscando(PESSOA_NAO_BINARIA)
                  .celula("6gyft"),
              "par com 03"),
          ficha(
              "00000000-0000-4000-8000-000000000011",
              CandidatoBuilder.candidato(0)
                  .entrouSegundosDepois(52)
                  .idade(20)
                  .faixa(18, 22)
                  .celula("6gyft"),
              "sem par: 04 preferiu 12, mais perto; o resto do grupo é conta de teste"),
          ficha(
              "00000000-0000-4000-8000-000000000012",
              entrou(12).idade(21).faixa(18, 22).celula("6gycf"),
              "par com 04"),
          ficha(
              "00000000-0000-4000-8000-000000000013",
              entrou(13)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(34)
                  .faixa(33, 37)
                  .genero(HOMEM)
                  .buscando(MULHER)
                  .celula("6gycf"),
              "par com 19: sem o veto, seria 05 com namoro"),
          ficha(
              "00000000-0000-4000-8000-000000000014",
              entrou(14).idade(38).faixa(38, 42).celula("6gyf4"),
              "par com 20: sem o veto, 06 levaria 14"),
          ficha(
              "00000000-0000-4000-8000-000000000015",
              entrou(15)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(45)
                  .faixa(43, 47)
                  .genero(HOMEM)
                  .buscando(MULHER)
                  .raio(ATE_20_KM, true)
                  .celula("6gycf"),
              "par com 07"),
          ficha(
              "00000000-0000-4000-8000-000000000016",
              entrou(16)
                  .intencoes(NAMORO)
                  .idade(55)
                  .faixa(53, 60)
                  .genero(MULHER)
                  .buscando(HOMEM)
                  .celula("6gyf4"),
              "sem par: vetada com 08; 27 exige 5 km e está a 16,6 km"),
          ficha(
              "00000000-0000-4000-8000-000000000017",
              entrou(17)
                  .intencoes(NAMORO)
                  .idade(25)
                  .faixa(23, 27)
                  .genero(HOMEM)
                  .buscando(MULHER)
                  .celula("6gyf4"),
              "par com 02"),
          ficha(
              "00000000-0000-4000-8000-000000000018",
              entrou(18).idade(31).faixa(28, 32).celula("6gycf"),
              "sem par: 24 busca 28 a 30 anos"),
          ficha(
              "00000000-0000-4000-8000-000000000019",
              entrou(19).idade(35).faixa(33, 37).celula("6gyft"),
              "par com 13"),
          ficha(
              "00000000-0000-4000-8000-000000000020",
              entrou(20).idade(40).faixa(38, 42).celula("6gycf"),
              "par com 14"),
          ficha(
              "00000000-0000-4000-8000-000000000021",
              entrou(21)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(46)
                  .faixa(43, 47)
                  .genero(MULHER)
                  .buscando(HOMEM, MULHER)
                  .raio(ATE_50_KM, true)
                  .celula("75cm9"),
              "sem par: raio de 50 km no Rio, o grupo está a mais de 340 km"),
          ficha(
              "00000000-0000-4000-8000-000000000022",
              entrou(22)
                  .intencoes(NAMORO)
                  .idade(56)
                  .faixa(53, 60)
                  .genero(MULHER)
                  .buscando(HOMEM)
                  .celula("6gyft"),
              "par com 08"),
          ficha(
              "00000000-0000-4000-8000-000000000023",
              entrou(23)
                  .intencoes(NAMORO)
                  .idade(27)
                  .faixa(25, 27)
                  .genero(MULHER)
                  .buscando(MULHER)
                  .celula("6gyft"),
              "sem par: 09 tem 24 anos, fora da faixa dela; 28 é homem"),
          ficha(
              "00000000-0000-4000-8000-000000000024",
              entrou(24)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(32)
                  .faixa(28, 30)
                  .genero(HOMEM)
                  .buscando(MULHER),
              "sem par: 18 tem 31 anos, fora da faixa dele"),
          ficha(
              "00000000-0000-4000-8000-000000000025",
              entrou(25)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(36)
                  .faixa(33, 37)
                  .genero(HOMEM)
                  .buscando(HOMEM)
                  .celula("6gyf4"),
              "par com 05, só amizade: o namoro não casa"),
          ficha(
              "00000000-0000-4000-8000-000000000026",
              entrou(26).idade(47).faixa(43, 47).raio(ATE_20_KM, true),
              "sem par: raio obrigatório sem célula conhecida"),
          ficha(
              "00000000-0000-4000-8000-000000000027",
              entrou(27)
                  .intencoes(NAMORO)
                  .idade(60)
                  .faixa(53, 60)
                  .genero(HOMEM)
                  .buscando(MULHER)
                  .raio(ATE_5_KM, true)
                  .celula("6gyft"),
              "sem par: 16 está a 16,6 km, fora do raio de 5 km; 30 não é mulher"),
          ficha(
              "00000000-0000-4000-8000-000000000028",
              entrou(28)
                  .intencoes(NAMORO)
                  .idade(26)
                  .faixa(23, 27)
                  .genero(HOMEM)
                  .buscando(HOMEM)
                  .celula("6gxp9"),
              "sem par: nenhum outro homem do grupo busca homem"),
          ficha(
              "00000000-0000-4000-8000-000000000029",
              entrou(29)
                  .intencoes(AMIZADE, NAMORO)
                  .idade(22)
                  .faixa(18, 22)
                  .genero(MULHER)
                  .buscando(HOMEM)
                  .celula("6gycf")
                  .deTeste(),
              "par com 01, só amizade: conta de teste só com conta de teste"),
          ficha(
              "00000000-0000-4000-8000-000000000030",
              entrou(30)
                  .intencoes(NAMORO)
                  .idade(58)
                  .faixa(53, 60)
                  .genero(PESSOA_NAO_BINARIA)
                  .buscando(MULHER),
              "sem par: a única mulher livre (16) só busca homem"));

  private static final Set<ParDeContas> VETADOS =
      Set.of(
          ParDeContas.de(pessoa(5), pessoa(13)),
          ParDeContas.de(pessoa(5), pessoa(19)),
          ParDeContas.de(pessoa(6), pessoa(14)),
          ParDeContas.de(pessoa(6), pessoa(20)),
          ParDeContas.de(pessoa(8), pessoa(16)));

  private PerfisFicticios() {}

  /** As 30 pessoas, da 01 à 30, na ordem em que entraram na fila. */
  public static List<Candidato> trinta() {
    return FICHAS.stream().map(Ficha::candidato).toList();
  }

  /** Os 5 pares vetados da tabela. */
  public static Set<ParDeContas> vetados() {
    return VETADOS;
  }

  /** A conta da pessoa {@code numero} (1 a 30) da tabela. */
  public static ContaId pessoa(int numero) {
    return FICHAS.get(numero - 1).candidato().conta();
  }

  /** O número da pessoa na tabela, com dois dígitos. */
  public static String rotulo(ContaId conta) {
    return "%02d".formatted(indice(conta) + 1);
  }

  /** O desfecho esperado da pessoa e o motivo, como na tabela. */
  public static String desfecho(ContaId conta) {
    return FICHAS.get(indice(conta)).desfecho();
  }

  private static int indice(ContaId conta) {
    for (int i = 0; i < FICHAS.size(); i++) {
      if (FICHAS.get(i).candidato().conta().equals(conta)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Conta fora da simulação");
  }

  /** A pessoa {@code numero}, na fila desde {@code T0 + 5·(numero−1)} s. */
  private static CandidatoBuilder entrou(int numero) {
    return CandidatoBuilder.candidato(0).entrouSegundosDepois(5L * (numero - 1));
  }

  /** O candidato montado, com a conta trocada pelo UUID literal da tabela. */
  private static Ficha ficha(String uuid, CandidatoBuilder perfil, String desfecho) {
    Candidato montado = perfil.build();
    Candidato candidato =
        new Candidato(
            new ContaId(UUID.fromString(uuid)),
            montado.entrouEm(),
            montado.idade(),
            montado.preferencias(),
            montado.celula(),
            montado.deTeste());
    return new Ficha(candidato, desfecho);
  }
}
