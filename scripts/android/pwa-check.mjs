#!/usr/bin/env node
// Verifica a instalação da PWA no Chrome do emulador Android (AVD duora-pixel).
//
// Uso: node scripts/android/pwa-check.mjs --porta 8080 [--nome pwa] [--caminho /]
//        [--sem-instalar] [--manter-chrome] [--exigir-webapk]
//
// Pré-requisito: emulador ligado (scripts/android/emulador.sh subir) e a aplicação
// servindo em localhost:<porta> nesta máquina.
//
// Passos (PASSOS): adb reverse da porta e abre a URL no Chrome; consulta pelo CDP o manifest e
// Page.getInstallabilityErrors; confere que o terraço em pé usa a largura 1800 (currentSrc +
// escolherVariante do app) e só então captura a tela em pé (<nome>-em-pe.png); captura a tela
// deitada (<nome>-deitado.png) e devolve a rotação de antes; instala pelo menu "Install app" e
// confirma WebAPK (pacote org.chromium.webapk.*) ou atalho fixado no launcher; abre o app
// instalado, exige display-mode standalone sem a barra do navegador e captura
// (<nome>-standalone.png); captura a tela inicial (<nome>-home.png). Resultado e capturas em
// target/android/. Sai com código 1 em qualquer falha.
//
// Sem conta Google no emulador, a Play Store recusa instalar o WebAPK e o Chrome cai
// para atalho fixado; por isso o padrão aceita atalho e --exigir-webapk o recusa.
//
// Variáveis: ANDROID_SDK_ROOT (ou ANDROID_HOME), DUORA_AVD_PORTA (padrão 5580),
// DUORA_CDP_PORTA (porta local do DevTools, padrão 9333).

