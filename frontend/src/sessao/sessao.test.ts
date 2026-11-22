import { describe, expect, expectTypeOf, it, vi } from "vitest";
import { type Api, ErroApi, type Resposta } from "../api/cliente";
import type { GanchosDoPolling, Polling } from "../api/polling";
import type { Visao } from "../dominio/visao";
import type { Aviso } from "./aviso";
import { criarSessao, type EstadoDaTela } from "./sessao";

const BRUNO = { assento: "B", nome: "Bruno", conectado: true } as const;

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
    ...mudancas,
  };
}

const resposta = (v: Visao | null, etag: string | null = '"v"'): Resposta => ({ naoMudou: false, visao: v, etag });
const pendente = () => new Promise<Resposta>(() => undefined);

/** Monta a sessão com api, aviso e polling falsos; o polling devolve os ganchos para o teste agir por ele. */
function montar({ api = {}, convite = null }: { api?: Partial<Api>; convite?: string | null } = {}) {
  const apiFalsa: Api = {
    criarSala: vi.fn(pendente),
    entrar: vi.fn(pendente),
    obterSala: vi.fn(pendente),
    moverPeca: vi.fn(pendente),
    sinalizar: vi.fn(pendente),
    marcarPronto: vi.fn(pendente),
    ajustar: vi.fn(pendente),
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

  it("a visão em AGUARDANDO_PAR mostra o convite", () => {
    const { sessao } = naSala(visao({ fase: "AGUARDANDO_PAR", parceiro: null, convite: "abc" }));

    expect(sessao.obter()).toMatchObject({ tela: "convite" });
  });

  it("a visão revelada passa para a noite com o lado do telescópio", () => {
    const resultado = { secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "esquerda" };
    const { sessao } = naSala(visao({ fase: "REVELADO", resultado }));

    expect(sessao.obter()).toMatchObject({ momento: "noite", ladoTelescopio: "esquerda" });
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
    expect(estado).toMatchObject({ tela: "encerrado", texto: "Você saiu e o terraço foi encerrado." });
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
    const { sessao, api, aviso } = naSala(visao(), {
      api: { sinalizar: vi.fn(() => Promise.resolve(resposta(visao({ versao: 4 })))) },
    });

    await sessao.escolherPeca("banco", false);

    expect(api.sinalizar).toHaveBeenCalledWith("banco");
    expect(aviso.avisar).toHaveBeenLastCalledWith("Você sugeriu que Bruno mova o banco.");
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

    doPolling().aoReceber(visao());

    expect(aviso.avisar).toHaveBeenCalledWith(
      "Este navegador já está em um terraço. Para usar o convite, abra-o em uma janela anônima ou em outro navegador.",
    );
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

  it("sala encerrada pelo parceiro mostra o encerrado com o nome dele", () => {
    const { sessao, doPolling } = naSala();

    doPolling().aoFalhar(new ErroApi({ status: 410, codigo: "SALA_ENCERRADA", motivo: "SAIU" }), null);

    expect(sessao.obter()).toMatchObject({ tela: "encerrado", texto: "Bruno saiu e o terraço foi encerrado." });
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
});
