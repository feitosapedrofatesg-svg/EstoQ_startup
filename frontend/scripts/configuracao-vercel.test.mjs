import { test } from "node:test";
import assert from "node:assert/strict";
import { criarConfiguracao } from "./configuracao-vercel.mjs";

test("main mantém a API de produção e os cabeçalhos de segurança", () => {
  const config = criarConfiguracao({ VERCEL_GIT_COMMIT_REF: "main", VERCEL_ENV: "production" });
  assert.equal(config.rewrites[0].destination, "https://estoq-backend-va.onrender.com/api/:path*");
  assert.ok(config.headers[0].headers.some(h => h.key === "X-Content-Type-Options"));
  assert.match(config.buildCommand, /VITE_AMBIENTE=producao/);
});

test("homologacao usa sua API mesmo sendo a branch principal de outro projeto Vercel", () => {
  const config = criarConfiguracao({ VERCEL_GIT_COMMIT_REF: "homologacao", VERCEL_ENV: "production",
    ESTOQ_API_ORIGIN: "https://backend-teste.onrender.com/" });
  assert.equal(config.rewrites[0].destination, "https://backend-teste.onrender.com/api/:path*");
  assert.match(config.buildCommand, /VITE_AMBIENTE=homologacao/);
});

test("preview sem backend próprio falha em vez de usar produção", () => {
  for (const variaveis of [{ VERCEL_ENV: "preview" }, { VERCEL_GIT_COMMIT_REF: "dev" }, { ESTOQ_AMBIENTE: "homologacao" }])
    assert.throws(() => criarConfiguracao(variaveis), /ESTOQ_API_ORIGIN/);
});

test("erro de digitação no ambiente não seleciona produção", () => {
  assert.throws(() => criarConfiguracao({ ESTOQ_AMBIENTE: "homologação" }), /ESTOQ_AMBIENTE/);
});

test("homologacao rejeita API de produção e URLs inválidas para o proxy", () => {
  for (const api of ["https://estoq-backend-va.onrender.com", "http://backend-teste.onrender.com",
    "https://usuario:senha@backend-teste.onrender.com", "https://backend-teste.onrender.com/api", "https://backend-teste.onrender.com?chave=abc"]) {
    assert.throws(() => criarConfiguracao({ ESTOQ_AMBIENTE: "homologacao", ESTOQ_API_ORIGIN: api }));
  }
});
