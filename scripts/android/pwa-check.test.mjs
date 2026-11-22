// Testes do pwa-check sem emulador: node --test 'scripts/android/*.test.mjs'
// (glob entre aspas, como no README; com só a pasta, o Node 24 não acha os testes).
import assert from 'node:assert/strict';
import { test } from 'node:test';

import { PASSOS, comandosParaRestaurar, prepararChrome } from './pwa-check.mjs';

/** adb falso: registra os comandos de shell, responde o SDK e falha nos comandos pedidos. */
function adbFalso({ sdk, falharEm = () => false }) {
  const comandos = [];
  return {
    comandos,
    shell(comando) {
      comandos.push(comando);
      if (falharEm(comando)) throw new Error(`falhou: ${comando}`);
      return comando === 'getprop ro.build.version.sdk' ? `${sdk}\n` : '';
    },
  };
}

const concedeNotificacoes = (comando) => comando.startsWith('pm grant ')
  && comando.endsWith(' android.permission.POST_NOTIFICATIONS');

test('rotacaoNulaVoltaComDelete', () => {
  const comandos = comandosParaRestaurar({ automatica: '1', usuario: 'null' });

  assert.ok(comandos.includes('settings delete system user_rotation'), comandos.join('\n'));
  assert.ok(comandos.includes('settings put system accelerometer_rotation 1'), comandos.join('\n'));
});

test('rotacaoComValorVoltaComPut', () => {
  const comandos = comandosParaRestaurar({ automatica: '0', usuario: '3' });

  assert.deepEqual([...comandos].sort(), [
    'settings put system accelerometer_rotation 0',
    'settings put system user_rotation 3',
  ]);
});

test('terracoVerificadoAntesDaCapturaEmPe', () => {
  assert.ok(PASSOS.indexOf('verificarTerraco') >= 0);
  assert.ok(PASSOS.indexOf('verificarTerraco') < PASSOS.indexOf('capturarEmPe'));
});

test('standaloneDepoisDeInstalar', () => {
  assert.ok(PASSOS.indexOf('instalar') >= 0);
  assert.ok(PASSOS.indexOf('instalar') < PASSOS.indexOf('abrirInstalado'));
  assert.ok(PASSOS.indexOf('abrirInstalado') < PASSOS.indexOf('capturarStandalone'));
});

test('android12NaoPedePermissaoDeNotificacao', () => {
  const adb = adbFalso({ sdk: 32 });

  prepararChrome(adb, false);

  assert.ok(!adb.comandos.some(concedeNotificacoes), adb.comandos.join('\n'));
});

test('android13FalhaSeAPermissaoDeNotificacaoFalhar', () => {
  const adb = adbFalso({ sdk: 33, falharEm: concedeNotificacoes });

  assert.throws(() => prepararChrome(adb, false), /POST_NOTIFICATIONS/);
});
