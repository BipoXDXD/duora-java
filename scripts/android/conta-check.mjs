#!/usr/bin/env node
// Prova o cadastro de conta no Chrome do emulador Android (AVD duora-pixel), com o widget real do
// Turnstile.
//
// Uso: node scripts/android/conta-check.mjs --porta 8080 --log <arquivo do spring-boot:run>
//
// Pré-requisito: emulador ligado (scripts/android/emulador.sh subir) e a aplicação servindo em
// localhost:<porta> nesta máquina, com a saída do ./mvnw spring-boot:run no arquivo de --log.
//
// Passos (PASSOS): adb reverse da porta e abre /entrar no Chrome; liga pelo CDP a escuta do
// evento securitypolicyviolation e recarrega a tela, para a escuta valer desde o começo, exigindo
// o Content-Security-Policy na resposta de /entrar (sem ele, a escuta não provaria nada); espera o
// widget real do Turnstile (chaves de teste do dev) liberar o envio e captura (entrar.png); pede o
// link com um e-mail novo (o POST precisa dar 2xx) e lê o link do log do EmailNoLog; abre o
// link, entra e captura o cadastro (cadastro.png); conclui o cadastro; abre Minha conta
// (minha-conta.png) e volta à tela Cadastro recebido (aguardando.png), onde para. Resultado e
// capturas em target/android/. Sai com código 1 em qualquer falha, inclusive a cada violação da
// CSP; SIGINT e SIGTERM desfazem o reverse e o forward, gravam o resultado e saem com 130 e 143.
//
// Variáveis: as do pwa-check.mjs (ANDROID_SDK_ROOT, DUORA_AVD_PORTA, DUORA_CDP_PORTA).