import { execFileSync } from 'node:child_process';
import { mkdirSync, realpathSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const CHROME = 'com.android.chrome';
const RAIZ_REPO = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const SAIDA = join(RAIZ_REPO, 'target', 'android');
const SERIAL = `emulator-${process.env.DUORA_AVD_PORTA ?? '5580'}`;
const PORTA_CDP = Number(process.env.DUORA_CDP_PORTA ?? '9333');
const TEXTOS_FECHAR_PROMO = ['No thanks', 'No, thanks', 'Not now', 'Got it'];
// "Add to home screen" também aparece em páginas não instaláveis; só "Install app" indica PWA.
const TEXTO_MENU_INSTALAR = 'Install app';
const TEXTOS_CONFIRMAR_LAUNCHER = ['Add to home screen', 'Add automatically', 'Add'];
// Android 13 (API 33) criou a permissão POST_NOTIFICATIONS.
const SDK_COM_PERMISSAO_DE_NOTIFICACAO = 33;

class Falha extends Error {}

const esperar = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (msg) => console.log(`==> ${msg}`);

function lerArgumentos() {
  const { values } = parseArgs({
    options: {
      porta: { type: 'string' },
      nome: { type: 'string', default: 'pwa' },
      caminho: { type: 'string', default: '/' },
      'sem-instalar': { type: 'boolean', default: false },
      'manter-chrome': { type: 'boolean', default: false },
      'exigir-webapk': { type: 'boolean', default: false },
    },
  });
  const porta = Number(values.porta);
  if (!Number.isInteger(porta) || porta <= 0 || porta > 65535) {
    console.error('uso: node scripts/android/pwa-check.mjs --porta <n> [--nome pwa] [--caminho /] '
      + '[--sem-instalar] [--manter-chrome] [--exigir-webapk]');
    process.exit(2);
  }
  if (!/^[\w.-]+$/.test(values.nome)) throw new Falha(`--nome inválido: ${values.nome}`);
  return { ...values, porta };
}

function resolverAdb() {
  let sdk = process.env.ANDROID_SDK_ROOT ?? process.env.ANDROID_HOME;
  if (!sdk) {
    try {
      sdk = join(execFileSync('brew', ['--prefix'], { encoding: 'utf8' }).trim(),
        'share', 'android-commandlinetools');
    } catch {
      throw new Falha('defina ANDROID_SDK_ROOT (Homebrew não encontrado para descobrir o SDK)');
    }
  }
  return join(sdk, 'platform-tools', 'adb');
}

function criarAdb(binario) {
  const executar = (args, opcoes = {}) => {
    try {
      return execFileSync(binario, ['-s', SERIAL, ...args],
        { encoding: opcoes.binario ? 'buffer' : 'utf8', maxBuffer: 64 * 1024 * 1024,
          stdio: ['ignore', 'pipe', 'pipe'] });
    } catch (e) {
      throw new Falha(`adb ${args.join(' ')} falhou: ${(e.stderr ?? e.message).toString().trim()}`);
    }
  };
  const shell = (comando) => executar(['shell', comando]);
  return { executar, shell };
}

function exigirEmuladorPronto(adb) {
  let pronto = false;
  try { pronto = adb.shell('getprop sys.boot_completed').trim() === '1'; } catch { /* desligado */ }
  if (!pronto) throw new Falha(`emulador ${SERIAL} não está pronto; rode: scripts/android/emulador.sh subir`);
}

export function prepararChrome(adb, limpar) {
  if (limpar) adb.shell(`pm clear ${CHROME}`);
  // Chrome lê este arquivo só quando é o "debug app"; pula o first run e o aviso de navegador padrão,
  // e desliga a tradução, cujo pop-up "Translate page?" entrava na captura standalone.
  adb.shell('echo "_ --disable-fre --no-default-browser-check --no-first-run --disable-features=Translate"'
    + ' > /data/local/tmp/chrome-command-line');
  adb.shell(`am set-debug-app --persistent ${CHROME}`);
  // Sem a permissão, o Chrome recém-limpo abre o aviso "Chrome notifications" e descarta a URL.
  // Ela só existe a partir do Android 13; antes disso, o pm grant falharia por permissão desconhecida.
  if (Number(adb.shell('getprop ro.build.version.sdk').trim()) >= SDK_COM_PERMISSAO_DE_NOTIFICACAO) {
    adb.shell(`pm grant ${CHROME} android.permission.POST_NOTIFICATIONS`);
  }
  adb.shell(`am force-stop ${CHROME}`);
}

// ---- UI Automator ----------------------------------------------------------

async function lerTela(adb) {
  for (let tentativa = 0; tentativa < 5; tentativa++) {
    const xml = adb.shell('uiautomator dump /sdcard/duora-ui.xml >/dev/null 2>&1; cat /sdcard/duora-ui.xml');
    if (xml.includes('<hierarchy')) {
      return [...xml.matchAll(/<node [^>]*>/g)].map(([tag]) => {
        const attr = Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map((m) => [m[1], m[2]]));
        const [x1, y1, x2, y2] = (attr.bounds.match(/\d+/g) ?? []).map(Number);
        return { ...attr, texto: (attr.text ?? '').replaceAll('&amp;', '&'), x: (x1 + x2) >> 1, y: (y1 + y2) >> 1 };
      });
    }
    await esperar(1000);
  }
  throw new Falha('uiautomator dump não retornou a hierarquia da tela');
}

const tocar = (adb, no) => adb.shell(`input tap ${no.x} ${no.y}`);

async function esperarNo(adb, criterio, timeoutMs) {
  const limite = Date.now() + timeoutMs;
  do {
    const no = (await lerTela(adb)).find(criterio);
    if (no) return no;
    await esperar(1000);
  } while (Date.now() < limite);
  return undefined;
}

async function fecharPromosDoChrome(adb) {
  for (let i = 0; i < 3; i++) {
    const no = await esperarNo(adb, (n) => n.package === CHROME && TEXTOS_FECHAR_PROMO.includes(n.texto), 3000);
    if (!no) return;
    log(`fechando aviso do Chrome ("${no.texto}")`);
    tocar(adb, no);
    await esperar(1000);
  }
}

// ---- Chrome DevTools Protocol ----------------------------------------------

async function listarPaginas(adb) {
  adb.executar(['forward', `tcp:${PORTA_CDP}`, 'localabstract:chrome_devtools_remote']);
  const alvos = await (await fetch(`http://127.0.0.1:${PORTA_CDP}/json/list`)).json();
  return alvos.filter((a) => a.type === 'page');
}

