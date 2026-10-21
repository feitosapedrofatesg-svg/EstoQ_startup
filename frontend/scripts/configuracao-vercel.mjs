import { readFileSync } from "node:fs";

const base = JSON.parse(readFileSync(new URL("../vercel.base.json", import.meta.url), "utf8"));
const API_PRODUCAO = "https://estoq-backend-va.onrender.com";

export function criarConfiguracao(variaveis) {
  if (variaveis.ESTOQ_AMBIENTE && !["homologacao", "producao"].includes(variaveis.ESTOQ_AMBIENTE))
    throw new Error("ESTOQ_AMBIENTE deve ser homologacao ou producao.");
  const branch = variaveis.VERCEL_GIT_COMMIT_REF;
  const homologacao = variaveis.ESTOQ_AMBIENTE === "homologacao"
    || variaveis.VERCEL_ENV === "preview" || (branch && branch !== "main");
  let api = API_PRODUCAO;
  if (homologacao) {
    if (!variaveis.ESTOQ_API_ORIGIN) throw new Error("Configure ESTOQ_API_ORIGIN com o backend de homologação.");
    const url = new URL(variaveis.ESTOQ_API_ORIGIN);
    if (url.protocol !== "https:" || url.origin === API_PRODUCAO || url.username || url.password
      || url.pathname !== "/" || url.search || url.hash) {
      throw new Error("Homologação exige a origem HTTPS de outro backend, sem credenciais ou caminho.");
    }
    api = url.origin;
  }
  return {
    framework: "vite",
    buildCommand: `VITE_AMBIENTE=${homologacao ? "homologacao" : "producao"} npm run build`,
    outputDirectory: "dist",
    headers: base.headers,
    rewrites: [
      { source: "/api/:path*", destination: `${api}/api/:path*` },
      { source: "/(.*)", destination: "/index.html" },
    ],
  };
}