import { existsSync, mkdirSync, readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

import {
  Falha, PORTA_CDP, SAIDA, abrirNoChrome, capturarTela, criarAdb, exigirEmuladorPronto, prepararChrome,
  resolverAdb,
} from './pwa-check.mjs';

const NASCIMENTO = '1990-05-10';
const BINDING_DA_CSP = 'duoraViolacaoDeCsp';
// O Turnstile real carrega da rede do emulador; o link sai pelo despachante da outbox (2 s em dev).
const ESPERA_DO_TURNSTILE_MS = 60000;
const ESPERA_DO_LINK_MS = 30000;
const ESPERA_DA_TELA_MS = 20000;
// Código de saída de quem morre pelo sinal (128 + número do sinal), como o shell reporta.
const CODIGOS_DOS_SINAIS = Object.freeze({ SIGINT: 130, SIGTERM: 143 });

const esperar = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (msg) => console.log(`==> ${msg}`);

/** O token do link mágico é base64url; o link sem token não serve. */
const LINK_DE_ENTRADA = /https?:\/\/[^\s#]+\/entrar\/confirmar#t=[A-Za-z0-9_-]+/g;

/** O último link de entrada do log de texto do EmailNoLog, ou null se não houver. */
export function ultimoLinkDoLog(texto) {
  return [...texto.matchAll(LINK_DE_ENTRADA)].at(-1)?.[0] ?? null;
}

/**
 * O link do e-mail apontado para a base da rodada: o e-mail traz a URL pública do dev
 * (127.0.0.1), não o localhost que o adb reverse leva ao emulador.
 */
export function linkNaBase(link, base) {
  const { pathname, hash } = new URL(link);
  return `${base}${pathname}${hash}`;
}

/** O valor do Content-Security-Policy nos cabeçalhos de uma resposta do CDP, ou null. */
export function cabecalhoDaCsp(cabecalhos) {
  const nome = Object.keys(cabecalhos ?? {}).find((n) => n.toLowerCase() === 'content-security-policy');
  return nome ? cabecalhos[nome] : null;
}

/** Ordem em que o main() roda os passos de EXECUTORES; a rodada para na tela Cadastro recebido. */
export const PASSOS = Object.freeze([
  'abrirEntrar', 'capturarEntrar', 'pedirLink', 'abrirLink', 'confirmarEntrada', 'capturarCadastro',
  'concluirCadastro', 'abrirMinhaConta', 'capturarMinhaConta', 'voltarAoAguardando',
  'capturarAguardando',
]);

/**
 * Os passos de PASSOS que a rodada pedida em `args` executa, na mesma ordem. O conta-check ainda
 * não tem variante de rodada: todos os passos rodam sempre.
 */
export function passosDaRodada(_args) {
  return [...PASSOS];
}

function lerArgumentos() {
  const { values } = parseArgs({
    options: { porta: { type: 'string' }, log: { type: 'string' } },
  });
  const porta = Number(values.porta);
  if (!Number.isInteger(porta) || porta <= 0 || porta > 65535 || !values.log) {
    console.error('uso: node scripts/android/conta-check.mjs --porta <n> --log <arquivo do spring-boot:run>');
    process.exit(2);
  }
  return { porta, log: values.log };
}

// ---- Página pelo CDP -------------------------------------------------------

/** Ajudantes avaliados na página: acham elementos como a pessoa os vê, por texto e rótulo. */
const AJUDANTES = `
  const texto = (el) => (el.textContent ?? '').replace(/\\s+/g, ' ').trim();
  const titulo = (nome) => [...document.querySelectorAll('h1')].find((h) => texto(h) === nome);
  const botao = (nome) => [...document.querySelectorAll('button')].find((b) => texto(b) === nome);
  const link = (nome) => [...document.querySelectorAll('a')].find((a) => texto(a) === nome);
  const campo = (rotulo) => [...document.querySelectorAll('label')]
    .find((l) => texto(l).startsWith(rotulo))?.control;
  // O React escuta o input nativo: o valor passa pelo setter do protótipo, não pela propriedade.
  const preencher = (el, valor) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, valor);
    el.dispatchEvent(new Event('input', { bubbles: true }));
  };
`;

/**
 * A sessão da aba, com a escuta da CSP: cada securitypolicyviolation da página chama o binding,
 * e a próxima avaliação falha com a violação. Guarda também as respostas de rede, para conferir
 * cabeçalhos e status.
 */
async function abrirPagina(cdp) {
  const violacoes = [];
  const respostas = [];
  cdp.ouvir('Runtime.bindingCalled', ({ name, payload }) => {
    if (name === BINDING_DA_CSP) violacoes.push(payload);
  });
  cdp.ouvir('Network.responseReceived', ({ type, response }) => {
    respostas.push({ tipo: type, url: response.url, status: response.status, cabecalhos: response.headers });
  });
  await cdp.enviar('Network.enable');
  await cdp.enviar('Runtime.enable');
  await cdp.enviar('Page.enable');
  await cdp.enviar('Runtime.addBinding', { name: BINDING_DA_CSP });
  await cdp.enviar('Page.addScriptToEvaluateOnNewDocument', {
    source: `addEventListener('securitypolicyviolation', (e) => ${BINDING_DA_CSP}(JSON.stringify({
      diretiva: e.effectiveDirective, bloqueado: e.blockedURI, arquivo: e.sourceFile })));`,
  });
  const exigirSemViolacao = () => {
    if (violacoes.length) throw new Falha(`violação da CSP no Chrome: ${violacoes.join('; ')}`);
  };
  return {
    violacoes,
    async avaliar(expressao) {
      exigirSemViolacao();
      const { result, exceptionDetails } = await cdp.enviar('Runtime.evaluate', {
        expression: `(() => { ${AJUDANTES} return (${expressao}); })()`, returnByValue: true,
      });
      if (exceptionDetails) {
        throw new Falha(`avaliação na página falhou: ${exceptionDetails.exception?.description ?? exceptionDetails.text}`);
      }
      return result.value;
    },
    async navegar(url) {
      exigirSemViolacao();
      await cdp.enviar('Page.navigate', { url });
    },
    /** Marca o ponto a partir do qual esperarResposta procura. */
    marca: () => respostas.length,
    async esperarResposta(desde, criterio, descricao) {
      const limite = Date.now() + ESPERA_DA_TELA_MS;
      do {
        const resposta = respostas.slice(desde).find(criterio);
        if (resposta) return resposta;
        await esperar(250);
      } while (Date.now() < limite);
      throw new Falha(`${descricao} não chegou em ${ESPERA_DA_TELA_MS / 1000}s`);
    },
    exigirSemViolacao,
  };
}

/** Espera a expressão dar verdadeiro; a troca de documento no meio da navegação só repete. */
async function esperarNaPagina(pagina, expressao, descricao, timeoutMs = ESPERA_DA_TELA_MS) {
  const limite = Date.now() + timeoutMs;
  let ultimoErro;
  do {
    try {
      if (await pagina.avaliar(expressao)) return;
    } catch (e) {
      if (e instanceof Falha && e.message.startsWith('violação da CSP')) throw e;
      ultimoErro = e;
    }
    await esperar(500);
  } while (Date.now() < limite);
  throw new Falha(`${descricao} não apareceu em ${timeoutMs / 1000}s${ultimoErro ? ` (${ultimoErro.message})` : ''}`);
}

const esperarTitulo = (pagina, nome) => esperarNaPagina(pagina, `!!titulo(${JSON.stringify(nome)})`, `a tela "${nome}"`);

async function clicar(pagina, achar, descricao) {
  if (!(await pagina.avaliar(`(() => { const el = ${achar}; el?.click(); return !!el; })()`))) {
    throw new Falha(`${descricao} não encontrado`);
  }
}

// ---- Log do spring-boot:run ------------------------------------------------

const tamanhoDoLog = (arquivo) => readFileSync(arquivo).length;

/** O link do pedido feito depois de `inicio` (tamanho do log antes do pedido), na base da rodada. */
async function esperarLinkNoLog(arquivo, inicio, base) {
  const limite = Date.now() + ESPERA_DO_LINK_MS;
  do {
    const link = ultimoLinkDoLog(readFileSync(arquivo).subarray(inicio).toString('utf8'));
    if (link) {
      return linkNaBase(link, base);
    }
    await esperar(1000);
  } while (Date.now() < limite);
  throw new Falha(`nenhum link de entrada novo em ${arquivo} em ${ESPERA_DO_LINK_MS / 1000}s`);
}

// ---- Fluxo -----------------------------------------------------------------

async function capturar({ adb, pagina, resultado }, nome) {
  // Deixa a tela assentar (foco, fontes) antes da captura.
  await esperar(1000);
  pagina.exigirSemViolacao();
  resultado.capturas[nome] = capturarTela(adb, join(SAIDA, `${nome}.png`));
  log(`captura: ${resultado.capturas[nome]}`);
}

// Cada executor recebe o contexto da rodada e pode devolver campos novos para os passos seguintes.
const EXECUTORES = {
  async abrirEntrar({ pagina, base }) {
    const desde = pagina.marca();
    await pagina.navegar(`${base}/entrar`);
    const documento = await pagina.esperarResposta(desde,
      (r) => r.tipo === 'Document' && new URL(r.url).pathname === '/entrar', 'a resposta de /entrar');
    // Controle negativo: sem o cabeçalho, nenhuma violação dispara e a escuta passaria em branco.
    if (!cabecalhoDaCsp(documento.cabecalhos)) {
      throw new Falha('a resposta de /entrar veio sem Content-Security-Policy: a prova da CSP não vale');
    }
    await esperarTitulo(pagina, 'Entrar no Duora');
    log('esperando o Turnstile liberar o envio');
    await esperarNaPagina(pagina, "botao('Enviar link')?.getAttribute('aria-disabled') !== 'true'"
      + " && !!botao('Enviar link')", 'o botão Enviar link liberado pelo Turnstile', ESPERA_DO_TURNSTILE_MS);
  },
  capturarEntrar: (contexto) => capturar(contexto, 'entrar'),
  async pedirLink({ pagina, args, base, resultado }) {
    const email = `android-${Date.now()}@exemplo.invalid`;
    const inicio = tamanhoDoLog(args.log);
    await pagina.avaliar(`(() => { preencher(campo('Seu e-mail'), ${JSON.stringify(email)}); return true; })()`);
    const desde = pagina.marca();
    await clicar(pagina, "botao('Enviar link')", 'botão Enviar link');
    const { status } = await pagina.esperarResposta(desde,
      (r) => new URL(r.url).pathname === '/api/conta/entrar', 'a resposta do pedido do link');
    if (status < 200 || status >= 300) throw new Falha(`o pedido do link voltou ${status}`);
    log(`link pedido para ${email}`);
    resultado.email = email;
    return { link: await esperarLinkNoLog(args.log, inicio, base) };
  },
  async abrirLink({ pagina, link }) {
    await pagina.navegar(link);
    await esperarTitulo(pagina, 'Confirmar entrada');
  },
  async confirmarEntrada({ pagina }) {
    await clicar(pagina, "botao('Entrar')", 'botão Entrar');
    await esperarTitulo(pagina, 'Falta pouco');
  },
  capturarCadastro: (contexto) => capturar(contexto, 'cadastro'),
  async concluirCadastro({ pagina }) {
    await pagina.avaliar(`(() => { preencher(campo('Data de nascimento'), '${NASCIMENTO}'); return true; })()`);
    await clicar(pagina, "campo('Li e aceito os Termos de uso')", 'aceite dos termos');
    await clicar(pagina, "botao('Concluir cadastro')", 'botão Concluir cadastro');
    await esperarTitulo(pagina, 'Cadastro recebido');
  },
  async abrirMinhaConta({ pagina }) {
    await clicar(pagina, "link('Minha conta')", 'link Minha conta');
    await esperarTitulo(pagina, 'Minha conta');
  },
  capturarMinhaConta: (contexto) => capturar(contexto, 'minha-conta'),
  async voltarAoAguardando({ pagina }) {
    await pagina.avaliar('(() => { history.back(); return true; })()');
    await esperarTitulo(pagina, 'Cadastro recebido');
  },
  capturarAguardando: (contexto) => capturar(contexto, 'aguardando'),
};

async function main() {
  const args = lerArgumentos();
  const adb = criarAdb(resolverAdb());
  const base = `http://localhost:${args.porta}`;
  mkdirSync(SAIDA, { recursive: true });
  const resultado = { base, capturas: {} };
  let cdp;

  if (!existsSync(args.log)) throw new Falha(`log do spring-boot:run não encontrado: ${args.log}`);
  exigirEmuladorPronto(adb);

  let limpo = false;
  // Roda uma vez só: no fim da rodada ou num sinal, o que vier primeiro.
  const limpar = () => {
    if (limpo) return;
    limpo = true;
    cdp?.fechar();
    try { adb.executar(['forward', '--remove', `tcp:${PORTA_CDP}`]); } catch { /* já removido */ }
    try { adb.executar(['reverse', '--remove', `tcp:${args.porta}`]); } catch { /* já removido */ }
    writeFileSync(join(SAIDA, 'conta-check.json'), `${JSON.stringify(resultado, null, 2)}\n`);
    log(`resultado: ${join(SAIDA, 'conta-check.json')}`);
  };
  // Ctrl+C ou kill no meio da rodada: desfaz o reverse e o forward e grava o resultado com o motivo.
  for (const [sinal, codigo] of Object.entries(CODIGOS_DOS_SINAIS)) {
    process.once(sinal, () => {
      resultado.ok = false;
      resultado.erro = `interrompido por ${sinal}`;
      limpar();
      process.exit(codigo);
    });
  }

  try {
    prepararChrome(adb, true);
    adb.executar(['reverse', `tcp:${args.porta}`, `tcp:${args.porta}`]);
    cdp = await abrirNoChrome(adb, `${base}/entrar`, base);
    resultado.chrome = (await cdp.enviar('Browser.getVersion').catch(() => ({}))).product;
    const pagina = await abrirPagina(cdp);
    let contexto = { args, adb, base, resultado, pagina };
    for (const passo of passosDaRodada(args)) {
      log(`passo: ${passo}`);
      contexto = { ...contexto, ...(await EXECUTORES[passo](contexto)) };
    }
    // Um último olhar: uma violação depois da última captura também derruba a rodada.
    await esperar(1000);
    pagina.exigirSemViolacao();
    resultado.ok = true;
  } catch (e) {
    resultado.ok = false;
    resultado.erro = e.message;
    try { resultado.capturaDaFalha = capturarTela(adb, join(SAIDA, 'conta-falha.png')); } catch { /* sem tela */ }
    throw e;
  } finally {
    limpar();
  }
}

// Importado pelos testes, o módulo só expõe as funções; o fluxo roda quando executado direto.
if (process.argv[1] && realpathSync(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error(`erro: ${e instanceof Falha ? e.message : e.stack}`);
    process.exit(1);
  });
}