/** Conecta na primeira página em `url` cujo id não esteja em `ignorar` (abas já conhecidas). */
async function conectarCdp(adb, url, ignorar = new Set(), timeoutMs = 30000) {
  const limite = Date.now() + timeoutMs;
  while (Date.now() < limite) {
    try {
      const pagina = (await listarPaginas(adb)).find((a) => a.url.startsWith(url) && !ignorar.has(a.id));
      if (pagina) return abrirSessao(pagina.webSocketDebuggerUrl.replace('localhost', '127.0.0.1'));
    } catch { /* DevTools ainda subindo */ }
    await esperar(1000);
  }
  throw new Falha(`página ${url} não apareceu no DevTools do Chrome em ${timeoutMs / 1000}s`);
}

/**
 * Abre a URL no Chrome e conecta o CDP na aba. Um aviso de primeira execução às vezes engole o
 * intent e o Chrome fica na página inicial; aí fecha os avisos e manda o intent de novo.
 */
async function abrirNoChrome(adb, url, base) {
  for (let tentativa = 1; ; tentativa++) {
    log(`abrindo ${url} no Chrome${tentativa > 1 ? ` (tentativa ${tentativa})` : ''}`);
    adb.shell(`am start -a android.intent.action.VIEW -d '${url}' ${CHROME}`);
    await fecharPromosDoChrome(adb);
    try {
      return await conectarCdp(adb, base, new Set(), 15000);
    } catch (e) {
      if (tentativa === 3) throw e;
    }
  }
}

function abrirSessao(wsUrl) {
  return new Promise((ok, erro) => {
    const ws = new WebSocket(wsUrl);
    const pendentes = new Map();
    let proximoId = 0;
    ws.onmessage = ({ data }) => {
      const msg = JSON.parse(data);
      const p = pendentes.get(msg.id);
      if (!p) return;
      pendentes.delete(msg.id);
      msg.error ? p.erro(new Falha(`CDP: ${msg.error.message}`)) : p.ok(msg.result);
    };
    ws.onerror = () => erro(new Falha(`falha ao conectar no CDP (${wsUrl})`));
    ws.onopen = () => ok({
      enviar: (method, params = {}) => new Promise((ok2, erro2) => {
        const id = ++proximoId;
        pendentes.set(id, { ok: ok2, erro: erro2 });
        ws.send(JSON.stringify({ id, method, params }));
      }),
      fechar: () => ws.close(),
    });
  });
}

async function verificarInstalabilidade(cdp) {
  // O ready resolve com o worker ainda em "activating"; o app só registra depois do load, então
  // espera o "activated" pelo statechange.
  const sw = await cdp.enviar('Runtime.evaluate', {
    expression: `Promise.race([
      navigator.serviceWorker
        ? navigator.serviceWorker.ready.then(({ active }) => {
          if (!active) return 'sem-ativo';
          if (active.state === 'activated') return active.state;
          return new Promise((r) => active.addEventListener('statechange', () => {
            if (active.state === 'activated' || active.state === 'redundant') r(active.state);
          }));
        })
        : 'sem-suporte',
      new Promise((r) => setTimeout(() => r('timeout'), 15000))])`,
    awaitPromise: true, returnByValue: true,
  });
  const estadoSw = sw.result?.value;

  // O Chrome recalcula a instalabilidade depois que o SW ativa; tenta por alguns segundos.
  let erros = [];
  for (let i = 0; i < 10; i++) {
    erros = (await cdp.enviar('Page.getInstallabilityErrors')).installabilityErrors ?? [];
    if (erros.length === 0) break;
    await esperar(1000);
  }
  const manifest = await cdp.enviar('Page.getAppManifest');
  let dados = {};
  try { dados = JSON.parse(manifest.data ?? '{}'); } catch { /* manifest inválido aparece em manifestErros */ }
  // No Chrome Android, getInstallabilityErrors pode vir vazio mesmo sem manifest; checa à parte.
  if (!manifest.url) erros.push({ errorId: 'no-manifest' });
  if (estadoSw !== 'activated') erros.push({ errorId: `service-worker-${estadoSw}` });
  return {
    serviceWorker: estadoSw,
    manifestUrl: manifest.url,
    manifestErros: (manifest.errors ?? []).map((e) => e.message),
    nome: dados.short_name ?? dados.name,
    erros: erros.map((e) => (e.errorArguments?.length
      ? `${e.errorId} (${e.errorArguments.map((a) => `${a.name}=${a.value}`).join(', ')})` : e.errorId)),
  };
}

