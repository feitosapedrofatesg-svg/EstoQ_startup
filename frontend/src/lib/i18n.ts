import { useSyncExternalStore } from "react";
import { traducoes } from "./traducoes";
import { extras } from "./traducoesExtras";
Object.assign(traducoes,extras);
const modelos=Object.keys(traducoes).filter(chave=>/\{\d+\}/.test(chave)).map(chave=>{
 const partes=chave.split(/(\{\d+\})/);
 const indices:string[]=[];
 const padrao=partes.map(parte=>{
  const parametro=/^\{(\d+)\}$/.exec(parte);
  if(parametro){indices.push(parametro[1]);return "(.+?)";}
  return parte.replace(/[.*+?^${}()|[\]\\]/g,"\\$&");
 }).join("");
 return {chave,indices,padrao:new RegExp(`^${padrao}$`)};
});
export type Idioma="pt-BR"|"en"|"es";
let atual:Idioma="pt-BR";
const ouvintes=new Set<()=>void>();
export function idiomaAtual(){return atual;}
export function definirIdioma(idioma:Idioma){atual=idioma;document.documentElement.lang=idioma;ouvintes.forEach(fn=>fn());}
export function useIdiomaAtual(){return useSyncExternalStore(fn=>{ouvintes.add(fn);return()=>ouvintes.delete(fn);},()=>atual);}
export function traduzir(texto:string, valores:Record<string,unknown>={}):string {
 let chave=texto;
 // Mensagens persistidas e erros da API contêm valores, não chaves de tradução.
 // Traduz apenas o modelo conhecido, preservando os nomes e valores capturados.
 if(atual!=="pt-BR"&&!traducoes[chave]&&!Object.keys(valores).length){
  for(const modelo of modelos){const encontrado=modelo.padrao.exec(texto);if(encontrado){
   chave=modelo.chave;valores=Object.fromEntries(modelo.indices.map((indice,i)=>[indice,encontrado[i+1]]));break;
  }}
 }
 const modelo=atual==="pt-BR"?texto:(traducoes[chave]?.[atual==="en"?0:1]??texto);
 return modelo.replace(/\{(\d+)\}/g,(original,chave)=>valores[chave]===undefined?original:String(valores[chave]));
}
export const t=traduzir;
