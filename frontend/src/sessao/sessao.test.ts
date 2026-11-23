import { describe, expect, expectTypeOf, it, vi } from "vitest";
import { type Api, ErroApi, type Resposta } from "../api/cliente";
import type { GanchosDoPolling, Polling } from "../api/polling";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { cenarioDoTerraco } from "../teste/cenarioDoTerraco";
import type { Aviso } from "./aviso";
import { type Agendar, DURACAO_DESTAQUE_MS } from "./destaques";
import { criarSessao, type EstadoDaTela } from "./sessao";

const BRUNO = { assento: "B", nome: "Bruno", conectado: true } as const;
const BANCO_EM_C2: VisaoPeca = { peca: "banco", assento: "B", lugar: "C2", destinosSugeriveis: ["C1"] };
const LANTERNA_EM_R2: VisaoPeca = {
  peca: "lanterna",
  assento: "B",
  lugar: "R2",
  destinosSugeriveis: ["L1", "L2", "R1"],
};

function visao(mudancas: Partial<Visao> = {}): Visao {
  return {
    fase: "MONTANDO",
    versao: 3,
    voce: { assento: "A", nome: "Ana" },
    parceiro: BRUNO,
    convite: null,
    pecas: [
      { peca: "telescopio", assento: "A", lugar: null, lugaresPermitidos: ["C1", "C2"], bloqueio: null },
      { peca: "manta", assento: "A", lugar: null, lugaresPermitidos: ["L1"], bloqueio: null },
      { peca: "banco", assento: "B", lugar: null },
    ],
    prontos: { voce: false, parceiro: false },
    confirmacao: { podeConfirmar: false, faltam: 4 },
    sinal: null,
    resultado: null,
    ajuste: null,
    abertura: null,
    ...mudancas,
  };
}

/** Bruno no assento B, com a sugestão de Ana para a lanterna dele ir ao R1. */
function visaoDeBrunoComSinal(mudancas: Partial<Visao> = {}): Visao {
  return visao({
    versao: 4,
    voce: { assento: "B", nome: "Bruno" },
    parceiro: { assento: "A", nome: "Ana", conectado: true },
    pecas: [{ peca: "lanterna", assento: "B", lugar: "R2", lugaresPermitidos: ["L1", "R1"], bloqueio: null }],
    sinal: { peca: "lanterna", deAssento: "A", lugar: "R1" },
    ...mudancas,
  });
}

const resposta = (v: Visao | null, etag: string | null = '"v"'): Resposta => ({ naoMudou: false, visao: v, etag });
const pendente = () => new Promise<Resposta>(() => undefined);

/** Uma resposta que o teste resolve ou rejeita quando quiser. */
function adiada() {
  let resolver: (r: Resposta) => void = () => undefined;
  let rejeitar: (erro: unknown) => void = () => undefined;
  const promessa = new Promise<Resposta>((res, rej) => {
    resolver = res;
    rejeitar = rej;
  });
  return { promessa, resolver, rejeitar };
}

const CONVITE_PROPRIO = "Este é o convite do seu terraço. Mande para a outra pessoa.";

const ENCERRADA_PELO_PARCEIRO = new ErroApi({ status: 410, codigo: "SALA_ENCERRADA", motivo: "SAIU" });

/** Monta a sessão com api, aviso e polling falsos; o polling devolve os ganchos para o teste agir por ele. */
function montar({
  api = {},
  convite = null,
  agendar,
  ehToque,
}: { api?: Partial<Api>; convite?: string | null; agendar?: Agendar; ehToque?: () => boolean } = {}) {
  const apiFalsa: Api = {
    criarSala: vi.fn(pendente),
    entrar: vi.fn(pendente),
    obterSala: vi.fn(pendente),
    moverPeca: vi.fn(pendente),
    sinalizar: vi.fn(pendente),
    marcarPronto: vi.fn(pendente),
    pedirAjuste: vi.fn(pendente),
    tocarAbertura: vi.fn(pendente),
    recusarAjuste: vi.fn(pendente),
    sair: vi.fn(pendente),
    ...api,
  };
  const aviso = { avisar: vi.fn<Aviso["avisar"]>(), avisarComDetalhe: vi.fn<Aviso["avisarComDetalhe"]>() };
  const polling = {
    iniciar: vi.fn<Polling["iniciar"]>(),
    parar: vi.fn<Polling["parar"]>(),
    consultarAgora: vi.fn<Polling["consultarAgora"]>(),
    aceitar: vi.fn<Polling["aceitar"]>(),
  };
  let ganchos: GanchosDoPolling | null = null;
  const sessao = criarSessao({
    api: apiFalsa,
    aviso,
    convite,
    cenario: cenarioDoTerraco(),
    ...(agendar ? { agendar } : {}),
    ...(ehToque ? { ehToque } : {}),
    criarPolling: (_api, g) => {
      ganchos = g;
      return polling;
    },
  });
  const doPolling = (): GanchosDoPolling => {
    if (ganchos === null) {
      throw new Error("polling não foi criado");
    }
    return ganchos;
  };
  return { sessao, api: apiFalsa, aviso, polling, doPolling };
}

/** Sessão iniciada que já recebeu a visão pelo polling. */
function naSala(v: Visao = visao(), opcoes: Parameters<typeof montar>[0] = {}) {
  const m = montar(opcoes);
  m.sessao.iniciar();
  m.doPolling().aoReceber(v);
  m.aviso.avisar.mockClear();
  return m;
}

/** Agenda falsa: o teste avança o tempo e vê quais tarefas seguem pendentes. */
function agendaFalsa() {
  let agora = 0;
  const tarefas: { quando: number; acao: () => void; ativa: boolean }[] = [];
  const agendar: Agendar = (acao, ms) => {
    const tarefa = { quando: agora + ms, acao, ativa: true };
    tarefas.push(tarefa);
    return () => {
      tarefa.ativa = false;
    };
  };
  return {
    agendar,
    avancar(ms: number) {
      agora += ms;
      for (const tarefa of tarefas) {
        if (tarefa.ativa && tarefa.quando <= agora) {
          tarefa.ativa = false;
          tarefa.acao();
        }
      }
    },
    pendentes: () => tarefas.filter((t) => t.ativa).length,
  };
}