// ---- Instalação ------------------------------------------------------------

function pacotesWebApk(adb) {
  return new Set(adb.shell('pm list packages org.chromium.webapk').split('\n')
    .map((l) => l.replace('package:', '').trim()).filter(Boolean));
}

/** Atalhos fixados e ativos do escopo, por id, com o rótulo que o launcher mostra. */
function atalhosFixados(adb, escopo) {
  const texto = adb.shell('dumpsys shortcut');
  const atalhos = new Map();
  for (const bloco of texto.split('ShortcutInfo {').slice(1)) {
    const cabecalho = bloco.match(/^id=([^,]+), flags=0x[0-9a-f]+ \[([^\]]*)\]/);
    if (cabecalho && cabecalho[2].includes('Pin') && !cabecalho[2].includes('Dis')
      && bloco.includes(`webapp_scope=${escopo}`)) {
      atalhos.set(cabecalho[1], bloco.match(/shortLabel=([^,\n]+)/)?.[1]?.trim());
    }
  }
  return atalhos;
}

const atalhosDoEscopo = (adb, escopo) => new Set(atalhosFixados(adb, escopo).keys());

async function instalar(adb, escopo) {
  const webApksAntes = pacotesWebApk(adb);
  const atalhosAntes = atalhosDoEscopo(adb, escopo);

  const menu = await esperarNo(adb, (n) => n['resource-id'] === `${CHROME}:id/menu_button`, 10000);
  if (!menu) throw new Falha('botão de menu do Chrome não encontrado');
  tocar(adb, menu);
  const itemInstalar = await esperarNo(adb, (n) => n.package === CHROME && n.texto === TEXTO_MENU_INSTALAR, 5000);
  if (!itemInstalar) {
    adb.shell('input keyevent KEYCODE_BACK');
    throw new Falha(`menu do Chrome sem "${TEXTO_MENU_INSTALAR}": o Chrome não considera a página instalável`);
  }
  log(`menu: "${itemInstalar.texto}"`);
  tocar(adb, itemInstalar);
  const confirmar = await esperarNo(adb, (n) => n['resource-id'] === `${CHROME}:id/positive_button`, 10000);
  if (!confirmar) throw new Falha('diálogo de instalação do Chrome não apareceu');
  log(`diálogo: "${confirmar.texto}"`);
  tocar(adb, confirmar);

  // Ou a Play Store instala o WebAPK, ou o Chrome cai para atalho e o launcher pede confirmação.
  const limite = Date.now() + 90000;
  while (Date.now() < limite) {
    const novos = [...pacotesWebApk(adb)].filter((p) => !webApksAntes.has(p));
    if (novos.length) return { tipo: 'webapk', evidencia: novos.join(', ') };
    const tela = await lerTela(adb);
    const botao = tela.find((n) => n.package !== CHROME && n.clickable === 'true'
      && TEXTOS_CONFIRMAR_LAUNCHER.includes(n.texto));
    if (botao) {
      log(`launcher pediu confirmação ("${botao.texto}"): WebAPK não instalado, caiu para atalho`);
      tocar(adb, botao);
      await esperar(2000);
    }
    const atalhos = [...atalhosDoEscopo(adb, escopo)].filter((id) => !atalhosAntes.has(id));
    if (atalhos.length) return { tipo: 'atalho', evidencia: `shortcut id ${atalhos.join(', ')}` };
    await esperar(2000);
  }
  const motivo = adb.shell('logcat -d -t 400').split('\n')
    .filter((l) => /webapk/i.test(l) && /(not allowed|unknown_account|fail|error)/i.test(l)).slice(-3);
  throw new Falha(`nem WebAPK nem atalho apareceram em 90s${motivo.length ? `; logcat:\n${motivo.join('\n')}` : ''}`);
}

