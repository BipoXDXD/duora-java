// Testes do pwa-check sem emulador: node --test 'scripts/android/*.test.mjs'
// (glob entre aspas, como no README; com só a pasta, o Node 24 não acha os testes).
import assert from 'node:assert/strict';
import { test } from 'node:test';

import { PASSOS, comandosParaRestaurar } from './pwa-check.mjs';

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