/** A visão com o banco de Bruno no lugar dado. */
function comBancoEm(lugar: string, mudancas: Partial<Visao> = {}): Visao {
  const base = visao(mudancas);
  return { ...base, pecas: base.pecas.map((p) => (p.peca === "banco" ? { ...p, lugar } : p)) };
}

const destaquesDe = (estado: EstadoDaTela): readonly string[] =>
  estado.tela === "montagem" ? estado.destaques : ["fora da montagem"];

describe("criarSessao", () => {
  it("começa carregando e inicia o polling", () => {
    const { sessao, polling } = montar();

    sessao.iniciar();

    expect(sessao.obter()).toMatchObject({ tela: "carregando", enviando: null, momento: "crepusculo" });
    expect(polling.iniciar).toHaveBeenCalledOnce();
  });

  it("semAssentoMostraEntrada", () => {
    const { sessao, doPolling, aviso } = montar();
    sessao.iniciar();

    doPolling().aoFalhar(new ErroApi({ status: 401, codigo: "SEM_ASSENTO" }), null);

    expect(sessao.obter()).toMatchObject({ tela: "entrada", comConvite: false, erroApelido: "" });
    expect(aviso.avisar).toHaveBeenCalledWith("");
  });

  it("a visão em MONTANDO mostra a montagem com a instrução", () => {
    const { sessao } = naSala();

    expect(sessao.obter()).toMatchObject({
      tela: "montagem",
      selecionada: null,
      instrucao: "Escolha uma das suas peças e depois um lugar no terraço.",
    });
  });

  it("tocarAberturaEnviaOToqueEMostraAVisaoDaResposta", async () => {
    const ativa = { ativa: true, voceTocou: false, outroTocou: false, acesa: false };
    const tocou = visao({ abertura: { ...ativa, voceTocou: true } });
    const { sessao, api } = naSala(visao({ abertura: ativa }), {
      api: { tocarAbertura: vi.fn(() => Promise.resolve(resposta(tocou))) },
    });

    await sessao.tocarAbertura();

    expect(api.tocarAbertura).toHaveBeenCalledOnce();
    expect(sessao.obter()).toMatchObject({ tela: "montagem", visao: tocou, instrucao: "", enviando: null });
  });

  describe("ajuste combinado", () => {
    const RESULTADO = { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "esquerda" };
    const noite = (ajuste: Visao["ajuste"] = null) => visao({ fase: "REVELADO", resultado: RESULTADO, ajuste });

    it("pedirAjusteChamaAApi", async () => {
      const { sessao, api, aviso } = naSala(noite(), {
        api: { pedirAjuste: vi.fn(() => Promise.resolve(resposta(noite({ deAssento: "A" })))) },
      });

      await sessao.pedirAjuste();

      expect(api.pedirAjuste).toHaveBeenCalledOnce();
      expect(sessao.obter()).toMatchObject({ momento: "noite", enviando: null });
      expect(aviso.avisar).toHaveBeenLastCalledWith("Pedido enviado. Esperando Bruno aceitar.");
    });

    it("pedidoCruzadoVoltaAMontagemComTextoDeAceite", async () => {
      const { sessao, aviso } = naSala(noite(), {
        api: { pedirAjuste: vi.fn(() => Promise.resolve(resposta(visao({ versao: 4 })))) },
      });

      await sessao.pedirAjuste();

      expect(sessao.obter()).toMatchObject({ tela: "montagem", momento: "crepusculo" });
      expect(aviso.avisar).toHaveBeenLastCalledWith("De volta à montagem. As confirmações foram zeradas.");
    });

    it("recusarAjusteDoOutroMantemANoite", async () => {
      const { sessao, api, aviso } = naSala(noite({ deAssento: "B" }), {
        api: { recusarAjuste: vi.fn(() => Promise.resolve(resposta(noite()))) },
      });

      await sessao.recusarAjuste();

      expect(api.recusarAjuste).toHaveBeenCalledOnce();
      expect(sessao.obter()).toMatchObject({ momento: "noite", enviando: null });
      expect(aviso.avisar).toHaveBeenLastCalledWith("Tudo bem, a noite fica como está.");
    });

    it("cancelarOProprioPedidoAvisaQueCancelou", async () => {
      const { sessao, aviso } = naSala(noite({ deAssento: "A" }), {
        api: { recusarAjuste: vi.fn(() => Promise.resolve(resposta(noite()))) },
      });

      await sessao.recusarAjuste();

      expect(aviso.avisar).toHaveBeenLastCalledWith("Pedido cancelado.");
    });

    it("recusaEmEnvioMostraQualAcaoEsperaOServidor", () => {
      const { sessao } = naSala(noite({ deAssento: "B" }));

      void sessao.recusarAjuste();

      expect(sessao.obter()).toMatchObject({ enviando: "recusar-ajuste" });
    });
  });

  it("toqueRecusadoPelaAberturaAvisaParaEsperar", async () => {
    const erro = new ErroApi({ status: 409, codigo: "ABERTURA_EM_ANDAMENTO" });
    const { sessao, aviso } = naSala(visao(), { api: { moverPeca: vi.fn(() => Promise.reject(erro)) } });
    await sessao.escolherPeca("telescopio", true);

    await sessao.escolherLugar("C1", null);

    expect(aviso.avisar).toHaveBeenLastCalledWith("Espere as lanternas acenderem.", "erro");
  });

  it("a visão em AGUARDANDO_PAR mostra o convite", () => {
    const { sessao } = naSala(visao({ fase: "AGUARDANDO_PAR", parceiro: null, convite: "abc" }));

    expect(sessao.obter()).toMatchObject({ tela: "convite" });
  });

  it("a visão revelada passa para a noite com o lado do telescópio", () => {
    const resultado = { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "esquerda" };
    const { sessao } = naSala(visao({ fase: "REVELADO", resultado }));

    expect(sessao.obter()).toMatchObject({ momento: "noite", ladoTelescopio: "esquerda" });
  });

  it("resultado sem ladoTelescopio não mostra meteoros", () => {
    const { sessao } = naSala(visao({ fase: "REVELADO", resultado: { juntos: "sim" } }));

    expect(sessao.obter()).toMatchObject({ momento: "noite", ladoTelescopio: null });
  });

  it("assinar avisa a cada mudança e obter devolve o mesmo objeto enquanto nada muda", () => {
    const { sessao, doPolling } = montar();
    const ouvinte = vi.fn();
    const cancelar = sessao.assinar(ouvinte);
    sessao.iniciar();
    const antes = sessao.obter();

    doPolling().aoReceber(visao());
    cancelar();
    doPolling().aoReceber(visao({ versao: 4 }));

    expect(sessao.obter()).toBe(sessao.obter());
    expect(sessao.obter()).not.toBe(antes);
    expect(ouvinte).toHaveBeenCalledTimes(2);
  });

  it("encerradoNaoCarregaVisao", async () => {
    const { sessao } = naSala(visao(), { api: { sair: vi.fn(() => Promise.resolve(resposta(null, null))) } });

    await sessao.encerrar();

    const estado = sessao.obter();
    expect(estado).toMatchObject({
      tela: "encerrado",
      titulo: "Você saiu do terraço",
      texto: "O encontro terminou para as duas pessoas.",
    });
    expect(estado).not.toHaveProperty("visao");
    expect(sessao.consumirFoco()).toEqual({ alvo: "titulo-encerrado" });
    expectTypeOf<Extract<EstadoDaTela, { tela: "encerrado" }>>().not.toHaveProperty("visao");
  });

  it("recusa409ComVisaoPedeFocoAposRecusa", async () => {
    const recusa = new ErroApi({ status: 409, codigo: "LUGAR_OCUPADO", visao: visao({ versao: 4 }) });
    const { sessao, aviso, polling } = naSala(visao(), {
      api: { moverPeca: vi.fn(() => Promise.reject(recusa)) },
    });
    await sessao.escolherPeca("telescopio", true);
    expect(sessao.consumirFoco()).toEqual({ alvo: "primeiro-lugar" });

    await sessao.escolherLugar("C1", null);

    expect(sessao.consumirFoco()).toEqual({ alvo: "apos-recusa", peca: "telescopio" });
    expect(sessao.consumirFoco()).toBeNull();
    expect(polling.aceitar).toHaveBeenLastCalledWith(null);
    expect(aviso.avisar).toHaveBeenLastCalledWith("Bruno acabou de usar este lugar. Escolha outro.", "erro");
  });

  it("peça colocada: a seleção some e o foco volta ao item", async () => {
    const colocada = visao({
      versao: 4,
      pecas: [{ peca: "telescopio", assento: "A", lugar: "C1", lugaresPermitidos: ["C1", "C2"], bloqueio: null }],
    });
    const { sessao, api, polling } = naSala(visao(), {
      api: { moverPeca: vi.fn(() => Promise.resolve(resposta(colocada, '"v4"'))) },
    });
    await sessao.escolherPeca("telescopio", true);
    sessao.consumirFoco();

    await sessao.escolherLugar("C1", null);

    expect(api.moverPeca).toHaveBeenCalledWith("telescopio", "C1", 3);
    expect(polling.aceitar).toHaveBeenLastCalledWith('"v4"');
    expect(sessao.obter()).toMatchObject({ tela: "montagem", selecionada: null, enviando: null });
    expect(sessao.consumirFoco()).toEqual({ alvo: "item", peca: "telescopio" });
  });

  it("lugar bloqueado só avisa", async () => {
    const { sessao, api, aviso } = naSala();
    await sessao.escolherPeca("telescopio", true);

    await sessao.escolherLugar("C1", "ocupado");

    expect(api.moverPeca).not.toHaveBeenCalled();
    expect(aviso.avisar).toHaveBeenCalledWith("Este lugar está ocupado. Escolha outro.", "erro");
  });

  it("peça do parceiro envia um sinal", async () => {
    const { sessao, api, aviso } = naSala(visao({ pecas: [BANCO_EM_C2] }), {
      api: { sinalizar: vi.fn(() => Promise.resolve(resposta(visao({ versao: 4 })))) },
    });

    await sessao.escolherPeca("banco", false);
    await sessao.escolherLugar("C1", null);

    expect(api.sinalizar).toHaveBeenCalledWith("banco", "C1");
    expect(aviso.avisar).toHaveBeenLastCalledWith("Você sugeriu que Bruno mova o banco: centro, à esquerda.");
  });

  it("sugerirChamaSinalizarComPecaELugar", async () => {
    const { sessao, api } = naSala(visao({ pecas: [LANTERNA_EM_R2] }), {
      api: { sinalizar: vi.fn(() => Promise.resolve(resposta(visao({ versao: 4 })))) },
    });

    await sessao.escolherPeca("lanterna", false);
    expect(api.sinalizar).not.toHaveBeenCalled();
    expect(sessao.obter()).toMatchObject({
      sugerindo: "lanterna",
      instrucao: "Escolha onde a lanterna de Bruno ficaria melhor. Esc cancela.",
    });
    expect(sessao.consumirFoco()).toEqual({ alvo: "primeiro-lugar" });

    await sessao.escolherLugar("R1", null);

    expect(api.sinalizar).toHaveBeenCalledExactlyOnceWith("lanterna", "R1");
    expect(sessao.obter()).toMatchObject({ sugerindo: null });
  });

  it("tocarDeNovoNaPecaSugeridaDesligaASugestao", async () => {
    const { sessao } = naSala(visao({ pecas: [LANTERNA_EM_R2] }));
    await sessao.escolherPeca("lanterna", false);

    await sessao.escolherPeca("lanterna", false);

    expect(sessao.obter()).toMatchObject({ sugerindo: null, selecionada: null });
  });

  it("escCancelaASugestao", async () => {
    const { sessao, api } = naSala(visao({ pecas: [LANTERNA_EM_R2] }));
    await sessao.escolherPeca("lanterna", false);
    sessao.consumirFoco();

    sessao.cancelarSelecao();

    expect(sessao.obter()).toMatchObject({ sugerindo: null });
    expect(sessao.consumirFoco()).toEqual({ alvo: "peca-na-cena", peca: "lanterna" });
    expect(api.sinalizar).not.toHaveBeenCalled();
  });

  it("selecionarPropriaCancelaSugestao", async () => {
    const { sessao } = naSala(visao({ pecas: [...visao().pecas, LANTERNA_EM_R2] }));
    await sessao.escolherPeca("lanterna", false);

    await sessao.escolherPeca("telescopio", true);

    expect(sessao.obter()).toMatchObject({ sugerindo: null, selecionada: "telescopio" });
  });

  it("sugerirCancelaASelecaoPropria", async () => {
    const { sessao } = naSala(visao({ pecas: [...visao().pecas, LANTERNA_EM_R2] }));
    await sessao.escolherPeca("telescopio", true);

    await sessao.escolherPeca("lanterna", false);

    expect(sessao.obter()).toMatchObject({ sugerindo: "lanterna", selecionada: null });
  });

  it("focoVoltaAPecaDepoisDeSugerir", async () => {
    const { sessao } = naSala(visao({ pecas: [LANTERNA_EM_R2] }), {
      api: { sinalizar: vi.fn(() => Promise.resolve(resposta(visao({ versao: 4, pecas: [LANTERNA_EM_R2] })))) },
    });
    await sessao.escolherPeca("lanterna", false);
    sessao.consumirFoco();

    await sessao.escolherLugar("R1", null);

    expect(sessao.consumirFoco()).toEqual({ alvo: "peca-na-cena", peca: "lanterna" });
  });

  it("sugestaoSomeQuandoAPecaFicaSemDestino", async () => {
    const { sessao, doPolling } = naSala(visao({ pecas: [LANTERNA_EM_R2] }));
    await sessao.escolherPeca("lanterna", false);

    doPolling().aoReceber(visao({ versao: 4, pecas: [{ ...LANTERNA_EM_R2, destinosSugeriveis: [] }] }));

    expect(sessao.obter()).toMatchObject({ sugerindo: null });
  });

  it("recusaDoSinalPorLugarOcupadoMantemASugestaoComTextoProprio", async () => {
    const recusa = new ErroApi({
      status: 409,
      codigo: "LUGAR_OCUPADO",
      visao: visao({ versao: 4, pecas: [{ ...LANTERNA_EM_R2, destinosSugeriveis: ["L1", "L2"] }] }),
    });
    const { sessao, aviso } = naSala(visao({ pecas: [LANTERNA_EM_R2] }), {
      api: { sinalizar: vi.fn(() => Promise.reject(recusa)) },
    });
    await sessao.escolherPeca("lanterna", false);
    sessao.consumirFoco();

    await sessao.escolherLugar("R1", null);

    expect(sessao.obter()).toMatchObject({ sugerindo: "lanterna" });
    expect(sessao.consumirFoco()).toEqual({ alvo: "primeiro-lugar" });
    expect(aviso.avisar).toHaveBeenLastCalledWith("Este lugar não está mais livre. Escolha outro.", "erro");
  });

  it("recusaDoSinalPorPecaAlheiaDizQueAPecaESua", async () => {
    const recusa = new ErroApi({ status: 409, codigo: "PECA_ALHEIA", visao: visao({ versao: 4 }) });
    const { sessao, aviso } = naSala(visao({ pecas: [LANTERNA_EM_R2] }), {
      api: { sinalizar: vi.fn(() => Promise.reject(recusa)) },
    });
    await sessao.escolherPeca("lanterna", false);

    await sessao.escolherLugar("R1", null);

    expect(aviso.avisar).toHaveBeenLastCalledWith(
      "Essa peça é sua. Para mudá-la de lugar, escolha-a em Suas peças.",
      "erro",
    );
  });

  it("moverParaLevaAPecaAoLugarSugerido", async () => {
    const { sessao, api } = naSala(visaoDeBrunoComSinal(), {
      api: { moverPeca: vi.fn(() => Promise.resolve(resposta(visaoDeBrunoComSinal({ versao: 5, sinal: null })))) },
    });

    await sessao.moverPara("lanterna", "R1");

    expect(api.moverPeca).toHaveBeenCalledExactlyOnceWith("lanterna", "R1", 4);
    expect(sessao.consumirFoco()).toEqual({ alvo: "item", peca: "lanterna" });
  });

  it("aceiteDepoisQueODestinoFoiOcupadoMostraAviso", async () => {
    const semSinal = visaoDeBrunoComSinal({ versao: 5, sinal: null });
    const recusa = new ErroApi({ status: 409, codigo: "LUGAR_OCUPADO", visao: semSinal });
    const { sessao, aviso } = naSala(visaoDeBrunoComSinal(), {
      api: { moverPeca: vi.fn(() => Promise.reject(recusa)) },
    });

    await sessao.moverPara("lanterna", "R1");

    expect(aviso.avisar).toHaveBeenLastCalledWith("Ana acabou de usar este lugar. Escolha outro.", "erro");
    expect(sessao.obter()).toMatchObject({ tela: "montagem", visao: { sinal: null } });
  });

  it("sinalSemDestinoSugerivelEIgnorado", async () => {
    const { sessao, api, aviso } = naSala(visao({ pecas: [{ ...BANCO_EM_C2, destinosSugeriveis: [] }] }));

    await sessao.escolherPeca("banco", false);

    expect(api.sinalizar).not.toHaveBeenCalled();
    expect(aviso.avisar).not.toHaveBeenCalled();
  });

  it("segundaAcaoDuranteEnvioEIgnorada", () => {
    const { sessao, api } = naSala(visao({ confirmacao: { podeConfirmar: true, faltam: 0 } }));

    void sessao.confirmar();
    void sessao.confirmar();

    expect(api.marcarPronto).toHaveBeenCalledExactlyOnceWith(3, true);
    expect(sessao.obter()).toMatchObject({ enviando: "pronto", instrucao: "Enviando…" });
  });

  it("sairComErroDeRedeReabreOPolling", async () => {
    const { sessao, polling, aviso } = naSala(visao(), {
      api: { sair: vi.fn(() => Promise.reject(new ErroApi({ status: 0, codigo: "REDE" }))) },
    });

    await sessao.encerrar();

    expect(sessao.obter()).toMatchObject({ tela: "montagem", enviando: null });
    expect(polling.parar).toHaveBeenCalledOnce();
    expect(polling.iniciar).toHaveBeenCalledTimes(2);
    expect(aviso.avisar).toHaveBeenLastCalledWith(
      "Sem conexão com o servidor. Confira se ele está rodando e tente de novo.",
      "erro",
    );
  });

  it("conviteDoMesmoNavegadorAvisa", () => {
    const { sessao, doPolling, aviso } = montar({ convite: "abc" });
    sessao.iniciar();

    doPolling().aoReceber(visao({ fase: "AGUARDANDO_PAR", parceiro: null, convite: "abc" }));

    expect(aviso.avisar).toHaveBeenCalledWith(CONVITE_PROPRIO);
    expect(sessao.obter()).toMatchObject({ tela: "convite" });
  });

  it("noToqueAInstrucaoNaoCitaEsc", async () => {
    const { sessao } = naSala(visao(), { ehToque: () => true });

    await sessao.escolherPeca("telescopio", true);

    expect(sessao.obter()).toMatchObject({
      instrucao: "Escolha onde o telescópio fica: na área de observação, no centro.",
    });
  });

  it("proprioConviteAvisaSemTrocar", () => {
    const { sessao, api, aviso } = naSala(visao({ fase: "AGUARDANDO_PAR", parceiro: null, convite: "abc" }));

    sessao.receberConvite("abc");

    expect(aviso.avisar).toHaveBeenCalledWith(CONVITE_PROPRIO);
    expect(sessao.obter()).toMatchObject({ tela: "convite" });
    expect(api.sair).not.toHaveBeenCalled();
  });

  it("conviteJaVistoContinuaProprioDepoisQueOParceiroEntra", () => {
    const { sessao, doPolling, aviso } = naSala(visao({ fase: "AGUARDANDO_PAR", parceiro: null, convite: "abc" }));
    doPolling().aoReceber(visao({ versao: 4 }));

    sessao.receberConvite("abc");

    expect(aviso.avisar).toHaveBeenLastCalledWith(CONVITE_PROPRIO);
    expect(sessao.obter()).toMatchObject({ tela: "montagem" });
  });

  it("conviteJaUsadoNestaAbaNaoSai", async () => {
    const deBruno = visao({
      voce: { assento: "B", nome: "Bruno" },
      parceiro: { assento: "A", nome: "Ana", conectado: true },
    });
    const { sessao, api, doPolling, aviso } = montar({
      convite: "xyz",
      api: { entrar: vi.fn(() => Promise.resolve(resposta(deBruno))) },
    });
    sessao.iniciar();
    doPolling().aoFalhar(new ErroApi({ status: 401, codigo: "SEM_ASSENTO" }), null);
    await sessao.enviarApelido("Bruno");

    sessao.receberConvite("xyz");

    expect(api.sair).not.toHaveBeenCalled();
    expect(api.entrar).toHaveBeenCalledOnce();
    expect(aviso.avisar).toHaveBeenLastCalledWith(CONVITE_PROPRIO);
    expect(sessao.obter()).toMatchObject({ tela: "montagem" });
  });

  it("conviteDeOutraSalaMostraATrocaEOPollingNaoATira", () => {
    const { sessao, doPolling, polling } = naSala();
    sessao.consumirFoco();

    sessao.receberConvite("outra");
    doPolling().aoReceber(visao({ versao: 4 }));

    expect(sessao.obter()).toMatchObject({ tela: "entrada", comConvite: true, trocaDeSala: true, erroApelido: "" });
    expect(sessao.consumirFoco()).toEqual({ alvo: "apelido" });
    expect(polling.parar).not.toHaveBeenCalled();
  });

  it("conviteDeOutraSalaSaiEEntra", async () => {
    const nova = visao({ versao: 1, parceiro: { assento: "A", nome: "Carla", conectado: true } });
    const { sessao, api, aviso } = naSala(visao(), {
      api: {
        sair: vi.fn(() => Promise.resolve(resposta(null, null))),
        entrar: vi.fn(() => Promise.resolve(resposta(nova))),
      },
    });
    sessao.receberConvite("outra");

    await sessao.enviarApelido("Ana");

    expect(api.entrar).toHaveBeenCalledWith("outra", "Ana");
    expect(vi.mocked(api.sair).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(api.entrar).mock.invocationCallOrder[0] ?? 0,
    );
    expect(sessao.obter()).toMatchObject({ tela: "montagem", enviando: null });
    expect(aviso.avisar).toHaveBeenLastCalledWith("Você entrou no terraço de Carla.");
  });

  it("trocaEntraMesmoSeASalaAtualJaAcabou", async () => {
    const { sessao, api } = naSala(visao(), {
      api: {
        sair: vi.fn(() => Promise.reject(ENCERRADA_PELO_PARCEIRO)),
        entrar: vi.fn(() => Promise.resolve(resposta(visao({ versao: 1 })))),
      },
    });
    sessao.receberConvite("outra");

    await sessao.enviarApelido("Ana");

    expect(api.entrar).toHaveBeenCalledWith("outra", "Ana");
  });

  it.each([
    ["410", ENCERRADA_PELO_PARCEIRO],
    ["404", new ErroApi({ status: 404, codigo: "SALA_INEXISTENTE" })],
  ])("salaAtualQueAcabaDuranteATrocaAvisaSemErroGenerico (%s)", (_status, erro) => {
    const { sessao, doPolling, aviso } = naSala();
    sessao.receberConvite("outra");

    doPolling().aoFalhar(erro, null);

    expect(aviso.avisar).toHaveBeenLastCalledWith("O terraço anterior terminou. Você pode entrar no convite.");
    expect(sessao.obter()).toMatchObject({ tela: "entrada", comConvite: true, trocaDeSala: false });
  });

  it("trocaComSaidaRecusadaFicaNaTrocaEReabreOPolling", async () => {
    const { sessao, api, aviso, polling } = naSala(visao(), {
      api: { sair: vi.fn(() => Promise.reject(new ErroApi({ status: 0, codigo: "REDE" }))) },
    });
    sessao.receberConvite("outra");

    await sessao.enviarApelido("Ana");

    expect(api.entrar).not.toHaveBeenCalled();
    expect(sessao.obter()).toMatchObject({ tela: "entrada", trocaDeSala: true, enviando: null });
    expect(polling.iniciar).toHaveBeenCalledTimes(2);
    expect(aviso.avisar).toHaveBeenLastCalledWith(expect.any(String), "erro");
  });

  it("conviteProprioDuranteATrocaDesfazATroca", () => {
    const { sessao, doPolling, aviso } = naSala(visao({ fase: "AGUARDANDO_PAR", parceiro: null, convite: "abc" }));
    doPolling().aoReceber(visao({ versao: 4 }));
    sessao.receberConvite("outra");

    sessao.receberConvite("abc");

    expect(aviso.avisar).toHaveBeenLastCalledWith(CONVITE_PROPRIO);
    expect(sessao.obter()).toMatchObject({ tela: "montagem" });
  });

  it("continuarNoTerracoDesisteDaTroca", async () => {
    const { sessao, api } = naSala();
    sessao.receberConvite("outra");

    sessao.continuarNoTerraco();

    expect(sessao.obter()).toMatchObject({ tela: "montagem" });
    await sessao.escolherPeca("telescopio", true);
    expect(sessao.obter()).toMatchObject({ selecionada: "telescopio" });
    expect(api.sair).not.toHaveBeenCalled();
  });

  it("conviteSemSessaoMostraAEntradaComConvite", () => {
    const { sessao, doPolling } = montar();
    sessao.iniciar();
    doPolling().aoFalhar(new ErroApi({ status: 401, codigo: "SEM_ASSENTO" }), null);

    sessao.receberConvite("abc");

    expect(sessao.obter()).toMatchObject({ tela: "entrada", comConvite: true, trocaDeSala: false });
  });

  it("queda transitória avisa com detalhe e a volta avisa uma vez", () => {
    const { sessao, doPolling, aviso } = montar();
    sessao.iniciar();

    doPolling().aoFalhar(new ErroApi({ status: 0, codigo: "REDE" }), 4000);
    doPolling().aoRecuperar();
    doPolling().aoRecuperar();

    expect(aviso.avisarComDetalhe).toHaveBeenCalledWith(
      "Sem conexão com o servidor.",
      "Nova tentativa em 4 s.",
      "erro",
    );
    expect(aviso.avisar.mock.calls).toEqual([["Conexão de volta."]]);
  });

  it("sala encerrada pelo parceiro despede com o nome dele, sem dizer quem saiu", () => {
    const { sessao, doPolling } = naSala();

    doPolling().aoFalhar(new ErroApi({ status: 410, codigo: "SALA_ENCERRADA", motivo: "SAIU" }), null);

    expect(sessao.obter()).toMatchObject({
      tela: "encerrado",
      titulo: "O encontro terminou",
      texto: "Que bom montar o terraço com Bruno.",
    });
  });

  it("criar a sala começa a sessão com o ETag da resposta", async () => {
    const criada = visao({ fase: "AGUARDANDO_PAR", parceiro: null, convite: "abc" });
    const { sessao, doPolling, polling } = montar({
      api: { criarSala: vi.fn(() => Promise.resolve(resposta(criada, '"v1"'))) },
    });
    sessao.iniciar();
    doPolling().aoFalhar(new ErroApi({ status: 401, codigo: "SEM_ASSENTO" }), null);

    await sessao.enviarApelido("Ana");

    expect(sessao.obter()).toMatchObject({ tela: "convite", enviando: null });
    expect(polling.aceitar).toHaveBeenLastCalledWith('"v1"');
    expect(polling.iniciar).toHaveBeenCalledTimes(2);
  });

  it("apelido recusado pelo servidor marca o erro e pede foco no campo", async () => {
    const { sessao, doPolling } = montar({
      api: { criarSala: vi.fn(() => Promise.reject(new ErroApi({ status: 400, codigo: "APELIDO_INVALIDO" }))) },
    });
    sessao.iniciar();
    doPolling().aoFalhar(new ErroApi({ status: 401, codigo: "SEM_ASSENTO" }), null);
    sessao.consumirFoco();

    await sessao.enviarApelido("Ana");

    expect(sessao.obter()).toMatchObject({
      tela: "entrada",
      enviando: null,
      erroApelido: "Use de 1 a 20 caracteres no apelido.",
    });
    expect(sessao.consumirFoco()).toEqual({ alvo: "apelido" });
  });

  it("abrir e fechar a confirmação de saída move o foco", () => {
    const { sessao } = naSala();

    sessao.abrirSaida();
    const aberta = sessao.obter().confirmandoSaida;
    const focoAoAbrir = sessao.consumirFoco();
    sessao.fecharSaida();

    expect([aberta, focoAoAbrir]).toEqual([true, { alvo: "continuar" }]);
    expect(sessao.obter().confirmandoSaida).toBe(false);
    expect(sessao.consumirFoco()).toEqual({ alvo: "sair" });
  });

  it("Esc cancela a seleção e devolve o foco ao item", async () => {
    const { sessao } = naSala();
    await sessao.escolherPeca("manta", true);

    sessao.cancelarSelecao();

    expect(sessao.obter()).toMatchObject({ selecionada: null });
    expect(sessao.consumirFoco()).toEqual({ alvo: "item", peca: "manta" });
  });

  it("o som alterna e desliga quando a sessão termina", () => {
    const { sessao, doPolling } = naSala();

    sessao.alternarSom();
    const ligado = sessao.obter().somLigado;
    doPolling().aoFalhar(new ErroApi({ status: 410, codigo: "SALA_ENCERRADA", motivo: "EXPIROU" }), null);

    expect([ligado, sessao.obter().somLigado]).toEqual([true, false]);
  });

  it("nova tentativa de entrar limpa o erro do apelido", async () => {
    const respostas = [
      () => Promise.reject(new ErroApi({ status: 400, codigo: "APELIDO_INVALIDO" })),
      () => Promise.reject(new ErroApi({ status: 0, codigo: "REDE" })),
    ];
    const { sessao, doPolling } = montar({
      api: { criarSala: vi.fn(() => respostas.shift()?.() ?? pendente()) },
    });
    sessao.iniciar();
    doPolling().aoFalhar(new ErroApi({ status: 401, codigo: "SEM_ASSENTO" }), null);
    await sessao.enviarApelido("");

    await sessao.enviarApelido("Ana");

    expect(sessao.obter()).toMatchObject({ tela: "entrada", erroApelido: "" });
  });

  it("limparErroApelido apaga o erro marcado pelo servidor", async () => {
    const { sessao, doPolling } = montar({
      api: { criarSala: vi.fn(() => Promise.reject(new ErroApi({ status: 400, codigo: "APELIDO_INVALIDO" }))) },
    });
    sessao.iniciar();
    doPolling().aoFalhar(new ErroApi({ status: 401, codigo: "SEM_ASSENTO" }), null);
    await sessao.enviarApelido("");

    sessao.limparErroApelido();

    expect(sessao.obter()).toMatchObject({ tela: "entrada", erroApelido: "" });
  });

  it("parar desliga o polling", () => {
    const { sessao, polling } = montar();
    sessao.iniciar();

    sessao.parar();

    expect(polling.parar).toHaveBeenCalledOnce();
  });

  it("409 sem visão redesenha e consulta a sala na hora", async () => {
    const recusa = new ErroApi({ status: 409, codigo: "VERSAO_OBSOLETA" });
    const { sessao, aviso, polling } = naSala(visao(), {
      api: { moverPeca: vi.fn(() => Promise.reject(recusa)) },
    });
    await sessao.escolherPeca("telescopio", true);

    await sessao.escolherLugar("C1", null);

    expect(polling.consultarAgora).toHaveBeenCalledOnce();
    expect(sessao.obter()).toMatchObject({ tela: "montagem", enviando: null });
    expect(aviso.avisar).toHaveBeenLastCalledWith("A composição mudou; veja a versão atual.", "erro");
  });

  it("409 com visão em que a peça deixou de estar selecionável devolve o foco ao item", async () => {
    const resultado = { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "esquerda" };
    const recusa = new ErroApi({ status: 409, codigo: "FASE_INVALIDA", visao: visao({ fase: "REVELADO", resultado }) });
    const { sessao } = naSala(visao(), { api: { moverPeca: vi.fn(() => Promise.reject(recusa)) } });
    await sessao.escolherPeca("telescopio", true);
    sessao.consumirFoco();

    await sessao.escolherLugar("C1", null);

    expect(sessao.consumirFoco()).toEqual({ alvo: "item", peca: "telescopio" });
  });

  it("sair com erro fora da API reabre o polling e avisa", async () => {
    const { sessao, polling, aviso } = naSala(visao(), {
      api: { sair: vi.fn(() => Promise.reject(new TypeError("quebrou"))) },
    });

    await sessao.encerrar();

    expect(sessao.obter()).toMatchObject({ tela: "montagem", enviando: null });
    expect(polling.iniciar).toHaveBeenCalledTimes(2);
    expect(aviso.avisar).toHaveBeenLastCalledWith("O servidor não aceitou o pedido (0). Tente de novo.", "erro");
  });

  it("ação aceita sem a visão na resposta solta o envio, avisa e propaga o defeito", async () => {
    const { sessao, aviso } = naSala(visao({ confirmacao: { podeConfirmar: true, faltam: 0 } }), {
      api: { marcarPronto: vi.fn(() => Promise.resolve(resposta(null))) },
    });
    const ouvinte = vi.fn();
    sessao.assinar(ouvinte);

    await expect(sessao.confirmar()).rejects.toThrow("Resposta da API sem a visão da sala");

    expect(sessao.obter()).toMatchObject({ tela: "montagem", enviando: null });
    expect(ouvinte).toHaveBeenCalledTimes(2);
    expect(aviso.avisar).toHaveBeenLastCalledWith(
      "Algo deu errado ao falar com o servidor. Recarregue a página.",
      "erro",
    );
  });

  it("ação em voo que responde depois de a sessão encerrar é descartada e não trava o próximo clique", async () => {
    const pronto = adiada();
    const criada = visao({ fase: "AGUARDANDO_PAR", parceiro: null, convite: "abc" });
    const { sessao, doPolling, polling, api } = naSala(visao({ confirmacao: { podeConfirmar: true, faltam: 0 } }), {
      api: {
        marcarPronto: vi.fn(() => pronto.promessa),
        criarSala: vi.fn(() => Promise.resolve(resposta(criada, '"v1"'))),
      },
    });
    const envio = sessao.confirmar();
    doPolling().aoFalhar(ENCERRADA_PELO_PARCEIRO, null);
    expect(sessao.obter()).toMatchObject({ tela: "encerrado", enviando: null });
    const encerrado = sessao.obter();

    pronto.resolver(resposta(visao({ versao: 9, prontos: { voce: true, parceiro: false } }), '"velho"'));
    await envio;

    expect(sessao.obter()).toBe(encerrado);
    expect(polling.aceitar).not.toHaveBeenCalledWith('"velho"');
    sessao.criarOutro();
    await sessao.enviarApelido("Ana");
    expect(api.criarSala).toHaveBeenCalledExactlyOnceWith("Ana");
    expect(sessao.obter()).toMatchObject({ tela: "convite", enviando: null });
  });

  it("recusa com visão de uma ação da sessão encerrada não entra na sessão nova", async () => {
    const pronto = adiada();
    const criada = visao({ fase: "AGUARDANDO_PAR", parceiro: null, convite: "abc" });
    const { sessao, doPolling, aviso } = naSala(visao({ confirmacao: { podeConfirmar: true, faltam: 0 } }), {
      api: {
        marcarPronto: vi.fn(() => pronto.promessa),
        criarSala: vi.fn(() => Promise.resolve(resposta(criada, '"v1"'))),
      },
    });
    const envio = sessao.confirmar();
    doPolling().aoFalhar(ENCERRADA_PELO_PARCEIRO, null);
    sessao.criarOutro();
    await sessao.enviarApelido("Ana");
    const novaSessao = sessao.obter();
    aviso.avisar.mockClear();

    pronto.rejeitar(new ErroApi({ status: 409, codigo: "VERSAO_OBSOLETA", visao: visao({ versao: 9 }) }));
    await envio;

    expect(sessao.obter()).toBe(novaSessao);
    expect(aviso.avisar).not.toHaveBeenCalled();
  });

  it("erro fora da API no polling mantém a tela, apaga a noite e o som e avisa", () => {
    const resultado = { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "esquerda" };
    const { sessao, doPolling, aviso } = naSala(visao({ fase: "REVELADO", resultado }));
    sessao.alternarSom();

    doPolling().aoFalhar(new TypeError("quebrou"), null);

    expect(sessao.obter()).toMatchObject({
      tela: "montagem",
      somLigado: false,
      momento: "crepusculo",
      ladoTelescopio: null,
    });
    expect(aviso.avisar).toHaveBeenLastCalledWith(
      "Algo deu errado ao falar com o servidor. Recarregue a página.",
      "erro",
    );
  });

  it("depois de um erro fora da API, a tela mostrada continua a mesma nas mudanças seguintes", () => {
    const { sessao, doPolling } = naSala();
    doPolling().aoFalhar(new TypeError("quebrou"), null);

    sessao.alternarSom();
    sessao.abrirSaida();

    expect(sessao.obter()).toMatchObject({ tela: "montagem", somLigado: true, confirmandoSaida: true });
  });

  it("destaqueSaiDepoisDoTempo", () => {
    const agenda = agendaFalsa();
    const { sessao, doPolling } = naSala(visao(), { agendar: agenda.agendar });

    doPolling().aoReceber(comBancoEm("C2"));
    expect(destaquesDe(sessao.obter())).toEqual(["banco"]);

    agenda.avancar(DURACAO_DESTAQUE_MS - 1);
    expect(destaquesDe(sessao.obter())).toEqual(["banco"]);
    agenda.avancar(1);
    expect(destaquesDe(sessao.obter())).toEqual([]);
  });

  it("destaqueTambemNaRespostaDaPropriaAcao", async () => {
    const agenda = agendaFalsa();
    const telescopioEmC1 = { peca: "telescopio", assento: "A", lugar: "C1" } as const;
    const aceita = comBancoEm("C2");
    aceita.pecas = aceita.pecas.map((p) => (p.peca === "telescopio" ? { ...p, ...telescopioEmC1 } : p));
    const { sessao } = naSala(visao(), {
      agendar: agenda.agendar,
      api: { moverPeca: vi.fn(() => Promise.resolve(resposta(aceita))) },
    });
    await sessao.escolherPeca("telescopio", true);

    await sessao.escolherLugar("C1", null);

    expect(destaquesDe(sessao.obter())).toEqual(["banco"]);
  });

  it("mesmaPecaDeNovoReiniciaOTempo", () => {
    const agenda = agendaFalsa();
    const { sessao, doPolling } = naSala(visao(), { agendar: agenda.agendar });
    doPolling().aoReceber(comBancoEm("C2"));
    agenda.avancar(2000);

    doPolling().aoReceber(comBancoEm("C1", { versao: 4 }));
    agenda.avancar(2000);
    expect(destaquesDe(sessao.obter())).toEqual(["banco"]);
    agenda.avancar(DURACAO_DESTAQUE_MS - 2000);

    expect(destaquesDe(sessao.obter())).toEqual([]);
    expect(agenda.pendentes()).toBe(0);
  });

  it("limparSessaoCancelaOsTimers", () => {
    const agenda = agendaFalsa();
    const { doPolling } = naSala(visao(), { agendar: agenda.agendar });
    doPolling().aoReceber(comBancoEm("C2"));
    expect(agenda.pendentes()).toBe(1);

    doPolling().aoFalhar(ENCERRADA_PELO_PARCEIRO, null);

    expect(agenda.pendentes()).toBe(0);
  });
});
