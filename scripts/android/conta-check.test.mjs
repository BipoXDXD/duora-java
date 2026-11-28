// Testes do conta-check sem emulador: node --test 'scripts/android/*.test.mjs'
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  alvosPequenos, cabecalhoDaCsp, linkNaBase, passosDaRodada, tecladoNumericoAberto, ultimoLinkDoLog,
} from './conta-check.mjs';

const TOKEN_ANTIGO = 'AAAAaaaa_bbbb-cccc';
const TOKEN_NOVO = 'ZZZZzzzz_yyyy-xxxx';

/** Trecho no formato do EmailNoLog: cabeçalho numa linha, texto do e-mail nas seguintes. */
const emailNoLog = (token) => `2026-11-27T10:00:00.000-03:00  INFO 1 --- [duora] [scheduling-1] `
  + `b.n.d.notificacao.infra.EmailNoLog : E-mail 0193 para m***@exemplo.com (Seu link para entrar no Duora):
Use o link abaixo para entrar no Duora. Ele vale por 15 minutos.

http://127.0.0.1:8080/entrar/confirmar#t=${token}
`;

test('ultimoLinkVenceOAnterior', () => {
  const log = `${emailNoLog(TOKEN_ANTIGO)}outra linha\n${emailNoLog(TOKEN_NOVO)}`;

  assert.equal(ultimoLinkDoLog(log), `http://127.0.0.1:8080/entrar/confirmar#t=${TOKEN_NOVO}`);
});

test('semLinkDevolveNull', () => {
  assert.equal(ultimoLinkDoLog('Started DuoraApplication in 3.2 seconds\n'), null);
});

test('linkSemTokenIgnorado', () => {
  const log = `${emailNoLog(TOKEN_ANTIGO)}http://127.0.0.1:8080/entrar/confirmar#t=\n`;

  assert.equal(ultimoLinkDoLog(log), `http://127.0.0.1:8080/entrar/confirmar#t=${TOKEN_ANTIGO}`);
});

test('passosParamNaTelaDaVerificacao', () => {
  const passos = passosDaRodada({ perfil: false });

  assert.equal(passos.at(-1), 'capturarVerificacao');
  for (const captura of ['capturarEntrar', 'capturarCadastro', 'capturarMinhaConta']) {
    assert.ok(passos.indexOf(captura) >= 0, `${captura} fora da rodada: ${passos.join(', ')}`);
  }
});

test('perfilAbreAsTelasDoPerfil', () => {
  const passos = passosDaRodada({ perfil: true });
  const depoisDe = (antes, depois) => passos.indexOf(antes) >= 0 && passos.indexOf(antes) < passos.indexOf(depois);

  assert.deepEqual(passos.slice(0, passosDaRodada({ perfil: false }).length), passosDaRodada({ perfil: false }));
  assert.ok(depoisDe('confirmarIdade', 'abrirPerfil'), passos.join(', '));
  assert.ok(depoisDe('abrirPerfil', 'capturarPerfil'), passos.join(', '));
  assert.ok(depoisDe('capturarPerfil', 'abrirPreferencias'), passos.join(', '));
  assert.ok(depoisDe('abrirPreferencias', 'capturarPreferencias'), passos.join(', '));
});

test('alvoAbaixoDe44PxApontado', () => {
  const medidas = [
    { alvo: 'botão Salvar perfil', largura: 320, altura: 48 },
    { alvo: 'link Minha conta', largura: 90, altura: 43.5 },
    { alvo: 'rádio Ele/dele', largura: 30, altura: 44 },
  ];

  assert.deepEqual(alvosPequenos(medidas), [medidas[1], medidas[2]]);
});

/**
 * Trecho do dumpsys input_method do Android 15: o histórico vem antes, com outros inputType, e o
 * campo focado fica no mInputEditorInfo.
 */
const dumpsysDoTeclado = (mostrado, tipo) => `  mInputShown=${mostrado}
  mStartInputHistory:
    StartInput #0:
      inputType=0x0 imeOptions=0x0 fieldId=0x0 fieldName=null actionId=0 actionLabel=null
  mInputStarted=true mInputViewStarted=true mCandidatesViewStarted=false
  mInputEditorInfo:
    inputType=0x${tipo.toString(16)} imeOptions=0x12000005 privateImeOptions=null
`;

test('tecladoNumericoSoComCampoNumericoEAberto', () => {
  assert.equal(tecladoNumericoAberto(dumpsysDoTeclado(true, 0x2)), true);
  // TYPE_CLASS_NUMBER com flags de variação continua numérico.
  assert.equal(tecladoNumericoAberto(dumpsysDoTeclado(true, 0x1002)), true);
  assert.equal(tecladoNumericoAberto(dumpsysDoTeclado(true, 0x1)), false);
  assert.equal(tecladoNumericoAberto(dumpsysDoTeclado(false, 0x2)), false);
});

test('linkVaiParaABaseDaRodada', () => {
  assert.equal(linkNaBase(`http://127.0.0.1:8080/entrar/confirmar#t=${TOKEN_NOVO}`, 'http://localhost:8080'),
    `http://localhost:8080/entrar/confirmar#t=${TOKEN_NOVO}`);
});

test('cspEmQualquerCaixaDeNome', () => {
  assert.equal(cabecalhoDaCsp({ 'content-security-policy': "default-src 'self'" }), "default-src 'self'");
  assert.equal(cabecalhoDaCsp({ 'Content-Security-Policy': "default-src 'self'" }), "default-src 'self'");
});

test('respostaSemCspDevolveNull', () => {
  assert.equal(cabecalhoDaCsp({ 'content-type': 'text/html' }), null);
});
