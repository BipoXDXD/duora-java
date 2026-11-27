// Testes do conta-check sem emulador: node --test 'scripts/android/*.test.mjs'
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  cabecalhoDaCsp, linkNaBase, passosDaRodada, ultimoLinkDoLog,
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

test('passosParamNaTelaAguardando', () => {
  const passos = passosDaRodada({});

  assert.equal(passos.at(-1), 'capturarAguardando');
  for (const captura of ['capturarEntrar', 'capturarCadastro', 'capturarMinhaConta']) {
    assert.ok(passos.indexOf(captura) >= 0, `${captura} fora da rodada: ${passos.join(', ')}`);
  }
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
