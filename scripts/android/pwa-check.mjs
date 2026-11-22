#!/usr/bin/env node
// Verifica a instalação da PWA no Chrome do emulador Android (AVD duora-pixel).
//
// Uso: node scripts/android/pwa-check.mjs --porta 8080 [--nome pwa] [--caminho /]
//        [--sem-instalar] [--manter-chrome] [--exigir-webapk]
//
// Pré-requisito: emulador ligado (scripts/android/emulador.sh subir) e a aplicação
// servindo em localhost:<porta> nesta máquina.
//
// Passos: adb reverse da porta; abre a URL no Chrome; consulta pelo CDP o manifest e
// Page.getInstallabilityErrors; instala pelo menu "Install app"/"Add to home screen";
// confirma WebAPK (pacote org.chromium.webapk.*) ou atalho fixado no launcher; tira
// screenshots em target/android/. Sai com código 1 em qualquer falha.
//
// Sem conta Google no emulador, a Play Store recusa instalar o WebAPK e o Chrome cai
// para atalho fixado; por isso o padrão aceita atalho e --exigir-webapk o recusa.
//
// Variáveis: ANDROID_SDK_ROOT (ou ANDROID_HOME), DUORA_AVD_PORTA (padrão 5580),
// DUORA_CDP_PORTA (porta local do DevTools, padrão 9333).

import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
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

function prepararChrome(adb, limpar) {
  if (limpar) adb.shell(`pm clear ${CHROME}`);
  // Chrome lê este arquivo só quando é o "debug app"; pula o first run e o aviso de navegador padrão.
  adb.shell('echo "_ --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line');
  adb.shell(`am set-debug-app --persistent ${CHROME}`);
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

async function conectarCdp(adb, url) {
  adb.executar(['forward', `tcp:${PORTA_CDP}`, 'localabstract:chrome_devtools_remote']);
  const limite = Date.now() + 30000;
  while (Date.now() < limite) {
    try {
      const alvos = await (await fetch(`http://127.0.0.1:${PORTA_CDP}/json/list`)).json();
      const pagina = alvos.find((a) => a.type === 'page' && a.url.startsWith(url));
      if (pagina) return abrirSessao(pagina.webSocketDebuggerUrl.replace('localhost', '127.0.0.1'));
    } catch { /* DevTools ainda subindo */ }
    await esperar(1000);
  }
  throw new Falha(`página ${url} não apareceu no DevTools do Chrome em 30s`);
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
  const sw = await cdp.enviar('Runtime.evaluate', {
    expression: `Promise.race([
      navigator.serviceWorker ? navigator.serviceWorker.ready.then((r) => r.active ? r.active.state : 'sem-ativo') : 'sem-suporte',
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

function atalhosDoEscopo(adb, escopo) {
  const texto = adb.shell('dumpsys shortcut');
  const ids = new Set();
  for (const bloco of texto.split('ShortcutInfo {').slice(1)) {
    const cabecalho = bloco.match(/^id=([^,]+), flags=0x[0-9a-f]+ \[([^\]]*)\]/);
    if (cabecalho && cabecalho[2].includes('Pin') && !cabecalho[2].includes('Dis')
      && bloco.includes(`webapp_scope=${escopo}`)) ids.add(cabecalho[1]);
  }
  return ids;
}

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

function capturarTela(adb, arquivo) {
  writeFileSync(arquivo, adb.executar(['exec-out', 'screencap', '-p'], { binario: true }));
  return arquivo;
}

// ---- Fluxo -----------------------------------------------------------------

async function main() {
  const args = lerArgumentos();
  const adb = criarAdb(resolverAdb());
  const base = `http://localhost:${args.porta}`;
  const url = `${base}${args.caminho.startsWith('/') ? '' : '/'}${args.caminho}`;
  mkdirSync(SAIDA, { recursive: true });
  const resultado = { url, serial: SERIAL };
  let cdp;

  exigirEmuladorPronto(adb);
  try {
    prepararChrome(adb, !args['manter-chrome']);
    adb.executar(['reverse', `tcp:${args.porta}`, `tcp:${args.porta}`]);
    adb.executar(['logcat', '-c']);
    log(`abrindo ${url} no Chrome`);
    adb.shell(`am start -a android.intent.action.VIEW -d '${url}' ${CHROME}`);
    await fecharPromosDoChrome(adb);

    cdp = await conectarCdp(adb, base);
    const versao = (await cdp.enviar('Browser.getVersion').catch(() => ({}))).product;
    resultado.chrome = versao;
    resultado.instalabilidade = await verificarInstalabilidade(cdp);
    const inst = resultado.instalabilidade;
    log(`Chrome ${versao ?? '?'}; service worker: ${inst.serviceWorker}; manifest: ${inst.manifestUrl || '(nenhum)'}`);
    log(`instalabilidade (CDP + manifest + SW): ${inst.erros.length ? inst.erros.join('; ') : '[] (instalável)'}`);
    resultado.screenshotPagina = capturarTela(adb, join(SAIDA, `${args.nome}.png`));
    if (inst.erros.length || inst.manifestErros.length) {
      throw new Falha(`PWA não instalável: ${[...inst.erros, ...inst.manifestErros].join('; ')}`);
    }

    if (!args['sem-instalar']) {
      resultado.instalacao = await instalar(adb, `${base}/`);
      log(`instalado como ${resultado.instalacao.tipo}: ${resultado.instalacao.evidencia}`);
      adb.shell('input keyevent KEYCODE_HOME');
      await esperar(1500);
      resultado.screenshotHome = capturarTela(adb, join(SAIDA, `${args.nome}-home.png`));
      if (args['exigir-webapk'] && resultado.instalacao.tipo !== 'webapk') {
        throw new Falha('--exigir-webapk: o Chrome instalou atalho, não WebAPK');
      }
    }
    resultado.ok = true;
  } catch (e) {
    resultado.ok = false;
    resultado.erro = e.message;
    try { resultado.screenshotFalha = capturarTela(adb, join(SAIDA, `${args.nome}-falha.png`)); } catch { /* sem tela */ }
    throw e;
  } finally {
    cdp?.fechar();
    try { adb.executar(['forward', '--remove', `tcp:${PORTA_CDP}`]); } catch { /* já removido */ }
    try { adb.executar(['reverse', '--remove', `tcp:${args.porta}`]); } catch { /* já removido */ }
    writeFileSync(join(SAIDA, `${args.nome}.json`), `${JSON.stringify(resultado, null, 2)}\n`);
    log(`resultado: ${join(SAIDA, `${args.nome}.json`)}`);
  }
}

main().catch((e) => {
  console.error(`erro: ${e instanceof Falha ? e.message : e.stack}`);
  process.exit(1);
});