/** Pacote do WebAPK que atende a URL do escopo, se houver. */
function webApkDoEscopo(adb, escopo) {
  const atividades = adb.shell(`cmd package query-activities --brief -a android.intent.action.VIEW -d '${escopo}'`);
  return atividades.match(/^\s*(org\.chromium\.webapk\.[\w.]+)\//m)?.[1];
}

/**
 * Abre o app instalado como o usuário abriria: o WebAPK pelo pacote, com a URL do escopo; sem
 * WebAPK, toca no ícone do atalho fixado na tela inicial.
 */
export async function abrirInstalado(adb, escopo) {
  const pacote = webApkDoEscopo(adb, escopo);
  if (pacote) {
    adb.shell(`am start -a android.intent.action.VIEW -d '${escopo}' ${pacote}`);
    return { tipo: 'webapk', pacote };
  }
  const rotulo = [...atalhosFixados(adb, escopo).values()].find(Boolean);
  if (!rotulo) throw new Falha(`nenhum WebAPK nem atalho fixado para ${escopo}`);
  adb.shell('input keyevent KEYCODE_HOME');
  await esperar(1500);
  const icone = await esperarNo(adb, (n) => n.package !== CHROME
    && (n.texto === rotulo || n['content-desc'] === rotulo), 10000);
  if (!icone) throw new Falha(`ícone "${rotulo}" não encontrado na tela inicial`);
  log(`tocando no atalho "${rotulo}" da tela inicial`);
  tocar(adb, icone);
  return { tipo: 'atalho', rotulo };
}

/** O documento da aba conectada está em display-mode standalone? */
export async function ehStandalone(cdp) {
  const { result } = await cdp.enviar('Runtime.evaluate', {
    expression: "matchMedia('(display-mode: standalone)').matches", returnByValue: true,
  });
  return result?.value === true;
}

/** A barra de endereço do Chrome está na tela? */
async function barraDoNavegadorVisivel(adb) {
  return (await lerTela(adb)).some((n) => n['resource-id'] === `${CHROME}:id/url_bar`);
}

function capturarTela(adb, arquivo) {
  writeFileSync(arquivo, adb.executar(['exec-out', 'screencap', '-p'], { binario: true }));
  return arquivo;
}

// ---- Terraço e rotação -----------------------------------------------------

// Pixel 7 em pé: tela de 412 px CSS, palco a 112% (≈ 462 px) × densidade 2,625 ≈ 1212 px, e
// escolherVariante(…, [900, 1800, 2700]) dá 1800. Se o aparelho ou as larguras mudarem, a conta
// refeita no próprio Chrome acusa.
const VARIANTE_ESPERADA_EM_PE = 1800;

/**
 * Lê pelo CDP a imagem que o Chrome escolheu para o terraço e refaz a conta do cena.js com o
 * escolherVariante do próprio app; exige as duas iguais a VARIANTE_ESPERADA_EM_PE.
 */
async function verificarTerraco(cdp) {
  const { result, exceptionDetails } = await cdp.enviar('Runtime.evaluate', {
    expression: `(async () => {
      const { escolherVariante, VARIANTES } = await import('/js/dados.js');
      const palco = document.getElementById('palco');
      const larguraCss = palco.getBoundingClientRect().width || innerWidth;
      const dpr = devicePixelRatio || 1;
      const terraco = document.querySelector('.terraco-crepusculo');
      const limite = Date.now() + 15000;
      while (!(terraco.complete && terraco.currentSrc) && Date.now() < limite) {
        await new Promise((r) => setTimeout(r, 250));
      }
      return { larguraCss, dpr, escolhida: escolherVariante(larguraCss, dpr, VARIANTES),
        currentSrc: terraco.currentSrc };
    })()`,
    awaitPromise: true, returnByValue: true,
  });
  if (exceptionDetails) {
    throw new Falha(`leitura do terraço falhou: ${exceptionDetails.exception?.description ?? exceptionDetails.text}`);
  }
  const terraco = { ...result.value, esperada: VARIANTE_ESPERADA_EM_PE };
  log(`terraço: ${terraco.currentSrc || '(sem imagem)'}; escolherVariante(${terraco.larguraCss} × ${terraco.dpr}) = ${terraco.escolhida}`);
  if (terraco.escolhida !== VARIANTE_ESPERADA_EM_PE) {
    throw new Falha(`escolherVariante deu ${terraco.escolhida}, não ${VARIANTE_ESPERADA_EM_PE}: revise VARIANTE_ESPERADA_EM_PE`);
  }
  if (!terraco.currentSrc.endsWith(`-${VARIANTE_ESPERADA_EM_PE}.webp`)) {
    throw new Falha(`terraço em ${terraco.currentSrc || '(sem imagem)'}, esperado -${VARIANTE_ESPERADA_EM_PE}.webp`);
  }
  return terraco;
}

function lerRotacao(adb) {
  return {
    automatica: adb.shell('settings get system accelerometer_rotation').trim(),
    usuario: adb.shell('settings get system user_rotation').trim(),
  };
}

/**
 * Comandos que devolvem a rotação lida por lerRotacao. "null" é o que settings get responde para
 * chave nunca gravada: a volta apaga a chave, para o sistema seguir no padrão dele.
 */
export function comandosParaRestaurar({ automatica, usuario }) {
  return Object.entries({ user_rotation: usuario, accelerometer_rotation: automatica })
    .map(([chave, valor]) => (valor === 'null'
      ? `settings delete system ${chave}`
      : `settings put system ${chave} ${valor}`));
}

function aplicarRotacao(adb, rotacao) {
  for (const comando of comandosParaRestaurar(rotacao)) adb.shell(comando);
}

/** Trava a tela deitada (rotação 1 = 90°), captura e devolve a rotação de antes. */
async function capturarDeitado(adb, arquivo) {
  const antes = lerRotacao(adb);
  try {
    aplicarRotacao(adb, { automatica: '0', usuario: '1' });
    await esperar(2500);
    return capturarTela(adb, arquivo);
  } finally {
    aplicarRotacao(adb, antes);
    await esperar(1500);
  }
}

// ---- Fluxo -----------------------------------------------------------------

/** Ordem em que o main() roda os passos de EXECUTORES. */
export const PASSOS = Object.freeze([
  'instalabilidade', 'verificarTerraco', 'capturarEmPe', 'capturarDeitado',
  'instalar', 'abrirInstalado', 'capturarStandalone', 'capturarHome',
]);

const EXECUTORES = {
  async instalabilidade({ cdp, resultado }) {
    resultado.instalabilidade = await verificarInstalabilidade(cdp);
    const inst = resultado.instalabilidade;
    log(`Chrome ${resultado.chrome ?? '?'}; service worker: ${inst.serviceWorker}; manifest: ${inst.manifestUrl || '(nenhum)'}`);
    log(`instalabilidade (CDP + manifest + SW): ${inst.erros.length ? inst.erros.join('; ') : '[] (instalável)'}`);
    if (inst.erros.length || inst.manifestErros.length) {
      throw new Falha(`PWA não instalável: ${[...inst.erros, ...inst.manifestErros].join('; ')}`);
    }
  },
  async verificarTerraco({ cdp, resultado }) {
    resultado.terraco = await verificarTerraco(cdp);
  },
  // Depois do verificarTerraco: a captura mostra o terraço já carregado na variante conferida.
  async capturarEmPe({ adb, args, resultado }) {
    resultado.screenshotEmPe = capturarTela(adb, join(SAIDA, `${args.nome}-em-pe.png`));
  },
  async capturarDeitado({ adb, args, resultado }) {
    resultado.screenshotDeitado = await capturarDeitado(adb, join(SAIDA, `${args.nome}-deitado.png`));
  },
  async instalar({ adb, args, base, resultado }) {
    if (args['sem-instalar']) return;
    resultado.instalacao = await instalar(adb, `${base}/`);
    log(`instalado como ${resultado.instalacao.tipo}: ${resultado.instalacao.evidencia}`);
    if (args['exigir-webapk'] && resultado.instalacao.tipo !== 'webapk') {
      throw new Falha('--exigir-webapk: o Chrome instalou atalho, não WebAPK');
    }
  },
  async abrirInstalado(contexto) {
    const { adb, args, base, resultado } = contexto;
    if (args['sem-instalar']) return;
    const conhecidas = new Set((await listarPaginas(adb)).map((p) => p.id));
    resultado.aberturaInstalado = await abrirInstalado(adb, `${base}/`);
    contexto.cdpInstalado = await conectarCdp(adb, base, conhecidas);
  },
  async capturarStandalone({ adb, args, cdpInstalado, resultado }) {
    if (args['sem-instalar']) return;
    await esperar(2500);
    const standalone = await ehStandalone(cdpInstalado);
    const barra = await barraDoNavegadorVisivel(adb);
    resultado.standalone = { displayModeStandalone: standalone, barraDoNavegador: barra };
    resultado.screenshotStandalone = capturarTela(adb, join(SAIDA, `${args.nome}-standalone.png`));
    log(`app instalado: display-mode standalone = ${standalone}; barra do navegador = ${barra}`);
    if (!standalone) throw new Falha('o app instalado não abriu em display-mode standalone');
    if (barra) throw new Falha('o app instalado abriu com a barra de endereço do Chrome');
  },
  async capturarHome({ adb, args, resultado }) {
    if (args['sem-instalar']) return;
    adb.shell('input keyevent KEYCODE_HOME');
    await esperar(1500);
    resultado.screenshotHome = capturarTela(adb, join(SAIDA, `${args.nome}-home.png`));
  },
};

async function main() {
  const args = lerArgumentos();
  const adb = criarAdb(resolverAdb());
  const base = `http://localhost:${args.porta}`;
  const url = `${base}${args.caminho.startsWith('/') ? '' : '/'}${args.caminho}`;
  mkdirSync(SAIDA, { recursive: true });
  const resultado = { url, serial: SERIAL };
  const contexto = { args, adb, base, url, resultado, cdp: undefined, cdpInstalado: undefined };

  exigirEmuladorPronto(adb);
  // Lida antes de abrir o Chrome: na primeira abertura, o sistema grava user_rotation=0 sozinho,
  // e a leitura do capturarDeitado já não vê o "null" de antes da rodada.
  const rotacaoInicial = lerRotacao(adb);
  try {
    prepararChrome(adb, !args['manter-chrome']);
    adb.executar(['reverse', `tcp:${args.porta}`, `tcp:${args.porta}`]);
    adb.executar(['logcat', '-c']);
    contexto.cdp = await abrirNoChrome(adb, url, base);
    resultado.chrome = (await contexto.cdp.enviar('Browser.getVersion').catch(() => ({}))).product;
    for (const passo of PASSOS) await EXECUTORES[passo](contexto);
    resultado.ok = true;
  } catch (e) {
    resultado.ok = false;
    resultado.erro = e.message;
    try { resultado.screenshotFalha = capturarTela(adb, join(SAIDA, `${args.nome}-falha.png`)); } catch { /* sem tela */ }
    throw e;
  } finally {
    contexto.cdp?.fechar();
    contexto.cdpInstalado?.fechar();
    try { aplicarRotacao(adb, rotacaoInicial); } catch { /* emulador caiu */ }
    try { adb.executar(['forward', '--remove', `tcp:${PORTA_CDP}`]); } catch { /* já removido */ }
    try { adb.executar(['reverse', '--remove', `tcp:${args.porta}`]); } catch { /* já removido */ }
    writeFileSync(join(SAIDA, `${args.nome}.json`), `${JSON.stringify(resultado, null, 2)}\n`);
    log(`resultado: ${join(SAIDA, `${args.nome}.json`)}`);
  }
}

// Importado pelos testes, o módulo só expõe as funções; o fluxo roda quando executado direto.
if (process.argv[1] && realpathSync(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error(`erro: ${e instanceof Falha ? e.message : e.stack}`);
    process.exit(1);
  });
}
